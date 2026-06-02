package com.example.app.sdkwrapper

import android.app.Activity
import com.android.billingclient.api.*
import com.example.paymentsdk.PaymentSdk
import com.example.paymentsdk.models.Product
import com.example.paymentsdk.models.PurchaseResult
import com.example.paymentsdk.models.Transaction
import com.example.paymentsdk.store.StoreOpsApiClient
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Android implementation of [PaymentSdk] wrapping
 * Google Play Billing Library.
 *
 * Lives in the androidApp layer because Google Play
 * Billing is an Android-only SDK.
 */
class StorePaymentSdk(
    private val activity: Activity,
    opsBaseUrl: String,
    authTokenProvider: suspend () -> String
) : PaymentSdk {

    private val opsClient = StoreOpsApiClient(
        baseUrl = opsBaseUrl,
        authTokenProvider = authTokenProvider
    )

    private val pendingPurchaseContinuation =
        AtomicReference<CancellableContinuation<PurchaseResult>?>(null)

    // Caches the live Purchase between purchase() and
    // getTransactionResult() so we don't have to
    // queryPurchasesAsync + filter again. Survives within
    // a process; on cold restart, we fall back to a query.
    private val pendingPurchases =
        mutableMapOf<String, Purchase>()

    // Caches ProductDetails so purchase() can launch the
    // billing flow without re-querying. Populated by
    // getProducts().
    private val productDetailsCache =
        mutableMapOf<String, ProductDetails>()

    private val billingClient = BillingClient.newBuilder(activity)
        .setListener { billingResult, purchases ->

            val continuation =
                pendingPurchaseContinuation.getAndSet(null)

            when (billingResult.responseCode) {

                BillingClient.BillingResponseCode.OK -> {
                    val purchase = purchases?.firstOrNull()
                    val orderId = purchase?.orderId ?: ""
                    val token = purchase?.purchaseToken ?: ""
                    if (purchase != null) {
                        // Key by both ids so getTransactionResult
                        // can look up regardless of which the
                        // caller passed back.
                        pendingPurchases[orderId] = purchase
                        pendingPurchases[token] = purchase
                    }
                    continuation?.resume(
                        PurchaseResult.Success(
                            transactionId = orderId,
                            receiptToken = token
                        )
                    )
                }

                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    continuation?.resume(
                        PurchaseResult.UserCanceled
                    )
                }

                else -> {
                    continuation?.resume(
                        PurchaseResult.Error(
                            billingResult.responseCode,
                            billingResult.debugMessage
                        )
                    )
                }
            }
        }
        .enablePendingPurchases()
        .build()

    // ---- Connection ----

    suspend fun connect() = suspendCancellableCoroutine { cont ->
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(
                    result: BillingResult
                ) {
                    cont.resume(Unit)
                }
                override fun onBillingServiceDisconnected() {}
            }
        )
    }

    private suspend fun ensureConnected() {
        if (!billingClient.isReady) connect()
    }

    suspend fun syncMissedPurchases() {
        ensureConnected()
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        // Process un-acknowledged purchases...
    }

    // ---- PaymentSdk Interface ----

    override suspend fun getProducts(
        productIds: List<String>
    ): List<Product> {

        if (productIds.isEmpty()) return emptyList()

        ensureConnected()

        val productList = productIds.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }

        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val (billingResult, detailsList) =
            billingClient.queryProductDetails(queryParams)

        if (billingResult.responseCode !=
            BillingClient.BillingResponseCode.OK
        ) {
            return emptyList()
        }

        return detailsList?.map { details ->
            productDetailsCache[details.productId] = details

            val phase = details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()

            Product(
                productId = details.productId,
                title = details.name,
                description = details.description,
                formattedPrice = phase?.formattedPrice ?: "",
                price = (phase?.priceAmountMicros
                    ?: 0) / 1_000_000.0,
                currencyCode = phase?.priceCurrencyCode ?: ""
            )
        } ?: emptyList()
    }

    override suspend fun purchase(
        product: Product
    ): PurchaseResult {

        ensureConnected()

        val details = productDetailsCache[product.productId]
            ?: return PurchaseResult.Error(
                code = -1,
                message = "Unknown product ${product.productId}; " +
                    "call getProducts() first"
            )

        return suspendCancellableCoroutine { continuation ->

            pendingPurchaseContinuation.set(continuation)

            continuation.invokeOnCancellation {
                pendingPurchaseContinuation.compareAndSet(
                    continuation, null
                )
            }

            val offerToken = details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken

            val productParams = BillingFlowParams.ProductDetailsParams
                .newBuilder()
                .setProductDetails(details)
                .apply { offerToken?.let { setOfferToken(it) } }
                .build()

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()

            billingClient.launchBillingFlow(activity, flowParams)
        }
    }

    /**
     * 1. Resolves the live `Purchase` (cached from `purchase()`,
     *    falls back to `queryPurchasesAsync` by purchaseToken).
     * 2. Acks/finishes the purchase. Google auto-refunds
     *    purchases that are not acknowledged within 3 days,
     *    so this must run on every successful purchase.
     * 3. POSTs the receipt to the Ops Platform for
     *    server-side verification (wire your HTTP client).
     *
     * The host app is responsible for forwarding the returned
     * [Transaction] to its own backend to grant entitlement —
     * the SDK does not call the host backend.
     */
    override suspend fun getTransactionResult(
        purchase: PurchaseResult.Success
    ): Transaction {

        ensureConnected()

        // 1. Prefer the cached live Purchase from purchase().
        //    Fall back to queryPurchasesAsync on cold restart.
        val storePurchase = pendingPurchases[purchase.receiptToken]
            ?: pendingPurchases[purchase.transactionId]
            ?: queryPurchaseByToken(purchase.receiptToken)

        // 2. Ack/finish so Google does not auto-refund after 3 days.
        if (storePurchase != null &&
            storePurchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            !storePurchase.isAcknowledged
        ) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(storePurchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams)
        }

        // 3. Clear cache entries for this purchase.
        pendingPurchases.remove(purchase.receiptToken)
        pendingPurchases.remove(purchase.transactionId)

        // 4. POST receipt to Ops Platform. Ops verifies with
        //    Google Play Developer API server-side and returns
        //    the canonical Transaction.
        return opsClient.verifyReceipt(
            transactionId = purchase.transactionId,
            receiptToken = purchase.receiptToken
        )
    }

    private suspend fun queryPurchaseByToken(
        token: String
    ): Purchase? {
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        return result.purchasesList.firstOrNull {
            it.purchaseToken == token
        }
    }
}
