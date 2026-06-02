package com.example.paymentsdk.store

import com.example.paymentsdk.models.Transaction
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

/**
 * HTTP client for the Ops Platform.
 *
 * Used by [com.example.paymentsdk.PaymentSdk] implementations
 * (store track) to POST receipts for server-side verification.
 *
 * The Ops Platform derives the user from the bearer token, so
 * no `userId` is sent in the body. The host app supplies the
 * token by passing an [authTokenProvider] lambda; the SDK
 * invokes it per request so the host can refresh tokens
 * without re-creating the SDK.
 */
class StoreOpsApiClient(
    private val baseUrl: String,
    private val authTokenProvider: suspend () -> String,
    private val httpClient: HttpClient = HttpClient {
        install(ContentNegotiation) { json() }
    }
) {

    /**
     * Send the receipt to the Ops Platform for verification.
     * Returns the Ops-verified [Transaction] (status = VERIFIED
     * on success).
     */
    suspend fun verifyReceipt(
        transactionId: String,
        receiptToken: String
    ): Transaction {
        return httpClient.post("$baseUrl/verify-receipt") {
            header(
                HttpHeaders.Authorization,
                "Bearer ${authTokenProvider()}"
            )
            contentType(ContentType.Application.Json)
            setBody(
                VerifyReceiptRequest(
                    transactionId = transactionId,
                    receiptToken = receiptToken
                )
            )
        }.body()
    }
}

/**
 * Body for `POST /verify-receipt`. Internal wire type.
 * (Add `@Serializable` when wiring kotlinx-serialization.)
 */
internal data class VerifyReceiptRequest(
    val transactionId: String,
    val receiptToken: String
)
