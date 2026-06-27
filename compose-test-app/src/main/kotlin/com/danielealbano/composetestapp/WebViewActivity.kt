package com.danielealbano.composetestapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity

/**
 * Minimal WebView activity for E2E testing of accessibility tree freshness
 * with non-Compose virtual accessibility nodes.
 *
 * Displays a simple HTML page with "Number: 0" that can be updated via intent:
 *   adb shell am start --activity-single-top \
 *     -n com.danielealbano.composetestapp/.WebViewActivity --ei number 42
 *
 * The number is updated via evaluateJavascript, which modifies the DOM without
 * reloading the page. WebView virtual accessibility nodes may return stale data
 * if not refreshed during tree traversal.
 *
 * When launched with the string extra `content=heavy`, it instead loads a large,
 * content-heavy page (hundreds of articles with headings, paragraphs, links, list
 * items and images) used to exercise WebView node collapsing in E2E tests.
 */
class WebViewActivity : ComponentActivity() {

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val heavy = intent?.getStringExtra(EXTRA_CONTENT) == CONTENT_HEAVY
        Log.i(TAG, "onCreate called, heavy=$heavy")

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            if (heavy) {
                loadDataWithBaseURL(null, buildHeavyHtml(), "text/html", "UTF-8", null)
            } else {
                loadData(
                    """
                    <html>
                    <body style="display:flex;justify-content:center;align-items:center;height:100vh;margin:0;">
                        <span id="counter" style="font-size:48px;">Number: 0</span>
                    </body>
                    </html>
                    """.trimIndent(),
                    "text/html",
                    "UTF-8",
                )
            }
        }
        setContentView(webView)

        if (!heavy) {
            handleNumberIntent(intent)
        }
    }

    /**
     * Builds a large HTML document with [ARTICLE_COUNT] articles, each containing a heading,
     * two paragraphs, a link, an inline image and a two-item list. This produces well over a
     * thousand WebView accessibility nodes, exercising the node-collapsing path.
     */
    private fun buildHeavyHtml(): String {
        val body = StringBuilder()
        body.append("<h1>Daily Digest</h1>")
        for (i in 1..ARTICLE_COUNT) {
            body.append("<article>")
            body.append("<h2>Story number $i headline</h2>")
            body.append("<p>First paragraph describing the details of story number $i in full.</p>")
            body.append("<p>Second paragraph with additional context for story number $i.</p>")
            body.append("<img src=\"$PIXEL_DATA_URI\" alt=\"Illustration for story $i\" width=\"32\" height=\"32\">")
            body.append("<a href=\"https://example.invalid/story/$i\">Read full story number $i</a>")
            body.append("<ul><li>Highlight A of story $i</li><li>Highlight B of story $i</li></ul>")
            body.append("</article>")
        }
        return "<html><body>$body</body></html>"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG, "onNewIntent called, extras=${intent.extras}")
        handleNumberIntent(intent)
    }

    private fun handleNumberIntent(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_NUMBER) == true) {
            val newNumber = intent.getIntExtra(EXTRA_NUMBER, 0)
            Log.i(TAG, "handleNumberIntent: updating WebView to number=$newNumber")
            webView?.evaluateJavascript(
                "document.getElementById('counter').textContent = 'Number: $newNumber';",
            ) { result ->
                Log.i(TAG, "handleNumberIntent: evaluateJavascript result=$result")
            }
        } else {
            Log.i(TAG, "handleNumberIntent: no '$EXTRA_NUMBER' extra in intent")
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WebViewTestApp"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_CONTENT = "content"
        private const val CONTENT_HEAVY = "heavy"
        private const val ARTICLE_COUNT = 200

        /** 1x1 transparent PNG so each article has a real <img> accessibility node, offline. */
        private const val PIXEL_DATA_URI =
            "data:image/png;base64," +
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    }
}
