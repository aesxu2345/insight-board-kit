package com.uikit.insight

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebRequestError
import java.net.HttpURLConnection
import java.net.URL

typealias UIInsightCreator = (NewUIInsightPlay) -> WebView

class NewUIInsightPlay internal constructor(
    val ip: String,
    val firstRoute: String,
    val secondRoute: String,
    val bypass: Boolean,
    private val routeErrorMessage: String?,
    val css: UIInsightCss
) : NewInsight() {
    val uiEvent: UIEvent = UIEvent()

    var creator: () -> Unit = {}
        private set

    private var webView: WebView? = null
    private var secondRouteBrowser: GeckoSecondRouteController? = null
    private var routeErrorShown = false
    private var lowPerformanceDevice = false

    fun Display(creator: UIInsightCreator): WebView {
        releaseWebView()
        val next = creator(this)
        configureWebView(next)
        lowPerformanceDevice = next.context.isLowPerformanceDevice()
        next.webViewClient = WebViewClient()
        val browser = GeckoSecondRouteController(
            host = next,
            secondRoute = secondRoute,
            onCardNo = ::OnCardNo,
            onRequestFirstScene = {
                next.post {
                    if (webView !== next) return@post
                    next.evaluateJavascript(
                        "window.UIKitInsightShowFirstScene && " +
                            "window.UIKitInsightShowFirstScene();",
                        null
                    )
                }
            },
            onSecondRouteLoaded = {
                next.post {
                    if (webView !== next) return@post
                    next.evaluateJavascript(
                        "window.UIKitInsightMarkSecondRouteLoaded && " +
                            "window.UIKitInsightMarkSecondRouteLoaded();",
                        null
                    )
                }
            }
        )
        secondRouteBrowser = browser
        next.addJavascriptInterface(UIEventJavascriptBridge(uiEvent), UI_EVENT_BRIDGE_NAME)
        next.addJavascriptInterface(
            SecondRouteJavascriptBridge(browser),
            SECOND_ROUTE_BRIDGE_NAME
        )
        next.addJavascriptInterface(
            RouteJsonJavascriptBridge(next.context.applicationContext, firstRoute) { json ->
                next.post {
                    if (webView !== next) return@post
                    val quotedJson = JSONObject.quote(json)
                    next.evaluateJavascript(
                        "window.UIKitInsightReceiveFirstRouteJson && " +
                            "window.UIKitInsightReceiveFirstRouteJson($quotedJson);",
                        null
                    )
                }
            },
            ROUTE_JSON_BRIDGE_NAME
        )
        showRouteErrorOnce(next.context)
        this.webView = next
        this.creator = {
            next.stopLoading()
            next.loadUrl(assetUrl())
        }
        this.creator.invoke()
        return next
    }

    fun Display(activity: Activity): WebView = Display(activity as Context, null)

    override fun OnClickUIEvent(events: UIEventStruct): UIEvent {
        return uiEvent.OnClickUIEvent(events)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun Display(context: Context, container: ViewGroup? = null): WebView {
        val hostActivity = context.findActivity()
        val useLowPerformanceMode = context.isLowPerformanceDevice()
        val browserContainer = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val next = Display {
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = false
                settings.textZoom = 100
                isHorizontalScrollBarEnabled = false
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                setLayerType(
                    if (useLowPerformanceMode) View.LAYER_TYPE_NONE else View.LAYER_TYPE_HARDWARE,
                    null
                )
                setBackgroundColor(
                    runCatching { Color.parseColor(css.backgroundColor) }
                        .getOrDefault(Color.TRANSPARENT)
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setRendererPriorityPolicy(
                        if (useLowPerformanceMode) WebView.RENDERER_PRIORITY_WAIVED
                        else WebView.RENDERER_PRIORITY_BOUND,
                        !useLowPerformanceMode
                    )
                }
                visibility = View.VISIBLE
            }
        }
        browserContainer.addView(next)
        if (container != null) {
            container.apply {
                removeAllViews()
                addView(browserContainer)
            }
        } else if (hostActivity != null) {
            hostActivity.setContentView(browserContainer)
        } else {
            error("Display requires an Activity context or a non-null container")
        }
        return next
    }

    fun Destory() {
        creator = { releaseWebView(clearEvents = true) }
        creator.invoke()
    }

    fun Destroy() = Destory()

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(target: WebView) {
        target.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = true
            allowContentAccess = true
            loadsImagesAutomatically = true
            blockNetworkImage = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(target, true)
        }
    }

    fun assetUrl(): String {
        return Uri.Builder()
            .scheme("file")
            .encodedPath("/android_asset/${css.assetEntry.trimStart('/')}")
            .appendQueryParameter("ip", ip)
            .appendQueryParameter("firstRoute", firstRoute)
            .appendQueryParameter("secondRoute", secondRoute)
            .appendQueryParameter("lowPerformance", if (lowPerformanceDevice) "1" else "0")
            .build()
            .toString()
    }

    private fun releaseWebView(clearEvents: Boolean = false) {
        secondRouteBrowser?.destroy()
        secondRouteBrowser = null
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.removeJavascriptInterface(UI_EVENT_BRIDGE_NAME)
            it.removeJavascriptInterface(ROUTE_JSON_BRIDGE_NAME)
            it.removeJavascriptInterface(SECOND_ROUTE_BRIDGE_NAME)
            it.stopLoading()
            it.loadUrl("about:blank")
            it.clearHistory()
            it.removeAllViews()
            it.destroy()
        }
        webView = null
        if (clearEvents) uiEvent.clear()
    }

    private fun showRouteErrorOnce(context: Context) {
        val message = routeErrorMessage ?: return
        if (routeErrorShown) return
        routeErrorShown = true
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private fun Context.isLowPerformanceDevice(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also {
            activityManager?.getMemoryInfo(it)
        }
        val lowRamDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activityManager?.isLowRamDevice == true
        } else {
            false
        }
        val constrainedPhysicalMemory =
            memoryInfo.totalMem in 1..(4L * 1024L * 1024L * 1024L)
        val constrainedHeap = (activityManager?.memoryClass ?: Int.MAX_VALUE) <= 192
        val constrainedCpu = Runtime.getRuntime().availableProcessors() <= 4
        return lowRamDevice || constrainedPhysicalMemory || constrainedHeap || constrainedCpu
    }

    private companion object {
        const val UI_EVENT_BRIDGE_NAME = "UIKitInsightUIEvent"
        const val ROUTE_JSON_BRIDGE_NAME = "UIKitInsightRouteJson"
        const val SECOND_ROUTE_BRIDGE_NAME = "UIKitInsightBrowser"
    }
}

