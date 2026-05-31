package com.example.paymentsdk.inhouse

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CompletableDeferred

/**
 * Android actual: launches a Chrome Custom Tab.
 * Internal — callers interact via [InHousePaymentSdk].
 */
internal actual class PaymentWebView(
    private val activity: Activity
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
            activity,
            Uri.parse(checkoutUrl)
        )

        return deferred.await()
    }

    actual fun handleCallback(url: String) {
        val uri = Uri.parse(url)
        val params = mutableMapOf<String, String>()

        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameter(key)?.let {
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
