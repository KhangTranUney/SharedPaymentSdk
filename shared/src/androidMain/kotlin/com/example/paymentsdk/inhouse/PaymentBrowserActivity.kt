package com.example.paymentsdk.inhouse

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Transparent Activity that launches a Chrome Custom Tab
 * and detects when the user returns (back press).
 *
 * Flow:
 * 1. Launched by [PaymentWebView.open] with checkout URL
 * 2. Opens Custom Tab on top of this activity
 * 3. If payment completes → [PaymentRedirectActivity]
 *    handles the redirect and completes the deferred
 * 4. If user presses back → onResume detects this and
 *    signals dismissal
 *
 * Declared in the SDK's AndroidManifest.xml with a
 * transparent theme. The caller never interacts with
 * this activity.
 */
internal class PaymentBrowserActivity : Activity() {

    private var customTabLaunched = false
    private var shouldCheckDismissal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            customTabLaunched =
                savedInstanceState.getBoolean(STATE_LAUNCHED, false)
            shouldCheckDismissal =
                savedInstanceState.getBoolean(STATE_CHECK, false)
            return
        }

        val checkoutUrl = intent.getStringExtra(EXTRA_CHECKOUT_URL)
        if (checkoutUrl == null) {
            finish()
            return
        }

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(this, Uri.parse(checkoutUrl))
        customTabLaunched = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LAUNCHED, customTabLaunched)
        outState.putBoolean(STATE_CHECK, shouldCheckDismissal)
    }

    override fun onResume() {
        super.onResume()

        if (shouldCheckDismissal) {
            // User returned from Custom Tab — either the
            // redirect already completed the deferred, or
            // the user pressed back. Wait briefly to let
            // PaymentRedirectActivity finish first.
            Handler(Looper.getMainLooper()).postDelayed({
                val pending = PaymentWebView.pendingResult
                if (pending != null && !pending.isCompleted) {
                    pending.complete(WebViewResult.Dismissed)
                    PaymentWebView.pendingResult = null
                }
                finish()
            }, 300)
        }

        if (customTabLaunched) {
            shouldCheckDismissal = true
        }
    }

    companion object {
        const val EXTRA_CHECKOUT_URL = "checkout_url"
        private const val STATE_LAUNCHED = "custom_tab_launched"
        private const val STATE_CHECK = "should_check_dismissal"
    }
}
