package it.skyunoplayer.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var root: FrameLayout
    private lateinit var toolbar: LinearLayout
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private val startUrl = "https://thedaddy.li/watch.php?id=881"
    private val prefs by lazy { getSharedPreferences("sky_uno_player", MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 15, 22)
        window.navigationBarColor = Color.BLACK

        root = FrameLayout(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 15, 22))
        }
        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 8, 10, 8)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "SKY UNO PLAYER"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(8, 8, 8, 8)
        }
        val reload = TextView(this).apply {
            text = "↻ Ricarica"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(14, 10, 14, 10)
            setOnClickListener { webView.reload() }
        }
        toolbar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(reload)

        webView = WebView(this).apply { setBackgroundColor(Color.BLACK) }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host?.lowercase() ?: return true
                return !(host == "thedaddy.li" || host.endsWith(".thedaddy.li"))
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url.startsWith("https://thedaddy.li/")) restoreLastPlayer()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean = false

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null) return
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                toolbar.visibility = View.GONE
                webView.visibility = View.GONE
                root.addView(view, FrameLayout.LayoutParams(-1, -1))
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }

            override fun onHideCustomView() {
                exitFullscreen()
            }
        }

        column.addView(toolbar)
        column.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(column)
        setContentView(root)
        webView.loadUrl(startUrl)
    }

    private fun restoreLastPlayer() {
        val last = prefs.getInt("last_player", 0)
        val js = """
            (function() {
              if (window.__skyUnoRestoreInstalled) return;
              window.__skyUnoRestoreInstalled = true;
              function playerNumber(el) {
                if (!el) return 0;
                var s = (el.innerText || el.textContent || '').trim();
                var m = s.match(/^Player\s*([1-7])$/i);
                return m ? parseInt(m[1], 10) : 0;
              }
              document.addEventListener('click', function(e) {
                var el = e.target;
                for (var i = 0; el && i < 5; i++, el = el.parentElement) {
                  var n = playerNumber(el);
                  if (n) {
                    localStorage.setItem('skyUnoLastPlayer', String(n));
                    AndroidPlayerStore.save(n);
                    break;
                  }
                }
              }, true);
              var wanted = $last;
              if (wanted >= 1 && wanted <= 7) {
                var nodes = Array.from(document.querySelectorAll('a,button,[role=button],li,span,div'));
                var found = nodes.find(function(el) { return playerNumber(el) === wanted; });
                if (found) found.click();
              }
            })();
        """.trimIndent()
        webView.addJavascriptInterface(PlayerStoreBridge(), "AndroidPlayerStore")
        webView.evaluateJavascript(js, null)
    }

    inner class PlayerStoreBridge {
        @android.webkit.JavascriptInterface
        fun save(number: Int) {
            if (number in 1..7) prefs.edit().putInt("last_player", number).apply()
        }
    }

    private fun exitFullscreen() {
        val view = customView ?: return
        root.removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        toolbar.visibility = View.VISIBLE
        webView.visibility = View.VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (customView != null) {
            exitFullscreen()
            return
        }
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
