package com.example.paymentsdk.inhouse

import com.example.paymentsdk.PaymentSdk
import com.example.paymentsdk.PlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json

/**
 * In-house payment SDK (Stripe / PayPal via system browser).
 *
 * ```kotlin
 * // Android
 * val sdk = InHousePaymentSdk(this, "client-id", "https://api.example.com")
 *
 * // iOS
 * val sdk = InHousePaymentSdk(PlatformContext(), "client-id", "https://api.example.com")
 * ```
 *
 * All internals (HTTP client, browser) are hidden.
 * Host app must forward deep links to [handleCallback].
 */
class InHousePaymentSdk private constructor(
    private val delegate: InHousePaymentSdkDelegate
) : PaymentSdk by delegate {

    constructor(
        context: PlatformContext,
        clientId: String,
        baseUrl: String,
        callbackScheme: String = "myapp"
    ) : this(
        InHousePaymentSdkDelegate(
            apiClient = PaymentApiClient(
                baseUrl,
                HttpClient {
                    install(ContentNegotiation) { json() }
                    defaultRequest {
                        headers.append("X-Client-Id", clientId)
                    }
                }
            ),
            webView = PaymentWebView(context),
            callbackScheme = callbackScheme
        )
    )

    /**
     * Forward deep link callbacks here.
     * - Android: call from onNewIntent()
     * - iOS: call from .onOpenURL()
     */
    fun handleCallback(url: String) =
        delegate.handleCallback(url)

    /**
     * Android: call from onResume() with ~500ms delay.
     * iOS: no-op (SFSafariVC delegate handles dismissal).
     */
    fun handleUserReturn() =
        delegate.handleUserReturn()
}
