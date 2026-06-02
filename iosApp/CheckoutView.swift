import SwiftUI
import shared

/// SwiftUI checkout view.
///
/// Calls PaymentSdk via CheckoutViewModel.
/// Works identically for both StorePaymentSdk and
/// InHousePaymentSdk — the View doesn't know which
/// track is active.
struct CheckoutView: View {

    @StateObject private var viewModel: CheckoutViewModel

    init(paymentSdk: PaymentSdk) {
        _viewModel = StateObject(wrappedValue:
            CheckoutViewModel(paymentSdk: paymentSdk)
        )
    }

    var body: some View {
        VStack(spacing: 16) {

            switch viewModel.uiState {

            case .loading:
                ProgressView()

            case .verifying:
                Text("Verifying your purchase...")
                ProgressView()

            case .success:
                Text("Purchase complete!")

            case .error(let message):
                Text("Error: \(message)")
                    .foregroundColor(.red)

            case .idle:
                ForEach(
                    viewModel.products,
                    id: \.productId
                ) { product in
                    Button {
                        viewModel.onBuyClicked(
                            product: product
                        )
                    } label: {
                        Text(
                            "\(product.title) — " +
                            "\(product.formattedPrice)"
                        )
                    }
                }
            }
        }
        .padding()
        .task {
            await viewModel.loadProducts()
        }
    }
}