internal class SecondRouteJavascriptBridge(
    private val browser: GeckoSecondRouteController
) {
    @JavascriptInterface
    fun prepare() = browser.prepare()

    @JavascriptInterface
    fun setVisible(visible: Boolean) = browser.setVisible(visible)

    @JavascriptInterface
    fun retry() = browser.retry()

    @JavascriptInterface
    fun showNA() = browser.showNA()
}

internal class GeckoSecondRouteController(
    private val host: WebView,
    private val secondRoute: String,
    private val onCardNo: (String) -> Unit,
    private val onRequestFirstScene: () -> Unit,
    private val onSecondRouteLoaded: () -> Unit
) {
    private val expectedRoute = Uri.parse(secondRoute)
    private val view = object : GeckoView(host.context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            return if (handleBrowserTouch(event)) true else super.dispatchTouchEvent(event)
        }
    }
    private var session: GeckoSession? = null
    private var destroyed = false
    private var prepared = false
    private var displayRequested = false
    private var loadInFlight = false
    private var targetNavigationStarted = false
    private var pageStoppedSuccessfully = false
    private var firstCompositeReceived = false
    private var contentReady = false
    private var loadGeneration = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var touchStartY = 0f
    private var collapseGestureTriggered = false

    init {
        view.visibility = View.INVISIBLE
        view.isEnabled = false
        view.setBackgroundColor(BROWSER_COVER_COLOR)
    }

    private fun ensureSession(): GeckoSession {
        session?.let { return it }
        val next = GeckoSession()
        next.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onFirstComposite(session: GeckoSession) {
                if (!loadInFlight || !targetNavigationStarted) return
                firstCompositeReceived = true
                completeSuccessfulLoadIfReady()
            }

            override fun onPaintStatusReset(session: GeckoSession) {
                if (!loadInFlight || !targetNavigationStarted) return
                firstCompositeReceived = false
                contentReady = false
                updateVisibility()
            }
        }
        next.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val uri = Uri.parse(request.uri)
                if (!isVirtualCardRoute(uri)) return null
                onCardNo(uri.pathSegments[2])
                return GeckoResult.deny()
            }

            override fun onLoadError(
                session: GeckoSession,
                uri: String?,
                error: WebRequestError
            ): GeckoResult<String>? {
                keepBrowserAvailable()
                return null
            }
        }
        next.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (!loadInFlight || url == ABOUT_BLANK) return
                targetNavigationStarted = true
                pageStoppedSuccessfully = false
                firstCompositeReceived = false
                contentReady = false
                updateVisibility()
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (!loadInFlight || !targetNavigationStarted) return
                if (!success) {
                    keepBrowserAvailable()
                    return
                }
                pageStoppedSuccessfully = true
                completeSuccessfulLoadIfReady()
            }
        }
        next.open(runtime(host.context.applicationContext))
        view.setSession(next)
        session = next
        return next
    }

    fun prepare() = host.post {
        if (destroyed || prepared || secondRoute.isBlank()) return@post
        prepared = true
        if (displayRequested) loadRoute()
    }

    fun retry() = host.post {
        if (destroyed || secondRoute.isBlank()) return@post
        prepared = true
        displayRequested = true
        loadRoute()
    }

    fun setVisible(visible: Boolean) = host.post {
        if (destroyed) return@post
        displayRequested = visible
        if (visible && prepared && session == null && !loadInFlight && !contentReady) {
            loadRoute()
        } else {
            updateVisibility()
        }
    }

    fun showNA() = host.post {
        if (destroyed) return@post
        updateVisibility()
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        loadGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        session?.let {
            view.releaseSession()
            it.close()
        }
        session = null
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun loadRoute() {
        loadGeneration++
        loadInFlight = true
        targetNavigationStarted = false
        pageStoppedSuccessfully = false
        firstCompositeReceived = false
        contentReady = false
        attachView()
        updateVisibility()
        ensureSession().loadUri(secondRoute)
    }

    private fun completeSuccessfulLoadIfReady() {
        if (!loadInFlight || !targetNavigationStarted ||
            !pageStoppedSuccessfully || !firstCompositeReceived
        ) return
        finishSuccessfulLoad()
    }

    private fun finishSuccessfulLoad() {
        loadInFlight = false
        contentReady = true
        loadGeneration++
        updateVisibility()
        onSecondRouteLoaded()
    }

    private fun attachView() {
        if (view.parent != null) return
        val parent = host.parent as? ViewGroup ?: return
        val topMargin = (COMPACT_HEADER_DP * host.resources.displayMetrics.density).toInt()
        val layoutParams = if (parent is FrameLayout) {
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { this.topMargin = topMargin }
        } else {
            ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { this.topMargin = topMargin }
        }
        parent.addView(
            view,
            layoutParams
        )
    }

    private fun handleBrowserTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartY = event.rawY
                collapseGestureTriggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                val threshold = COLLAPSE_GESTURE_DP * host.resources.displayMetrics.density
                if (!collapseGestureTriggered && event.rawY - touchStartY >= threshold) {
                    collapseGestureTriggered = true
                    displayRequested = false
                    updateVisibility()
                    onRequestFirstScene()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val consumed = collapseGestureTriggered
                collapseGestureTriggered = false
                return consumed
            }
        }
        return collapseGestureTriggered
    }

    private fun keepBrowserAvailable() {
        if (destroyed) return
        loadInFlight = false
        contentReady = true
        loadGeneration++
        updateVisibility()
        onSecondRouteLoaded()
    }

    private fun updateVisibility() {
        val shouldAttach = displayRequested
        view.visibility = if (shouldAttach) View.VISIBLE else View.INVISIBLE
        view.isEnabled = shouldAttach
        if (shouldAttach) view.bringToFront()
    }

    private fun isVirtualCardRoute(uri: Uri): Boolean {
        if (!sameOrigin(expectedRoute, uri)) return false
        val segments = uri.pathSegments
        return segments.size == 3 &&
            segments[0] == "invalid" &&
            segments[1] == "exam" &&
            segments[2].isNotBlank()
    }

    private fun sameOrigin(first: Uri, second: Uri): Boolean {
        return first.scheme.equals(second.scheme, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) &&
            first.port == second.port
    }

    private companion object {
        const val ABOUT_BLANK = "about:blank"
        val BROWSER_COVER_COLOR = Color.rgb(247, 250, 247)
        const val COLLAPSE_GESTURE_DP = 64f
        const val COMPACT_HEADER_DP = 28f

        @Volatile
        private var sharedRuntime: GeckoRuntime? = null

        fun runtime(context: Context): GeckoRuntime {
            return sharedRuntime ?: synchronized(this) {
                sharedRuntime ?: GeckoRuntime.create(
                    context,
                    lowMemoryRuntimeSettings()
                ).also { sharedRuntime = it }
            }
        }

        private fun lowMemoryRuntimeSettings(): GeckoRuntimeSettings {
            val settings = GeckoRuntimeSettings.Builder()
                .fissionEnabled(false)
                .lowMemoryDetection(true)
                .build()
            settings.setWebContentIsolationStrategy(
                GeckoRuntimeSettings.STRATEGY_ISOLATE_NOTHING
            )
            runCatching {
                GeckoRuntimeSettings::class.java
                    .getDeclaredMethod("setProcessCount", Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                    .invoke(settings, 1)
            }
            return settings
        }
    }
}

