import SwiftUI
import shared

@main
struct PaymentApp: App {

    let paymentSdk: PaymentSdk
    let inHouseSdk: InHousePaymentSdk?

    init() {
        let useNativeStore = true // feature flag

        if useNativeStore {
            paymentSdk = NativePaymentSdk()
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
                    if url.scheme == "myapp" {
                        inHouseSdk?.handleCallback(
                            url: url.absoluteString
                        )
                    }
                }
        }
    }
}
