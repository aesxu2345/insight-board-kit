package com.uikit.insight

open class Scan2Fail protected constructor() {
    open val value: Int = 0
}

internal class MutableScan2Fail : Scan2Fail() {
    @Volatile
    private var currentValue = 0
    @Volatile
    private var destroyed = false

    override val value: Int
        get() = currentValue

    @Synchronized
    fun fix(value: Int) {
        check(!destroyed) { "scan2fail has been destroyed" }
        require(value >= 0) { "scan2fail must be greater than or equal to 0" }
        currentValue = value
    }

    @Synchronized
    fun destroy() {
        if (destroyed) return
        destroyed = true
        currentValue = 0
    }
}
