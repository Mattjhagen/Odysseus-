package site.finchwire.odysseus

object AppState {
    var isForeground = false
    var mainWebView: android.webkit.WebView? = null
    var fileChooserCallback: android.webkit.ValueCallback<Array<android.net.Uri>>? = null
}
