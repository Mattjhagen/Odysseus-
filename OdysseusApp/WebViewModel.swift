import Foundation
import WebKit
import Combine

@MainActor
final class WebViewModel: ObservableObject {
    @Published var isLoading = true
    @Published var loadProgress: Double = 0
    @Published var pageTitle: String = "Odysseus"
    @Published var canGoBack = false
    @Published var canGoForward = false
    @Published var hasError = false
    @Published var errorMessage = ""

    let webView: WKWebView

    init() {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []

        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true
        config.defaultWebpagePreferences = prefs

        webView = WKWebView(frame: .zero, configuration: config)
        webView.allowsBackForwardNavigationGestures = true
        webView.scrollView.contentInsetAdjustmentBehavior = .automatic
        webView.isOpaque = false
        webView.backgroundColor = .black
        webView.scrollView.backgroundColor = .black
    }

    func load(_ url: URL) {
        webView.load(URLRequest(url: url, cachePolicy: .returnCacheDataElseLoad, timeoutInterval: 15))
    }

    func reload() { webView.reload() }
    func goBack() { webView.goBack() }
    func goForward() { webView.goForward() }

    // Called after each page load to auto-fill credentials if we land on the login page
    func attemptAutoLogin() {
        guard let creds = KeychainService.load() else { return }
        let u = creds.username.jsEscaped
        let p = creds.password.jsEscaped
        let js = """
        (function() {
            var userField = document.querySelector(
                'input[name="username"], input[name="user"], input[name="email"], ' +
                'input[type="email"], input[autocomplete="username"], input[autocomplete="email"]'
            );
            var passField = document.querySelector(
                'input[name="password"], input[type="password"]'
            );
            if (!userField || !passField) return false;
            function setNative(el, val) {
                var nativeSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
                nativeSetter.call(el, val);
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
            }
            setNative(userField, '\(u)');
            setNative(passField, '\(p)');
            var submitBtn = document.querySelector(
                'button[type="submit"], input[type="submit"], ' +
                'button:not([type="button"]):not([type="reset"])'
            );
            if (submitBtn) {
                setTimeout(function() { submitBtn.click(); }, 80);
            }
            return true;
        })();
        """
        webView.evaluateJavaScript(js, completionHandler: nil)
    }
}

private extension String {
    var jsEscaped: String {
        self
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'",  with: "\\'")
            .replacingOccurrences(of: "\n", with: "\\n")
            .replacingOccurrences(of: "\r", with: "\\r")
    }
}
