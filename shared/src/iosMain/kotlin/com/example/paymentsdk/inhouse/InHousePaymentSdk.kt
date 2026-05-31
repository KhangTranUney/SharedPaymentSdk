package com.example.paymentsdk.inhouse

import com.example.paymentsdk.PaymentSdk
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json

/**
 * iOS actual.
 *
 * ```swift
 * let sdk = InHousePaymentSdk(
 *     clientId: "your-company-id",
 *     baseUrl: "https://api.example.com"
 * )
 * ```
 */
actual class InHousePaymentSdk private constructor(
    private val impl: InHousePaymentSdkImpl
) : PaymentSdk by impl {

    constructor(
        clientId: String,
        baseUrl: String,
        callbackScheme: String = "myapp"
    ) : this(
        InHousePaymentSdkImpl(
            apiClient = PaymentApiClient(
                baseUrl,
                HttpClient {
                    install(ContentNegotiation) { json() }
                    defaultRequest {
                        headers.append("X-Client-Id", clientId)
                    }
                }
            ),
            webView = PaymentWebView(),
            callbackScheme = callbackScheme
        )
    )

    actual fun handleCallback(url: String) =
        impl.handleCallback(url)

    actual fun handleUserReturn() =
        impl.handleUserReturn()
}
