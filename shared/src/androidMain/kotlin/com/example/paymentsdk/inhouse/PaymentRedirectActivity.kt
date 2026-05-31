package com.example.paymentsdk.inhouse

import android.app.Activity
import android.os.Bundle

/**
 * Transparent Activity that receives the payment callback
 * deep link redirect.
 *
 * Declared in the SDK's AndroidManifest.xml with an
 * intent filter for `myapp://payment/callback`.
 *
 * When the payment gateway redirects to this URL scheme,
 * the OS launches this activity. It parses the query
 * parameters, completes the pending deferred in
 * [PaymentWebView], and finishes immediately.
 *
 * The caller never interacts with this activity.
 */
internal class PaymentRedirectActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        if (uri != null) {
            val params = mutableMapOf<String, String>()
            uri.queryParameterNames.forEach { key ->
                uri.getQueryParameter(key)?.let {
                    params[key] = it
                }
            }

            PaymentWebView.pendingResult?.complete(
                WebViewResult.CallbackReceived(params)
            )
            PaymentWebView.pendingResult = null
        }

        finish()
    }
}
