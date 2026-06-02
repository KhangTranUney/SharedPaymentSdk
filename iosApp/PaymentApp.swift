import SwiftUI
import shared

@main
struct PaymentApp: App {

    let paymentSdk: PaymentSdk
    let inHouseSdk: InHousePaymentSdk?

    init() {
        let useStoreTrack = true // feature flag

        if useStoreTrack {
            paymentSdk = StorePaymentSdk(
                opsBaseUrl: "https://ops.example.com",
                // Host-supplied auth — Ops Platform derives
                // userId server-side from this token.
                authTokenProvider: { await AuthStore.shared.accessToken() }
            )
            inHouseSdk = nil
        } else {
            let sdk = InHousePaymentSdk(
                context: PlatformContext(),
                clientId: "your-company-id",
                baseUrl: "https://api.example.com"
            )
            paymentSdk = sdk
            inHouseSdk = sdk
        }
    }

    var body: some Scene {
        WindowGroup {
            CheckoutView(paymentSdk: paymentSdk)
                .onOpenURL { url in
                    inHouseSdk?.handlePaymentCallback(
                        uri: url.absoluteString
                    )
                }
        }
    }
}
