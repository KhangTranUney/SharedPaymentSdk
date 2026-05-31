package com.example.paymentsdk.models

/**
 * Returned by the backend when creating a checkout session.
 * Internal to InHousePaymentSdk — the caller never sees this.
 */
internal data class CheckoutInfo(
    val checkoutUrl: String,
    val sessionId: String,
    val expiresAt: Long
)
