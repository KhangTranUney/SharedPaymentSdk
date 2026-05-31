package com.example.paymentsdk.inhouse

import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.SafariServices.SFSafariViewController
import platform.SafariServices.SFSafariViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/**
 * iOS actual: presents an SFSafariViewController.
 * Internal — callers interact via [InHousePaymentSdk].
 */
internal actual class PaymentWebView {

    private var pendingResult: CompletableDeferred<WebViewResult>? = null
    private var safariVC: SFSafariViewController? = null

    actual suspend fun open(
        checkoutUrl: String,
        callbackScheme: String
    ): WebViewResult {

        val deferred = CompletableDeferred<WebViewResult>()
        pendingResult = deferred

        val url = NSURL.URLWithString(checkoutUrl)!!
        val safari = SFSafariViewController(uRL = url)

        val delegate = object : NSObject(),
            SFSafariViewControllerDelegateProtocol {

            override fun safariViewControllerDidFinish(
                controller: SFSafariViewController
            ) {
                if (!deferred.isCompleted) {
                    deferred.complete(WebViewResult.Dismissed)
                    pendingResult = null
                }
                safariVC = null
            }
        }

        safari.delegate = delegate
        safariVC = safari

        val rootVC = UIApplication.sharedApplication
            .keyWindow?.rootViewController
        var presenter = rootVC
        while (presenter?.presentedViewController != null) {
            presenter = presenter.presentedViewController
        }
        presenter?.presentViewController(
            safari, animated = true, completion = null
        )

        return deferred.await()
    }

    actual fun handleCallback(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return

        val components = NSURLComponents
            .componentsWithURL(nsUrl, true)
        val params = mutableMapOf<String, String>()
        components?.queryItems?.forEach { item ->
            val qi = item as NSURLQueryItem
            qi.value?.let { params[qi.name] = it }
        }

        safariVC?.dismissViewControllerAnimated(true, null)
        safariVC = null

        pendingResult?.complete(
            WebViewResult.CallbackReceived(params)
        )
        pendingResult = null
    }

    /**
     * No-op on iOS — SFSafariViewController's delegate
     * handles dismissal automatically via
     * safariViewControllerDidFinish.
     */
    actual fun handleUserReturn() {
        // no-op
    }
}
