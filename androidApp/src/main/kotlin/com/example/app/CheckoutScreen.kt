package com.example.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.paymentsdk.PaymentSdk

/**
 * Composable checkout screen.
 *
 * Calls [PaymentSdk] via [CheckoutViewModel].
 * Works identically for both NativePaymentSdk and
 * InHousePaymentSdk — the Composable doesn't know
 * which track is active.
 */
@Composable
fun CheckoutScreen(
    paymentSdk: PaymentSdk,
    viewModel: CheckoutViewModel = viewModel {
        CheckoutViewModel(paymentSdk)
    }
) {
    val products by viewModel.products
        .collectAsStateWithLifecycle()
    val uiState by viewModel.uiState
        .collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadProducts(
            listOf("premium_monthly", "premium_yearly")
        )
    }

    Column(modifier = Modifier.padding(16.dp)) {

        when (uiState) {

            is CheckoutUiState.Loading -> {
                CircularProgressIndicator()
            }

            is CheckoutUiState.Verifying -> {
                Text("Verifying your purchase...")
                CircularProgressIndicator()
            }

            is CheckoutUiState.Success -> {
                Text("Purchase complete!")
            }

            is CheckoutUiState.Error -> {
                Text(
                    "Error: ${(uiState as CheckoutUiState.Error).message}",
                    color = MaterialTheme.colorScheme.error
                )
            }

            is CheckoutUiState.Idle -> {
                products.forEach { product ->
                    Button(
                        onClick = {
                            viewModel.onBuyClicked(product)
                        }
                    ) {
                        Text(
                            "${product.title} — " +
                                product.formattedPrice
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
