package com.example.paymentsdk

import com.example.paymentsdk.models.Product
import com.example.paymentsdk.models.PurchaseResult
import com.example.paymentsdk.models.Transaction

/**
 * Unified payment interface.
 *
 * Both NativePaymentSdk and InHousePaymentSdk implement
 * this contract. The host app's ViewModel programs against
 * this interface — it does not know which track is active.
 *
 * Caller flow:
 * 1. getProducts()          -> render your own UI
 * 2. purchase(product)      -> SDK handles checkout UI
 * 3. getTransactionResult() -> confirm payment details
 * 4. finishTransaction()    -> acknowledge / fulfill
 */
interface PaymentSdk {

    /**
     * Fetch available products for display.
     *
     * - NativePaymentSdk: queries Google Play / App Store
     * - InHousePaymentSdk: calls backend API
     */
    suspend fun getProducts(
        productIds: List<String>
    ): List<Product>

    /**
     * Start a purchase flow for the given product.
     * Suspends until the user completes, cancels, or an
     * error occurs.
     *
     * - NativePaymentSdk: launches OS payment sheet
     * - InHousePaymentSdk: opens WebView with checkout URL,
     *   intercepts callback, closes WebView — all handled
     *   internally by the SDK
     */
    suspend fun purchase(product: Product): PurchaseResult

    /**
     * Get the final transaction result after purchase.
     *
     * - NativePaymentSdk: queries store for transaction
     * - InHousePaymentSdk: calls backend API with txId
     */
    suspend fun getTransactionResult(
        transactionId: String
    ): Transaction

    /**
     * Acknowledge that the transaction was processed.
     *
     * - NativePaymentSdk: acknowledgePurchase() or
     *   transaction.finish()
     * - InHousePaymentSdk: calls backend API to verify
     *   receipt and mark as fulfilled
     */
    suspend fun finishTransaction(transactionId: String)
}
