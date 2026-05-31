package com.example.paymentsdk.inhouse

import com.example.paymentsdk.PaymentSdk

/**
 * In-house payment SDK (Stripe / PayPal via system browser).
 *
 * Platform constructors:
 * - Android: `InHousePaymentSdk(activity, clientId, baseUrl)`
 * - iOS:     `InHousePaymentSdk(clientId, baseUrl)`
 *
 * All internals (HTTP client, browser) are hidden.
 * Host app must forward deep links to [handleCallback].
 */
expect class InHousePaymentSdk : PaymentSdk {

    /**
     * Forward deep link callbacks here.
     * - Android: call from onNewIntent()
     * - iOS: call from .onOpenURL()
     */
    fun handleCallback(url: String)

    /**
     * Android: call from onResume() with ~500ms delay.
     * iOS: no-op (SFSafariVC delegate handles dismissal).
     */
    fun handleUserReturn()
}
