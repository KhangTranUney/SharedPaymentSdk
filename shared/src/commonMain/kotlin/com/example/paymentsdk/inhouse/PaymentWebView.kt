package com.example.paymentsdk.inhouse

import com.example.paymentsdk.PlatformContext

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
 * - Android: CustomTabsIntent via transparent Activity
 *   (fully self-contained, no caller wiring needed)
 * - iOS: SFSafariViewController
 *   (caller forwards URLs via [handleOpenURL])
 *
 * Internal to the SDK.
 */
internal expect class PaymentWebView(context: PlatformContext) {

    suspend fun open(
        checkoutUrl: String,
        callbackScheme: String
    ): WebViewResult

    /**
     * Forward an incoming URL to the SDK.
     * - Android: returns false (handled internally)
     * - iOS: checks scheme, completes deferred, returns true if handled
     */
    fun handleOpenURL(url: String): Boolean
}
