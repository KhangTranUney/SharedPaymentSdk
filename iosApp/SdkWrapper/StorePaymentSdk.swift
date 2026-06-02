import StoreKit
import Foundation
import shared

/// iOS implementation of PaymentSdk wrapping StoreKit 2.
///
/// Lives in the iosApp layer because StoreKit 2 is
/// Swift-only (no Kotlin/Native interop).
class StorePaymentSdk: PaymentSdk {

    private let opsClient: StoreOpsApiClient

    // Caches the verified StoreKit.Transaction between purchase()
    // and getTransactionResult() so we can call .finish() without
    // iterating Transaction.all again. Survives within a process;
    // on cold restart, we fall back to iterating Transaction.all.
    private var pendingTransactions: [String: StoreKit.Transaction] = [:]

    init(
        opsBaseUrl: String,
        authTokenProvider: @escaping () async -> String
    ) {
        // Bridge a Swift async closure into the KMP
        // `suspend () -> String` parameter.
        self.opsClient = StoreOpsApiClient(
            baseUrl: opsBaseUrl,
            authTokenProvider: { completion in
                Task {
                    completion(await authTokenProvider(), nil)
                }
            }
        )
    }

    // MARK: - Get Products

    func getProducts(
        productIds: [String]
    ) async throws -> [Product] {

        if productIds.isEmpty { return [] }

        let storeProducts = try await StoreKit.Product.products(
            for: productIds
        )

        return storeProducts.map { p in
            Product(
                productId: p.id,
                title: p.displayName,
                description: p.description,
                formattedPrice: p.displayPrice,
                price: NSDecimalNumber(
                    decimal: p.price
                ).doubleValue,
                currencyCode: p.priceFormatStyle
                    .currencyCode ?? ""
            )
        }
    }

    // MARK: - Purchase

    func purchase(
        product: Product
    ) async throws -> PurchaseResult {

        guard let storeProduct = try await
            StoreKit.Product.products(
                for: [product.productId]
            ).first
        else {
            return PurchaseResult.Error(
                code: -1,
                message: "Product not found"
            )
        }

        let result = try await storeProduct.purchase()

        switch result {

        case .success(let verification):
            guard case .verified(let transaction)
                = verification else {
                return PurchaseResult.Error(
                    code: -1,
                    message: "Verification failed"
                )
            }
            let txId = String(transaction.id)
            // Cache the live Transaction so getTransactionResult()
            // can call .finish() without iterating Transaction.all.
            pendingTransactions[txId] = transaction
            return PurchaseResult.Success(
                transactionId: txId,
                receiptToken: transaction.jwsRepresentation
            )

        case .userCancelled:
            return PurchaseResult.UserCanceled.shared

        case .pending:
            // Ask to Buy / SCA — not a confirmed success.
            return PurchaseResult.Error(
                code: -2,
                message: "Purchase pending external approval"
            )

        @unknown default:
            return PurchaseResult.Error(
                code: -1,
                message: "Unknown"
            )
        }
    }

    // MARK: - Get Transaction Result

    /// 1. Resolves the verified StoreKit.Transaction (cached
    ///    from purchase(), falls back to iterating Transaction.all
    ///    by transaction id).
    /// 2. Calls `tx.finish()` so StoreKit stops re-delivering it.
    /// 3. POSTs the receipt (jwsRepresentation) to the Ops
    ///    Platform for server-side verification (wire your
    ///    HTTP client here).
    ///
    /// The host app is responsible for forwarding the returned
    /// Transaction to its own backend to grant entitlement —
    /// the SDK does not call the host backend.
    func getTransactionResult(
        purchase: PurchaseResult.Success
    ) async throws -> Transaction {

        // 1. Resolve the live transaction (cache → fallback).
        let storeTx: StoreKit.Transaction? = await {
            if let cached = pendingTransactions[purchase.transactionId] {
                return cached
            }
            guard let lookupId = UInt64(purchase.transactionId) else {
                return nil
            }
            for await result in StoreKit.Transaction.all {
                if case .verified(let tx) = result,
                   tx.id == lookupId {
                    return tx
                }
            }
            return nil
        }()

        guard let tx = storeTx else {
            return Transaction(
                transactionId: purchase.transactionId,
                productId: "",
                receiptToken: purchase.receiptToken,
                status: .failed,
                purchasedAt: 0
            )
        }

        // 2. Finish so StoreKit does not re-deliver this tx.
        await tx.finish()

        // 3. Clear cache.
        pendingTransactions.removeValue(forKey: purchase.transactionId)

        // 4. POST receipt to Ops Platform. Ops verifies the JWS
        //    server-side and returns the canonical Transaction.
        return try await opsClient.verifyReceipt(
            transactionId: purchase.transactionId,
            receiptToken: purchase.receiptToken
        )
    }
}
