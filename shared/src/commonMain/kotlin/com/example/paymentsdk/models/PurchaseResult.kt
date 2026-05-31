package com.example.paymentsdk.models

/**
 * The result of a purchase attempt.
 *
 * Sealed interface (not sealed class) so subtypes can
 * also implement other interfaces if needed.
 *
 * Both NativePaymentSdk and InHousePaymentSdk return the
 * same types — the caller does not know which track ran.
 */
sealed interface PurchaseResult {

    data class Success(
        val transactionId: String
    ) : PurchaseResult

    data object UserCanceled : PurchaseResult

    data class Error(
        val code: Int,
        val message: String
    ) : PurchaseResult
}
