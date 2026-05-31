# Payment SDK — Architecture Plan

A Kotlin Multiplatform payment SDK with two tracks: **native store billing** (Google Play / Apple StoreKit) and **in-house payment gateway** (Stripe / PayPal via system browser). Both tracks implement a single `PaymentSdk` interface.

---

## Architecture Overview

```
                        ┌─────────────────┐
                        │   PaymentSdk    │   ← commonMain interface
                        │   (interface)    │
                        └────────┬────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │
        ┌────────┴────────┐             ┌────────┴────────┐
        │ NativePaymentSdk│             │InHousePaymentSdk│
        │  (per-platform  │             │  (fully in KMP  │
        │   app wrapper)  │             │   shared module) │
        └────────┬────────┘             └────────┬────────┘
                 │                               │
        ┌────────┴────────┐             ┌────────┴────────┐
        │ Google Play     │             │ internal:       │
        │ Billing (Android)│             │  PaymentApiClient│
        │ StoreKit 2 (iOS)│             │  PaymentWebView │
        └─────────────────┘             │  (CustomTabs /  │
                                        │   SFSafariVC)   │
                                        └─────────────────┘
```

| Aspect | NativePaymentSdk | InHousePaymentSdk |
|--------|-----------------|-------------------|
| **Lives in** | App layer (`sdkwrapper/`) | KMP shared module |
| **Checkout UI** | OS payment sheet | CustomTabsIntent / SFSafariViewController |
| **Internals hidden?** | N/A (platform SDK) | Yes — `PaymentApiClient` and `PaymentWebView` are `internal` |
| **Constructor** | `NativePaymentSdk(activity)` | `InHousePaymentSdk(context, clientId, baseUrl)` — single class in `commonMain` |

**Caller flow is identical for both tracks:**

```
1. val products = sdk.getProducts(ids)        // render your own UI
2. val result   = sdk.purchase(product)       // SDK shows OS sheet or browser
3. val tx       = sdk.getTransactionResult(id)
4.                sdk.finishTransaction(id)
```

---

## 1. Shared Models (`commonMain`)

> `shared/src/commonMain/kotlin/com/example/paymentsdk/models/`

```kotlin
data class Product(
    val productId: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val price: Double,
    val currencyCode: String,
    val discount: Discount? = null,
    val metadata: Map<String, String> = emptyMap()
)

data class Discount(
    val originalPrice: Double,
    val formattedOriginalPrice: String,
    val percentage: Int
)

sealed interface PurchaseResult {
    data class Success(val transactionId: String) : PurchaseResult
    data object UserCanceled : PurchaseResult
    data class Error(val code: Int, val message: String) : PurchaseResult
}

data class Transaction(
    val transactionId: String,
    val productId: String,
    val receiptToken: String,
    val status: TransactionStatus,
    val purchasedAt: Long
)

enum class TransactionStatus {
    PENDING, COMPLETED, VERIFIED, FAILED
}
```

---

## 2. PaymentSdk Interface (`commonMain`)

> `shared/src/commonMain/kotlin/com/example/paymentsdk/PaymentSdk.kt`

```kotlin
interface PaymentSdk {
    suspend fun getProducts(productIds: List<String>): List<Product>
    suspend fun purchase(product: Product): PurchaseResult
    suspend fun getTransactionResult(transactionId: String): Transaction
    suspend fun finishTransaction(transactionId: String)
}
```

---

## 3. InHousePaymentSdk (KMP shared module)

Fully in KMP. Single class in `commonMain` — no `expect/actual` needed for `InHousePaymentSdk` itself. Platform differences are handled by `expect/actual PlatformContext` and `expect/actual PaymentWebView` (both internal).

### 3a. Public API

```kotlin
// commonMain — single class, not expect/actual
class InHousePaymentSdk(
    context: PlatformContext,     // Android = Activity, iOS = empty class
    clientId: String,
    baseUrl: String,
    callbackScheme: String = "myapp"
) : PaymentSdk {

    fun handlePaymentCallback(url: String): Boolean  // forward URLs (SDK checks scheme)
    fun handleUserReturn()                           // Android: call from onResume()
}

// PlatformContext — the only expect/actual the caller sees
expect class PlatformContext
// androidMain: actual typealias PlatformContext = Activity
// iosMain:     actual class PlatformContext
```

### 3b. Internal Components (hidden from caller)

| Component | Visibility | Location | Role |
|-----------|-----------|----------|------|
| `PaymentApiClient` | `internal` | `commonMain` | Ktor HTTP client, sends `X-Client-Id` header |
| `PaymentWebView` | `internal` | `expect/actual` | Opens system browser, takes `PlatformContext` |
| `WebViewResult` | `internal` | `commonMain` | Sealed interface for browser result |
| `CheckoutInfo` | `internal` | `commonMain` | Checkout URL + session from backend |

### 3c. Deep Link Flow

```
purchase() called
    → SDK calls POST /api/checkout → gets checkoutUrl
    → SDK opens CustomTabsIntent / SFSafariViewController
    → User pays on Stripe/PayPal page
    → Gateway redirects to myapp://payment/callback?status=success&transaction_id=123
    → OS routes deep link to app
    → Host app calls sdk.handlePaymentCallback(url) — SDK checks scheme
    → SDK parses params, resumes purchase()
    → Returns PurchaseResult.Success(transactionId)
```

### 3d. Host App Setup

**Android** — `AndroidManifest.xml` intent filter + `onNewIntent`/`onResume`:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="myapp" android:host="payment" android:pathPrefix="/callback" />
</intent-filter>
```

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.data?.let { sdk.handlePaymentCallback(it.toString()) }
}

override fun onResume() {
    super.onResume()
    lifecycleScope.launch { delay(500); sdk.handleUserReturn() }
}
```

