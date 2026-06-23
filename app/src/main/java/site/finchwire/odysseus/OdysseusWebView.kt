package site.finchwire.odysseus

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("SetJavaScriptEnabled")
fun WebView.configure(
    onProgressChanged: (Int) -> Unit,
    onPageFinished: (String?) -> Unit
) {
    settings.apply {
        javaScriptEnabled          = true
        domStorageEnabled          = true
        databaseEnabled            = true
        setSupportZoom(false)
        loadWithOverviewMode       = true
        useWideViewPort            = true
        mixedContentMode           = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        cacheMode                  = WebSettings.LOAD_CACHE_ELSE_NETWORK
        mediaPlaybackRequiresUserGesture = false
    }

    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            onProgressChanged(newProgress)
        }
    }

    webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            onPageFinished(url)
        }
        override fun shouldOverrideUrlLoading(
            view: WebView, request: WebResourceRequest
        ): Boolean {
            // Stay in-app for same host; hand off external links
            val host = request.url.host ?: return false
            return !host.contains("finchwire") && !host.contains("192.168")
        }
    }

    setBackgroundColor(android.graphics.Color.BLACK)
}

fun WebView.injectAutoLogin(username: String, password: String) {
    val u = username.jsEscape()
    val p = password.jsEscape()
    val js = """
    javascript:(function(){
        var u=document.querySelector(
            'input[name="username"],input[name="user"],input[name="email"],'+
            'input[type="email"],input[autocomplete="username"],input[autocomplete="email"]');
        var pw=document.querySelector('input[name="password"],input[type="password"]');
        if(!u||!pw) return;
        function set(el,val){
            var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');
            d.set.call(el,val);
            el.dispatchEvent(new Event('input',{bubbles:true}));
            el.dispatchEvent(new Event('change',{bubbles:true}));
        }
        set(u,'$u'); set(pw,'$p');
        var btn=document.querySelector('button[type="submit"],input[type="submit"],button:not([type="button"]):not([type="reset"])');
        if(btn) setTimeout(function(){btn.click();},80);
    })();
    """.trimIndent()
    loadUrl(js)
}

private fun String.jsEscape() = this
    .replace("\\", "\\\\")
    .replace("'", "\\'")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
