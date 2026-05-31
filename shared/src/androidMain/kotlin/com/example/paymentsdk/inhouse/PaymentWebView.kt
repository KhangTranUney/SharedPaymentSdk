package com.example.paymentsdk.inhouse

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.paymentsdk.PlatformContext
import kotlinx.coroutines.CompletableDeferred

/**
 * Android actual: launches a Chrome Custom Tab.
 *
 * Registers a lifecycle observer on the Activity to
 * detect when the user returns without completing
 * payment (back press from Custom Tab). No caller
 * wiring needed.
 */
internal actual class PaymentWebView actual constructor(
    private val context: PlatformContext
) {

    private var pendingResult: CompletableDeferred<WebViewResult>? = null
    private var customTabOpen = false

    private val lifecycleObserver = object : DefaultLifecycleObserver {

        override fun onResume(owner: LifecycleOwner) {
            if (!customTabOpen) return
            customTabOpen = false

            // Delay to let handleCallback() fire first
            // if a redirect arrived at the same time.
            Handler(Looper.getMainLooper()).postDelayed({
                val pending = pendingResult ?: return@postDelayed
                if (!pending.isCompleted) {
                    pending.complete(WebViewResult.Dismissed)
                    pendingResult = null
                }
            }, 500)
        }
    }

    actual suspend fun open(
        checkoutUrl: String,
        callbackScheme: String
    ): WebViewResult {

        val deferred = CompletableDeferred<WebViewResult>()
        pendingResult = deferred

        (context as LifecycleOwner).lifecycle
            .addObserver(lifecycleObserver)

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabOpen = true
        customTabsIntent.launchUrl(
            context,
            Uri.parse(checkoutUrl)
        )

        return deferred.await().also {
            (context as LifecycleOwner).lifecycle
                .removeObserver(lifecycleObserver)
        }
    }

    actual fun handleCallback(uri: String) {
        customTabOpen = false

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
}
