package com.example.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.app.sdkwrapper.NativePaymentSdk
import com.example.paymentsdk.PaymentSdk
import com.example.paymentsdk.inhouse.InHousePaymentSdk
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var inHouseSdk: InHousePaymentSdk? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val useNativeStore = true // feature flag

        val paymentSdk: PaymentSdk = if (useNativeStore) {
            NativePaymentSdk(this).also { sdk ->
                lifecycleScope.launch {
                    sdk.connect()
                    sdk.syncMissedPurchases()
                }
            }
        } else {
            InHousePaymentSdk(
                context = this,
                clientId = "your-company-id",
                baseUrl = "https://api.example.com"
            ).also { inHouseSdk = it }
        }

        setContent {
            CheckoutScreen(paymentSdk = paymentSdk)
        }
    }

    // Forward deep link callback from Custom Tab
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            inHouseSdk?.handlePaymentCallback(uri.toString())
        }
    }
}
