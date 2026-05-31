package com.example.paymentsdk.inhouse

import android.content.Intent
import com.example.paymentsdk.PlatformContext
import kotlinx.coroutines.CompletableDeferred

/**
 * Android actual: delegates to [PaymentBrowserActivity] which
 * launches a Custom Tab and [PaymentRedirectActivity] which
 * receives the callback. Fully self-contained — the caller
 * never handles deep links.
 */
internal actual class PaymentWebView actual constructor(
    private val context: PlatformContext
) {

    companion object {
        internal var pendingResult: CompletableDeferred<WebViewResult>? = null
    }

    actual suspend fun open(
        checkoutUrl: String,
        callbackScheme: String
    ): WebViewResult {

        val deferred = CompletableDeferred<WebViewResult>()
        pendingResult = deferred

        val intent = Intent(context, PaymentBrowserActivity::class.java)
        intent.putExtra(PaymentBrowserActivity.EXTRA_CHECKOUT_URL, checkoutUrl)
        context.startActivity(intent)

        return deferred.await()
    }

    actual fun handleOpenURL(url: String): Boolean {
        // On Android, PaymentRedirectActivity handles this
        return false
    }
}
