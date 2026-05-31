package com.example.paymentsdk.inhouse

/**
 * Result of a browser checkout session.
 */
internal sealed interface WebViewResult {

    data class CallbackReceived(
        val parameters: Map<String, String>
    ) : WebViewResult

    data object Dismissed : WebViewResult
}

/**
 * Platform-specific browser for checkout.
 *
 * - Android: CustomTabsIntent (Chrome Custom Tab)
 * - iOS: SFSafariViewController
 *
 * Internal to the SDK — callers never see this class.
 * Deep link callbacks are forwarded via
 * [InHousePaymentSdk.handleCallback].
 */
internal expect class PaymentWebView {

    suspend fun open(
        checkoutUrl: String,
        callbackScheme: String
    ): WebViewResult

    fun handleCallback(url: String)

    /**
     * Detect user returning without completing payment.
     * Android: called from onResume() with a delay.
     * iOS: no-op (SFSafariVC delegate handles this).
     */
    fun handleUserReturn()
}
