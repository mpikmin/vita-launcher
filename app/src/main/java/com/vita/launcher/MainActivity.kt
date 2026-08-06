package com.vita.launcher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var bridge: Bridge
    private var packageWatcher: BroadcastReceiver? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the status and navigation bars so the UI owns the whole panel.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(android.R.color.black)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)

        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true          // the UI keeps its preferences in localStorage
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = false
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                textZoom = 100                    // ignore system font scaling; the UI scales itself
            }
            webChromeClient = WebChromeClient()

            // Assets are served over a real https origin instead of file://, so
            // localStorage has a stable origin and file access can stay disabled.
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this@MainActivity))
                .build()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView, request: android.webkit.WebResourceRequest
                ) = loader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: android.webkit.WebResourceRequest
                ) = true
            }
        }

        bridge = Bridge(this, web)
        web.addJavascriptInterface(bridge, "Android")
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")

        // HOME while already home: let the UI fold back to the first page.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.__vitaBack && window.__vitaBack();", null)
            }
        })

        // Rebuild the grid when apps are installed or removed.
        packageWatcher = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                web.evaluateJavascript("window.__vitaAppsChanged && window.__vitaAppsChanged();", null)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this, packageWatcher, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /** Pressing HOME while the launcher is already showing arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        web.evaluateJavascript("window.__vitaHome && window.__vitaHome();", null)
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        web.evaluateJavascript("window.__vitaResume && window.__vitaResume();", null)
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        packageWatcher?.let { runCatching { unregisterReceiver(it) } }
        web.destroy()
        super.onDestroy()
    }
}
