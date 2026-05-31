package com.example.paymentsdk.inhouse

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.example.paymentsdk.PlatformContext
import kotlinx.coroutines.CompletableDeferred

/**
 * Android actual: launches a Chrome Custom Tab.
 * Internal — callers interact via [InHousePaymentSdk].
 */
internal actual class PaymentWebView actual constructor(
    private val context: PlatformContext
) {

    private var pendingResult: CompletableDeferred<WebViewResult>? = null

    actual suspend fun open(
        checkoutUrl: String,
        callbackScheme: String
    ): WebViewResult {

        val deferred = CompletableDeferred<WebViewResult>()
        pendingResult = deferred

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(
            context,
            Uri.parse(checkoutUrl)
        )

        return deferred.await()
    }

    actual fun handleCallback(uri: String) {
        val parsed = Uri.parse(uri)
        val params = mutableMapOf<String, String>()

        parsed.queryParameterNames.forEach { key ->
            parsed.getQueryParameter(key)?.let {
                params[key] = it
            }
        }

        pendingResult?.complete(
            WebViewResult.CallbackReceived(params)
        )
        pendingResult = null
    }

    actual fun handleUserReturn() {
        val pending = pendingResult ?: return

        if (!pending.isCompleted) {
            pending.complete(WebViewResult.Dismissed)
            pendingResult = null
        }
    }
}