**iOS** — `Info.plist` URL scheme + `.onOpenURL`:

```xml
<key>CFBundleURLTypes</key>
<array><dict>
    <key>CFBundleURLSchemes</key>
    <array><string>myapp</string></array>
</dict></array>
```

```swift
.onOpenURL { url in
    sdk.handlePaymentCallback(url: url.absoluteString)
}
```

### 3e. Backend API Contract

```
GET  /api/products?ids=prod_1,prod_2         → [Product]
POST /api/checkout  {productId, returnUrl}   → {checkoutUrl, sessionId, expiresAt}
GET  /api/transactions/{id}                  → Transaction
POST /api/transactions/{id}/fulfill          → {ok}
```

All requests include `X-Client-Id` header (set automatically by the SDK from the `clientId` parameter).

---

## 4. NativePaymentSdk (app layer)

Lives in each platform's app module because the store SDKs cannot be abstracted via KMP:
- **Android:** Google Play Billing Library is Android-only
- **iOS:** StoreKit 2 is Swift-only (no Kotlin/Native interop)

Both implement `PaymentSdk`. See prototype code for full implementation.

---

## 5. Host App Integration

The ViewModel programs against `PaymentSdk` — the same code works for both tracks.

**Android:**

```kotlin
// Choose track
val paymentSdk: PaymentSdk = if (useNativeStore) {
    NativePaymentSdk(this)
} else {
    InHousePaymentSdk(
        context = this,
        clientId = "your-company-id",
        baseUrl = "https://api.example.com"
    )
}

// ViewModel — identical for both tracks
class CheckoutViewModel(private val sdk: PaymentSdk) : ViewModel() {
    fun onBuyClicked(product: Product) {
        viewModelScope.launch {
            when (val result = sdk.purchase(product)) {
                is PurchaseResult.Success -> {
                    val tx = sdk.getTransactionResult(result.transactionId)
                    sdk.finishTransaction(result.transactionId)
                }
                is PurchaseResult.UserCanceled -> { /* idle */ }
                is PurchaseResult.Error -> { /* show error */ }
            }
        }
    }
}
```

**iOS:**

```swift
// Choose track
let paymentSdk: PaymentSdk = useNativeStore
    ? NativePaymentSdk()
    : InHousePaymentSdk(
          context: PlatformContext(),
          clientId: "your-company-id",
          baseUrl: "https://api.example.com"
      )

// ViewModel — identical for both tracks
func onBuyClicked(product: Product) {
    Task {
        let result = try await paymentSdk.purchase(product: product)
        switch result {
        case let success as PurchaseResult.Success:
            let tx = try await paymentSdk.getTransactionResult(transactionId: success.transactionId)
            try await paymentSdk.finishTransaction(transactionId: success.transactionId)
        case is PurchaseResult.UserCanceled: break
        case let error as PurchaseResult.Error: /* show error */
        }
    }
}
```

---

## 6. Project Structure

```
shared/src/
├── commonMain/kotlin/com/example/paymentsdk/
│   ├── PaymentSdk.kt                                # public interface
│   ├── PlatformContext.kt                           # expect class
│   ├── models/
│   │   ├── Product.kt                               # public
│   │   ├── PurchaseResult.kt                        # public sealed interface
│   │   ├── Transaction.kt                           # public
│   │   └── CheckoutInfo.kt                          # internal
│   └── inhouse/
│       ├── InHousePaymentSdk.kt                     # public class
│       ├── PaymentApiClient.kt                      # internal
│       └── PaymentWebView.kt                        # internal expect
│
├── androidMain/kotlin/com/example/paymentsdk/
│   ├── PlatformContext.kt                           # actual typealias = Activity
│   └── inhouse/
│       └── PaymentWebView.kt                        # internal actual (CustomTabsIntent)
│
└── iosMain/kotlin/com/example/paymentsdk/
    ├── PlatformContext.kt                           # actual class (empty)
    └── inhouse/
        └── PaymentWebView.kt                        # internal actual (SFSafariViewController)

androidApp/.../app/
├── sdkwrapper/NativePaymentSdk.kt                   # Google Play Billing wrapper
├── MainActivity.kt                                  # picks track + deep link forwarding
├── CheckoutViewModel.kt                             # uses PaymentSdk interface
└── CheckoutScreen.kt                                # Composable

iosApp/
├── SdkWrapper/NativePaymentSdk.swift                # StoreKit 2 wrapper
├── PaymentApp.swift                                 # picks track + .onOpenURL forwarding
├── CheckoutViewModel.swift                          # uses PaymentSdk protocol
└── CheckoutView.swift                               # SwiftUI
```

---

## 7. Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **`sealed interface PurchaseResult`** | More flexible than sealed class. Uses `data object` for singletons. |
| **`PaymentApiClient` + `PaymentWebView` are `internal`** | Caller only sees `InHousePaymentSdk(clientId, baseUrl)`. No leaking of HTTP client or browser details. |
| **Single class + `PlatformContext`** | `InHousePaymentSdk` is one class in `commonMain` (not expect/actual). `expect class PlatformContext` abstracts the Android `Activity` dependency. All business logic in one place. |
| **`handlePaymentCallback()` checks scheme internally** | Caller forwards all URLs blindly. SDK matches against `callbackScheme` and returns `Boolean`. |
| **CustomTabsIntent / SFSafariViewController** | System browser sandbox, visible URL bar, shared cookies. More secure than in-app WebView for payments. |
| **`CompletableDeferred` bridging** | `purchase()` suspends. Deep link arrives asynchronously via `handlePaymentCallback()`. `CompletableDeferred` bridges the two. |
| **NativePaymentSdk in app layer** | StoreKit 2 is Swift-only, Google Play Billing is Android-only. Cannot be KMP-ified. |
