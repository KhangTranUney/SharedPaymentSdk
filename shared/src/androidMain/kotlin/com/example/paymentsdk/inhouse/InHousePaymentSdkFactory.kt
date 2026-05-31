package com.example.paymentsdk.inhouse

import android.app.Activity
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json

/**
 * Android factory for [InHousePaymentSdk].
 *
 * Creates the SDK with all internal dependencies
 * (HTTP client, browser) wired up. The caller only
 * provides business-level parameters.
 *
 * ```kotlin
 * val sdk = InHousePaymentSdk(
 *     activity = this,
 *     clientId = "your-company-id",
 *     baseUrl = "https://api.example.com"
 * )
 * ```
 */
fun InHousePaymentSdk(
    activity: Activity,
    clientId: String,
    baseUrl: String,
    callbackScheme: String = "myapp"
): InHousePaymentSdk {

    val httpClient = HttpClient {
        install(ContentNegotiation) { json() }
        defaultRequest {
            headers.append("X-Client-Id", clientId)
        }
    }

    return InHousePaymentSdk(
        apiClient = PaymentApiClient(baseUrl, httpClient),
        webView = PaymentWebView(activity),
        callbackScheme = callbackScheme
    )
}
