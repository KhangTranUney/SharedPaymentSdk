import StoreKit
import Foundation
import shared

/// iOS implementation of PaymentSdk wrapping StoreKit 2.
///
/// Lives in the iosApp layer because StoreKit 2 is
/// Swift-only (no Kotlin/Native interop).
class NativePaymentSdk: PaymentSdk {

    // MARK: - Get Products

    func getProducts(
        productIds: [String]?
    ) async throws -> [Product] {

        // StoreKit 2 requires explicit product ids; there is
        // no "list all" API. Caller must supply them.
        guard let ids = productIds, !ids.isEmpty else {
            return []
        }

        let storeProducts = try await StoreKit.Product.products(
            for: ids
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
            return PurchaseResult.Success(
                transactionId: String(transaction.id)
            )

        case .userCancelled:
            return PurchaseResult.UserCanceled.shared

        case .pending:
            return PurchaseResult.Success(
                transactionId: "pending"
            )

        @unknown default:
            return PurchaseResult.Error(
                code: -1,
                message: "Unknown"
            )
        }
    }

    // MARK: - Get Transaction Result

    /// Returns the verified transaction from StoreKit and calls
    /// `transaction.finish()` so StoreKit stops re-delivering
    /// it on subsequent launches.
    ///
    /// Backend stays in sync via App Store Server Notifications
    /// V2 (Apple → backend), and the backend pushes updates to
    /// the app out-of-band (APNs / silent push) — no HTTP call
    /// from the SDK is needed.
    func getTransactionResult(
        transactionId: String
    ) async throws -> Transaction {

        guard let txId = UInt64(transactionId) else {
            return Transaction(
                transactionId: transactionId,
                productId: "",
                receiptToken: "",
                status: .failed,
                purchasedAt: 0
            )
        }

        for await result in StoreKit.Transaction.all {
            guard case .verified(let tx) = result,
                  tx.id == txId else { continue }

            await tx.finish()

            return Transaction(
                transactionId: String(tx.id),
                productId: tx.productID,
                receiptToken: tx.jwsRepresentation,
                status: .completed,
                purchasedAt: Int64(
                    tx.purchaseDate
                        .timeIntervalSince1970 * 1000
                )
            )
        }

        return Transaction(
            transactionId: transactionId,
            productId: "",
            receiptToken: "",
            status: .failed,
            purchasedAt: 0
        )
    }
}
