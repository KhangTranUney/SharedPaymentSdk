package com.example.paymentsdk

import com.example.paymentsdk.models.Product
import com.example.paymentsdk.models.PurchaseResult
import com.example.paymentsdk.models.Transaction

/**
 * Unified payment interface.
 *
 * Both StorePaymentSdk and InHousePaymentSdk implement
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
     * Fetch product display details for the given ids.
     *
     * The host app is expected to fetch its product catalog
     * (ids + any local metadata) from its own backend first,
     * then pass the ids here. Both tracks require explicit
     * ids — no "list all" API is supported.
     */
    suspend fun getProducts(
        productIds: List<String>
    ): List<Product>

    /**
     * Start a purchase flow for the given product.
     * Suspends until the user completes, cancels, or an
     * error occurs.
     *
     * - StorePaymentSdk: launches OS payment sheet
     * - InHousePaymentSdk: opens WebView with checkout URL,
     *   intercepts callback, closes WebView — all handled
     *   internally by the SDK
     */
    suspend fun purchase(product: Product): PurchaseResult

    /**
     * Get the final transaction result after purchase, and
     * acknowledge it with the underlying provider.
     *
     * Takes the full [PurchaseResult.Success] so both
     * `transactionId` (iOS lookup key) and `receiptToken`
     * (Android lookup key / server primary key) are available.
     *
     * - StorePaymentSdk: acks/finishes with the store, then
     *   POSTs the receipt to the Ops Platform.
     * - InHousePaymentSdk: calls backend API with txId.
     *   Fulfillment is driven by the gateway webhook on the
     *   backend, so no extra client-side ack is needed.
     */
    suspend fun getTransactionResult(
        purchase: PurchaseResult.Success
    ): Transaction
}
