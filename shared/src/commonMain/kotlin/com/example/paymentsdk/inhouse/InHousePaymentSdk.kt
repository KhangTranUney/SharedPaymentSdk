package com.example.paymentsdk.inhouse

import com.example.paymentsdk.PaymentSdk
import com.example.paymentsdk.models.Product
import com.example.paymentsdk.models.PurchaseResult
import com.example.paymentsdk.models.Transaction

/**
 * In-house payment implementation.
 *
 * Lives entirely in the KMP shared module. The browser
 * and HTTP client are internal — the caller only passes
 * [clientId] and [baseUrl] via platform factory functions.
 *
 * Caller flow:
 * 1. getProducts()      -> render your own UI
 * 2. purchase(product)  -> SDK opens browser, handles
 *                          callback, returns result
 * 3. getTransactionResult() -> confirms with backend
 * 4. finishTransaction()    -> verifies & fulfills
 *
 * Host app must forward deep links to [handleCallback]
 * and (on Android) call [handleUserReturn] from onResume.
 */
class InHousePaymentSdk internal constructor(
    private val apiClient: PaymentApiClient,
    private val webView: PaymentWebView,
    private val callbackScheme: String
) : PaymentSdk {

    // ---- Deep Link Forwarding ----

    /**
     * Called by the host app when a deep link with the
     * callback scheme is received.
     *
     * - Android: call from onNewIntent()
     * - iOS: call from .onOpenURL()
     */
    fun handleCallback(url: String) {
        webView.handleCallback(url)
    }

    /**
     * Called by the host app in onResume() (Android only)
     * to detect when the user returned from the Custom Tab
     * without completing payment.
     *
     * Call with a short delay (~500ms) to allow
     * handleCallback() to arrive first.
     *
     * On iOS this is a no-op — SFSafariViewController's
     * delegate handles dismissal automatically.
     */
    fun handleUserReturn() {
        webView.handleUserReturn()
    }

    // ---- PaymentSdk Interface ----

    override suspend fun getProducts(
        productIds: List<String>
    ): List<Product> {
        return apiClient.fetchProducts(productIds)
    }

    override suspend fun purchase(
        product: Product
    ): PurchaseResult {

        val returnUrl = "$callbackScheme://payment/callback"

        val checkoutInfo = apiClient.createCheckout(
            productId = product.productId,
            returnUrl = returnUrl
        )

        val webViewResult = webView.open(
            checkoutUrl = checkoutInfo.checkoutUrl,
            callbackScheme = callbackScheme
        )

        return when (webViewResult) {

            is WebViewResult.CallbackReceived -> {
                val params = webViewResult.parameters
                val status = params["status"]
                val txId = params["transaction_id"]

                when (status) {
                    "success" -> PurchaseResult.Success(
                        transactionId = txId ?: ""
                    )
                    "cancel" -> PurchaseResult.UserCanceled
                    else -> PurchaseResult.Error(
                        code = -1,
                        message = params["error"]
                            ?: "Payment failed"
                    )
                }
            }

            is WebViewResult.Dismissed -> {
                PurchaseResult.UserCanceled
            }
        }
    }

    override suspend fun getTransactionResult(
        transactionId: String
    ): Transaction {
        return apiClient.getTransaction(transactionId)
    }

    override suspend fun finishTransaction(
        transactionId: String
    ) {
        apiClient.fulfillTransaction(transactionId)
    }
}
