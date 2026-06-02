package com.example.paymentsdk.models

/**
 * The result of a purchase attempt.
 *
 * Sealed interface (not sealed class) so subtypes can
 * also implement other interfaces if needed.
 *
 * Both StorePaymentSdk and InHousePaymentSdk return the
 * same types — the caller does not know which track ran.
 */
sealed interface PurchaseResult {

    /**
     * @param transactionId display id of the transaction.
     *   - StorePaymentSdk Android: Purchase.orderId
     *   - StorePaymentSdk iOS:     Transaction.id (as String)
     *   - InHousePaymentSdk:        gateway transaction id
     * @param receiptToken server-verifiable receipt; the canonical
     *   primary key for backend verification and dedup.
     *   - StorePaymentSdk Android: Purchase.purchaseToken
     *   - StorePaymentSdk iOS:     Transaction.jwsRepresentation
     *   - InHousePaymentSdk:        empty (gateway webhook is authoritative)
     */
    data class Success(
        val transactionId: String,
        val receiptToken: String
    ) : PurchaseResult

    data object UserCanceled : PurchaseResult

    data class Error(
        val code: Int,
        val message: String
    ) : PurchaseResult
}
