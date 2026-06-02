package com.example.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentsdk.PaymentSdk
import com.example.paymentsdk.models.Product
import com.example.paymentsdk.models.PurchaseResult
import com.example.paymentsdk.models.TransactionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CheckoutUiState {
    data object Idle : CheckoutUiState
    data object Loading : CheckoutUiState
    data object Verifying : CheckoutUiState
    data object Success : CheckoutUiState
    data class Error(val message: String) : CheckoutUiState
}

/**
 * ViewModel that drives the checkout Composable.
 *
 * Programs against [PaymentSdk] — the same code works
 * for both StorePaymentSdk and InHousePaymentSdk.
 *
 * Flow:
 * 1. loadProducts()    -> getProducts()
 * 2. onBuyClicked()    -> purchase() -> postReceipt()
 *    (the SDK acknowledges / finishes the transaction
 *    internally inside postReceipt)
 */
class CheckoutViewModel(
    private val paymentSdk: PaymentSdk
) : ViewModel() {

    private val _products =
        MutableStateFlow<List<Product>>(emptyList())
    val products = _products.asStateFlow()

    private val _uiState =
        MutableStateFlow<CheckoutUiState>(CheckoutUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun loadProducts(productIds: List<String>) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading
            _products.value =
                paymentSdk.getProducts(productIds)
            _uiState.value = CheckoutUiState.Idle
        }
    }

    fun onBuyClicked(product: Product) {
        viewModelScope.launch {
            _uiState.value = CheckoutUiState.Loading

            when (val result = paymentSdk.purchase(product)) {

                is PurchaseResult.Success -> {
                    _uiState.value = CheckoutUiState.Verifying

                    val tx = paymentSdk.postReceipt(result)

                    if (tx.status == TransactionStatus.COMPLETED ||
                        tx.status == TransactionStatus.VERIFIED
                    ) {
                        _uiState.value = CheckoutUiState.Success
                    } else {
                        _uiState.value = CheckoutUiState.Error(
                            "Transaction failed"
                        )
                    }
                }

                is PurchaseResult.UserCanceled -> {
                    _uiState.value = CheckoutUiState.Idle
                }

                is PurchaseResult.Error -> {
                    _uiState.value =
                        CheckoutUiState.Error(result.message)
                }
            }
        }
    }
}
