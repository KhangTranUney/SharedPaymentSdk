package com.example.paymentsdk.models

/**
 * A completed transaction with verification status.
 */
data class Transaction(
    val transactionId: String,
    val productId: String,
    val receiptToken: String,
    val status: TransactionStatus,
    val purchasedAt: Long
)

enum class TransactionStatus {
    PENDING,
    COMPLETED,
    VERIFIED,
    FAILED
}
