package com.example.paymentsdk.models

/**
 * Represents a product ready to be displayed in the UI.
 * Works for both native store products and in-house products.
 */
data class Product(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val price: Double,
    val currencyCode: String,
    val discount: Discount? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class Discount(
    val originalPrice: Double,
    val formattedOriginalPrice: String,
    val percentage: Int
)
