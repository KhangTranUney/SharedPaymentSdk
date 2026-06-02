package com.example.paymentsdk.inhouse

import com.example.paymentsdk.models.CheckoutInfo
import com.example.paymentsdk.models.Product
import com.example.paymentsdk.models.Transaction
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * HTTP client for the in-house payment backend.
 * Internal to the SDK — callers never see this class.
 */
internal class PaymentApiClient(
    private val baseUrl: String,
    private val httpClient: HttpClient
) {

    suspend fun fetchProducts(
        productIds: List<String>?
    ): List<Product> {
        return httpClient.get("$baseUrl/api/products") {
            if (!productIds.isNullOrEmpty()) {
                parameter("ids", productIds.joinToString(","))
            }
        }.body()
    }

    suspend fun createCheckout(
        productId: String,
        returnUrl: String
    ): CheckoutInfo {
        return httpClient.post("$baseUrl/api/checkout") {
            contentType(ContentType.Application.Json)
            setBody(mapOf(
                "productId" to productId,
                "returnUrl" to returnUrl
            ))
        }.body()
    }

    suspend fun getTransaction(
        transactionId: String
    ): Transaction {
        return httpClient.get(
            "$baseUrl/api/transactions/$transactionId"
        ).body()
    }
}
