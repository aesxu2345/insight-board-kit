package com.uikit.insight

import android.util.Log

data class UIInsightCss(
    val assetEntry: String = "uikit_insight/index.html",
    val collapsedCardRadiusDp: Float = 18f,
    val expandedHeaderMaxDp: Float = 156f,
    val expandedHeaderMinDp: Float = 88f,
    val backgroundColor: String = "#f7faf7",
    val primaryColor: String = "#18813b"
) {
    fun New(config: UIInsightPlayConfig): NewUIInsightPlay {
        val routeErrors = mutableListOf<String>()
        val firstRoute = verifiedRouteOrEmpty(
            fieldName = "firstRoute",
            userMessage = "第一幕数据路由无效，已显示 N/A",
            errors = routeErrors
        ) {
            SignedRouteVerifier.requireProviderRoute(
                config.firstRoute,
                "firstRoute",
                bypassSignature = config.bypass
            )
        }
        val secondRoute = verifiedRouteOrEmpty(
            fieldName = "secondRoute",
            userMessage = "第二幕页面路由无效，已停止加载",
            errors = routeErrors
        ) {
            SignedRouteVerifier.requireMainRoute(
                config.secondRoute,
                "secondRoute",
                bypassSignature = config.bypass
            )
        }
        return NewUIInsightPlay(
            ip = config.ip,
            firstRoute = firstRoute,
            secondRoute = secondRoute,
            bypass = config.bypass,
            routeErrorMessage = routeErrors.joinToString("\n").ifBlank { null },
            css = this
        )
    }

    private fun verifiedRouteOrEmpty(
        fieldName: String,
        userMessage: String,
        errors: MutableList<String>,
        verify: () -> String
    ): String {
        return try {
            verify()
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "$fieldName rejected: ${error.message}")
            errors += userMessage
            ""
        }
    }

    private companion object {
        const val TAG = "UIKitInsight"
    }
}
