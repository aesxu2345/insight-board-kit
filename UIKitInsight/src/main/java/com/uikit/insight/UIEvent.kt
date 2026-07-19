package com.uikit.insight

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

abstract class NewInsight {
    abstract fun OnClickUIEvent(events: UIEventStruct): UIEvent

    open fun OnCardNo(cardno: String) = Unit

    fun onClickUIEvent(events: UIEventStruct): UIEvent = OnClickUIEvent(events)
}

open class UIEventStruct {
    open fun onOpenScanner() = Unit
    open fun onManualBarcodeInput() = Unit
    open fun onConfigureBackendAddress() = Unit
    open fun onCameraInfraredSwitch() = Unit
    open fun onOpenSourceLicenses() = Unit

    @Deprecated("No longer exposed by the sidebar")
    open fun onBreakfastBrief() = Unit
    @Deprecated("No longer exposed by the sidebar")
    open fun onStatusPie() = Unit
    @Deprecated("No longer exposed by the sidebar")
    open fun onPersonnelList() = Unit
    @Deprecated("No longer exposed by the sidebar")
    open fun onScanRegistration() = Unit
    @Deprecated("No longer exposed by the sidebar")
    open fun onSettings() = Unit
}

class UIEvent internal constructor() : NewInsight() {
    @Volatile
    var events: UIEventStruct = UIEventStruct()
        private set

    override fun OnClickUIEvent(events: UIEventStruct): UIEvent {
        this.events = events
        return this
    }

    internal fun dispatch(key: String) {
        when (key) {
            "openScanner" -> events.onOpenScanner()
            "manualBarcodeInput" -> events.onManualBarcodeInput()
            "configureBackendAddress" -> events.onConfigureBackendAddress()
            "cameraInfraredSwitch" -> events.onCameraInfraredSwitch()
            "openSourceLicenses" -> events.onOpenSourceLicenses()
        }
    }

    internal fun clear() {
        events = UIEventStruct()
    }

}

internal class UIEventJavascriptBridge(
    private val uiEvent: UIEvent
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMenuClick(key: String) {
        mainHandler.post { uiEvent.dispatch(key) }
    }
}
