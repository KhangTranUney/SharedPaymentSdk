import Foundation
import shared

/// UI state for the checkout screen.
enum CheckoutUiState {
    case idle
    case loading
    case verifying
    case success
    case error(String)
}

/// ViewModel that drives the checkout SwiftUI View.
///
/// Programs against PaymentSdk — the same code works
/// for both StorePaymentSdk and InHousePaymentSdk.
///
/// Flow:
/// 1. loadProducts()    -> getProducts()
/// 2. onBuyClicked()    -> purchase() -> postReceipt()
///    (the SDK acknowledges / finishes the transaction
///    internally inside postReceipt)
@MainActor
class CheckoutViewModel: ObservableObject {

    private let paymentSdk: PaymentSdk

    @Published var products: [Product] = []
    @Published var uiState: CheckoutUiState = .idle

    init(paymentSdk: PaymentSdk) {
        self.paymentSdk = paymentSdk
    }

    // MARK: - Load Products

    func loadProducts() async {
        uiState = .loading

        products = (try? await paymentSdk.getProducts(
            productIds: ["premium_monthly", "premium_yearly"]
        )) ?? []

        uiState = .idle
    }

    // MARK: - Purchase

    func onBuyClicked(product: Product) {
        Task {
            uiState = .loading

            guard let result = try? await
                paymentSdk.purchase(product: product)
            else {
                uiState = .error("Purchase failed")
                return
            }

            switch result {

            case let success as PurchaseResult.Success:
                uiState = .verifying

                let tx = try? await
                    paymentSdk.postReceipt(purchase: success)

                if tx?.status == .completed ||
                   tx?.status == .verified {
                    uiState = .success
                } else {
                    uiState = .error("Transaction failed")
                }

            case is PurchaseResult.UserCanceled:
                uiState = .idle

            case let error as PurchaseResult.Error:
                uiState = .error(error.message)

            default:
                uiState = .idle
            }
        }
    }
}
