package com.mychatai

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import android.view.Gravity

class ProtonLoginActivity : Activity() {
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 18, 24, 18)
            setBackgroundColor(Color.rgb(24, 24, 24))
        }

        val title = TextView(this).apply {
            text = "تسجيل الدخول إلى Proton"
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val close = TextView(this).apply {
            text = "إغلاق"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(20, 8, 8, 8)
            setOnClickListener { finish() }
        }

        bar.addView(title)
        bar.addView(close)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.loadsImagesAutomatically = true
            settings.userAgentString = settings.userAgentString
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = false
            }

            loadUrl("https://account.proton.me/login")
        }

        root.addView(bar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(web, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)
    }

    override fun onDestroy() {
        web.stopLoading()
        web.destroy()
        super.onDestroy()
    }
}
