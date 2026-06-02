package com.example.paymentsdk.inhouse

import com.example.paymentsdk.PaymentSdk
import com.example.paymentsdk.PlatformContext
import com.example.paymentsdk.models.Product
import com.example.paymentsdk.models.PurchaseResult
import com.example.paymentsdk.models.Transaction
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json

/**
 * In-house payment SDK (Stripe / PayPal via system browser).
 *
 * ```kotlin
 * // Android
 * val sdk = InHousePaymentSdk(this, "client-id", "https://api.example.com")
 *
 * // iOS
 * val sdk = InHousePaymentSdk(PlatformContext(), "client-id", "https://api.example.com")
 * ```
 *
 * All internals (HTTP client, browser) are hidden.
 * Host app must forward deep links via [handlePaymentCallback].
 */
class InHousePaymentSdk(
    context: PlatformContext,
    clientId: String,
    baseUrl: String,
    private val callbackScheme: String = "myapp"
) : PaymentSdk {

    private val apiClient = InHouseOpsApiClient(
        baseUrl,
        HttpClient {
            install(ContentNegotiation) { json() }
            defaultRequest {
                headers.append("X-Client-Id", clientId)
            }
        }
    )

    private val webView = PaymentWebView(context)

    /**
     * Forward incoming URLs to the SDK. The SDK checks
     * the scheme internally — no comparison needed.
     *
     * - Android: call from onNewIntent()
     * - iOS: call from .onOpenURL()
     *
     * @return true if the URL was handled by the SDK
     */
    fun handlePaymentCallback(uri: String): Boolean {
        if (!uri.startsWith("$callbackScheme://")) return false
        webView.handleCallback(uri)
        return true
    }

    // ---- PaymentSdk ----

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
                        transactionId = txId ?: "",
                        // Gateway webhook on backend is authoritative;
                        // SDK has no client-side receipt for in-house.
                        receiptToken = ""
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
        purchase: PurchaseResult.Success
    ): Transaction {
        // Fulfillment is driven by the gateway webhook on the
        // backend — no client-side ack call is needed here.
        return apiClient.getTransaction(purchase.transactionId)
    }
}