internal class RouteJsonJavascriptBridge(
    private val context: Context,
    private val firstRoute: String,
    private val onLoaded: (String) -> Unit
) {
    @Volatile
    private var requestStarted = false
    private var consecutiveFailures = 0
    private var healthPromptShown = false

    @JavascriptInterface
    fun loadFirstRouteJson() {
        synchronized(this) {
            if (requestStarted) return
            requestStarted = true
        }
        Thread({
            var routeReachable = false
            val json = runCatching {
                val connection = (URL(firstRoute).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    useCaches = false
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (connection.responseCode !in 200..299) return@runCatching ""
                    routeReachable = true
                    connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }.getOrDefault("")
            val showHealthPrompt = synchronized(this) {
                requestStarted = false
                if (healthPromptShown) {
                    false
                } else if (routeReachable) {
                    consecutiveFailures = 0
                    false
                } else {
                    consecutiveFailures += 1
                    if (consecutiveFailures >= MAX_HEALTH_FAILURES) {
                        healthPromptShown = true
                        true
                    } else {
                        false
                    }
                }
            }
            if (showHealthPrompt) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "第一路由连续 10 次无法访问，请检查网络或服务地址",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            onLoaded(json)
        }, "UIKitInsight-first-route").start()
    }

    private companion object {
        const val MAX_HEALTH_FAILURES = 10
    }
}
