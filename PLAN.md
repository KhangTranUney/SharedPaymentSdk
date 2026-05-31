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

    fun handleOpenURL(url: String): Boolean  // iOS only
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
| `PaymentBrowserActivity` | `internal` | `androidMain` | Transparent Activity: launches Custom Tab, detects back press |
| `PaymentRedirectActivity` | `internal` | `androidMain` | Transparent Activity: receives redirect deep link |
| `WebViewResult` | `internal` | `commonMain` | Sealed interface for browser result |
| `CheckoutInfo` | `internal` | `commonMain` | Checkout URL + session from backend |

### 3c. Deep Link Flow

**Android (fully internal — no caller code needed):**

```
purchase()
    → SDK calls POST /api/checkout → gets checkoutUrl
    → SDK launches PaymentBrowserActivity (transparent)
    → PaymentBrowserActivity opens Custom Tab
    → User pays on Stripe/PayPal page
    → Gateway redirects to myapp://payment/callback?...
    → OS launches PaymentRedirectActivity (intent filter)
    → PaymentRedirectActivity parses params, completes deferred, finishes
    → PaymentBrowserActivity.onResume() → finishes
    → purchase() resumes with PurchaseResult
```

**iOS (caller only forwards URLs — no scheme comparison):**

```
purchase()
    → SDK calls POST /api/checkout → gets checkoutUrl
    → SDK presents SFSafariViewController
    → User pays on Stripe/PayPal page
    → Gateway redirects to myapp://payment/callback?...
    → OS delivers URL to .onOpenURL
    → Caller calls sdk.handleOpenURL(url)
    → SDK checks scheme internally, parses params, dismisses Safari
    → purchase() resumes with PurchaseResult
```

### 3d. Host App Setup

**Android** — no code needed. The SDK's `AndroidManifest.xml` declares the internal activities with the intent filter. It is merged automatically.

To customize the callback scheme, set `manifestPlaceholders` in your app's `build.gradle`:

```groovy
android {
    defaultConfig {
        manifestPlaceholders = [paymentCallbackScheme: "myapp"]
    }
}
```

**iOS** — register URL scheme in `Info.plist` + forward URLs:

```xml
<key>CFBundleURLTypes</key>
<array><dict>
    <key>CFBundleURLSchemes</key>
    <array><string>myapp</string></array>
</dict></array>
```

```swift
.onOpenURL { url in
    sdk.handleOpenURL(url: url.absoluteString)
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
    InHousePaymentSdk(this, "your-company-id", "https://api.example.com")
}
// No deep link handling needed — SDK handles it internally

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
let sdk = InHousePaymentSdk(
    context: PlatformContext(),
    clientId: "your-company-id",
    baseUrl: "https://api.example.com"
)
let paymentSdk: PaymentSdk = useNativeStore ? NativePaymentSdk() : sdk

// Forward URLs (no scheme comparison — SDK handles it)
// .onOpenURL { url in sdk.handleOpenURL(url: url.absoluteString) }

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
├── androidMain/
│   ├── AndroidManifest.xml                          # declares internal activities + intent filter
│   └── kotlin/com/example/paymentsdk/
│       ├── PlatformContext.kt                       # actual typealias = Activity
│       └── inhouse/
│           ├── PaymentWebView.kt                    # internal actual (companion deferred)
│           ├── PaymentBrowserActivity.kt            # internal (transparent, launches Custom Tab)
│           └── PaymentRedirectActivity.kt           # internal (transparent, receives redirect)
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
| **Android: transparent Activity pair** | `PaymentBrowserActivity` launches Custom Tab + detects back press. `PaymentRedirectActivity` receives deep link. Caller writes zero deep link code. |
| **iOS: `handleOpenURL()` hides scheme check** | Caller just forwards all URLs — SDK checks the scheme internally. |
| **CustomTabsIntent / SFSafariViewController** | System browser sandbox, visible URL bar, shared cookies. More secure than in-app WebView for payments. |
| **`CompletableDeferred` bridging** | `purchase()` suspends. Deep link completes the deferred (Android: via redirect Activity, iOS: via `handleOpenURL`). |
| **NativePaymentSdk in app layer** | StoreKit 2 is Swift-only, Google Play Billing is Android-only. Cannot be KMP-ified. |
