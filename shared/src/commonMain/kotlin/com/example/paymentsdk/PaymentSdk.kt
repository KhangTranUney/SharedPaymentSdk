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
 *                              (SDK acknowledges / finishes
 *                              the transaction internally)
 */
interface PaymentSdk {

    /**
     * Fetch available products for display.
     *
     * @param productIds optional list of product ids to fetch.
     *   - InHousePaymentSdk: when null, the backend returns
     *     the full catalog for this client.
     *   - NativePaymentSdk: required (store APIs need ids).
     *     Returns an empty list when null.
     */
    suspend fun getProducts(
        productIds: List<String>? = null
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
     * Get the final transaction result after purchase, and
     * acknowledge it with the underlying provider.
     *
     * - NativePaymentSdk: queries the store, then calls
     *   `transaction.finish()` (iOS) or `acknowledgePurchase`
     *   / `consumeAsync` (Android) before returning.
     * - InHousePaymentSdk: calls backend API with txId.
     *   Fulfillment is driven by the gateway webhook on the
     *   backend, so no extra client-side ack is needed.
     */
    suspend fun getTransactionResult(
        transactionId: String
    ): Transaction
}
