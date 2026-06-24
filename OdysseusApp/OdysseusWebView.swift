import SwiftUI
import WebKit

struct OdysseusWebView: UIViewRepresentable {
    @ObservedObject var model: WebViewModel

    func makeCoordinator() -> Coordinator { Coordinator(model: model) }

    func makeUIView(context: Context) -> WKWebView {
        let wv = model.webView
        wv.navigationDelegate = context.coordinator
        wv.uiDelegate = context.coordinator
        wv.addObserver(context.coordinator, forKeyPath: #keyPath(WKWebView.estimatedProgress), options: .new, context: nil)
        wv.addObserver(context.coordinator, forKeyPath: #keyPath(WKWebView.title), options: .new, context: nil)
        wv.addObserver(context.coordinator, forKeyPath: #keyPath(WKWebView.canGoBack), options: .new, context: nil)
        wv.addObserver(context.coordinator, forKeyPath: #keyPath(WKWebView.canGoForward), options: .new, context: nil)

        // Pull to refresh
        let refresh = UIRefreshControl()
        refresh.addTarget(context.coordinator, action: #selector(Coordinator.handleRefresh(_:)), for: .valueChanged)
        wv.scrollView.refreshControl = refresh

        // Setup Javascript Bridge
        let contentController = wv.configuration.userContentController
        contentController.add(context.coordinator, name: "odysseusObserver")
        
        let injectionScript = """
            (function() {
                if (window.__iosInjected) return;
                window.__iosInjected = true;
                
                var sendBtn = document.querySelector('.send-btn');
                if (!sendBtn) return;
                
                var isStreaming = false;
                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        if (mutation.attributeName === 'data-mode') {
                            var mode = sendBtn.getAttribute('data-mode');
                            if (mode === 'streaming' && !isStreaming) {
                                isStreaming = true;
                                window.webkit.messageHandlers.odysseusObserver.postMessage({ action: 'startStream' });
                            } else if (!mode && isStreaming) {
                                isStreaming = false;
                                var msgs = document.querySelectorAll('.msg-assistant .body');
                                var lastMsgText = msgs.length > 0 ? msgs[msgs.length - 1].innerText : 'Message received';
                                window.webkit.messageHandlers.odysseusObserver.postMessage({ action: 'endStream', message: lastMsgText });
                            }
                        }
                    });
                });
                observer.observe(sendBtn, { attributes: true });
            })();
        """
        let userScript = WKUserScript(source: injectionScript, injectionTime: .atDocumentEnd, forMainFrameOnly: false)
        contentController.addUserScript(userScript)

        // Setup the reply handler
        NotificationManager.shared.onReplyReceived = { replyText in
            DispatchQueue.main.async {
                let escapedReply = replyText.replacingOccurrences(of: "'", with: "\\'")
                let js = """
                (function() {
                    var input = document.getElementById('message');
                    if (input) {
                        input.value = '\(escapedReply)';
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        var btn = document.querySelector('.send-btn');
                        if (btn) {
                            btn.click();
                        }
                    }
                })();
                """
                wv.evaluateJavaScript(js, completionHandler: nil)
            }
        }

        return wv
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    // MARK: - Coordinator

    class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        let model: WebViewModel

        init(model: WebViewModel) { self.model = model }

        @objc func handleRefresh(_ control: UIRefreshControl) {
            Task { @MainActor in
                model.reload()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { control.endRefreshing() }
            }
        }

        override func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey: Any]?, context: UnsafeMutableRawPointer?) {
            guard let wv = object as? WKWebView else { return }
            Task { @MainActor in
                switch keyPath {
                case #keyPath(WKWebView.estimatedProgress):
                    model.loadProgress = wv.estimatedProgress
                case #keyPath(WKWebView.title):
                    model.pageTitle = wv.title ?? "Odysseus"
                case #keyPath(WKWebView.canGoBack):
                    model.canGoBack = wv.canGoBack
                case #keyPath(WKWebView.canGoForward):
                    model.canGoForward = wv.canGoForward
                default: break
                }
            }
        }

        // MARK: WKScriptMessageHandler
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard message.name == "odysseusObserver",
                  let body = message.body as? [String: Any],
                  let action = body["action"] as? String else { return }

            if action == "startStream" {
                BackgroundTaskManager.shared.startTask()
            } else if action == "endStream" {
                let text = body["message"] as? String ?? "Done"
                BackgroundTaskManager.shared.endTask()
                NotificationManager.shared.showNotification(message: text)
            }
        }

        // MARK: WKNavigationDelegate

        func webView(_ webView: WKWebView, didStartProvisionalNavigation _: WKNavigation!) {
            Task { @MainActor in
                model.isLoading = true
                model.hasError = false
            }
        }

        func webView(_ webView: WKWebView, didFinish _: WKNavigation!) {
            Task { @MainActor in
                model.isLoading = false
                model.attemptAutoLogin()
            }
        }

        func webView(_ webView: WKWebView, didFail _: WKNavigation!, withError error: Error) {
            handleError(error)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation _: WKNavigation!, withError error: Error) {
            handleError(error)
        }

        private func handleError(_ error: Error) {
            let nsError = error as NSError
            guard nsError.code != NSURLErrorCancelled else { return }
            Task { @MainActor in
                model.isLoading = false
                model.hasError = true
                model.errorMessage = error.localizedDescription
            }
        }

        // Allow new window targets (_blank links) to open in same view
        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                     for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if let url = navigationAction.request.url {
                webView.load(URLRequest(url: url))
            }
            return nil
        }

        // Forward file chooser (for document uploads)
        @available(iOS 18.4, *)
        func webView(_ webView: WKWebView, runOpenPanelWith parameters: WKOpenPanelParameters,
                     initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping ([URL]?) -> Void) {
            completionHandler(nil)
        }
    }
}
