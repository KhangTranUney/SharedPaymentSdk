package com.example.app.sdkwrapper

import android.app.Activity
import com.android.billingclient.api.*
import com.example.paymentsdk.PaymentSdk
import com.example.paymentsdk.models.Product
import com.example.paymentsdk.models.PurchaseResult
import com.example.paymentsdk.models.Transaction
import com.example.paymentsdk.models.TransactionStatus
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
class NativePaymentSdk(
    private val activity: Activity
) : PaymentSdk {

    private val pendingPurchaseContinuation =
        AtomicReference<CancellableContinuation<PurchaseResult>?>(null)

    private val billingClient = BillingClient.newBuilder(activity)
        .setListener { billingResult, purchases ->

            val continuation =
                pendingPurchaseContinuation.getAndSet(null)

            when (billingResult.responseCode) {

                BillingClient.BillingResponseCode.OK -> {
                    val orderId =
                        purchases?.firstOrNull()?.orderId ?: ""
                    continuation?.resume(
                        PurchaseResult.Success(orderId)
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

        return suspendCancellableCoroutine { continuation ->

            pendingPurchaseContinuation.set(continuation)

            continuation.invokeOnCancellation {
                pendingPurchaseContinuation.compareAndSet(
                    continuation, null
                )
            }

            // Look up cached ProductDetails by productId
            // and launch billing flow...
            // billingClient.launchBillingFlow(activity, params)
        }
    }

    /**
     * Queries the store for the transaction and, if it is in
     * the `PURCHASED` state and not yet acknowledged, calls
     * `acknowledgePurchase` (subs / non-consumable) or
     * `consumeAsync` (consumables) before returning.
     *
     * Google Play auto-refunds purchases that are not
     * acknowledged within 3 days, so this must run on every
     * successful purchase.
     *
     * Backend stays in sync via Play Real-time Developer
     * Notifications (Google → backend), and the backend pushes
     * updates to the app out-of-band (FCM) — no HTTP call from
     * the SDK is needed.
     */
    override suspend fun getTransactionResult(
        transactionId: String
    ): Transaction {

        ensureConnected()

        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val purchase = result.purchasesList.firstOrNull {
            it.orderId == transactionId
        }

        if (purchase != null &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            !purchase.isAcknowledged
        ) {
            // acknowledgePurchase() for subs / non-consumables,
            // or consumeAsync() for consumables.
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams)
        }

        return Transaction(
            transactionId = transactionId,
            productId = purchase?.products?.first() ?: "",
            receiptToken = purchase?.purchaseToken ?: "",
            status = when (purchase?.purchaseState) {
                Purchase.PurchaseState.PENDING ->
                    TransactionStatus.PENDING
                Purchase.PurchaseState.PURCHASED ->
                    TransactionStatus.COMPLETED
                else ->
                    TransactionStatus.FAILED
            },
            purchasedAt = purchase?.purchaseTime ?: 0
        )
    }
}
