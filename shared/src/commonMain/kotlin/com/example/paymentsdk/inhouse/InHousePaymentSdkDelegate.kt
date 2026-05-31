package com.example.paymentsdk.inhouse

import com.example.paymentsdk.PaymentSdk
import com.example.paymentsdk.models.Product
import com.example.paymentsdk.models.PurchaseResult
import com.example.paymentsdk.models.Transaction

/**
 * Internal delegate containing all in-house payment
 * business logic. Shared across platforms.
 *
 * The public [InHousePaymentSdk] (expect/actual) wraps
 * this via Kotlin class delegation (`by delegate`).
 */
internal class InHousePaymentSdkDelegate(
    private val apiClient: PaymentApiClient,
    private val webView: PaymentWebView,
    private val callbackScheme: String
) : PaymentSdk {

    fun handleCallback(url: String) {
        webView.handleCallback(url)
    }

    fun handleUserReturn() {
        webView.handleUserReturn()
    }

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
