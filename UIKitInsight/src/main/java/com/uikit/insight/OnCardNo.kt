package com.uikit.insight

import java.util.concurrent.LinkedBlockingQueue

open class OnCardNo {
    private sealed interface Signal {
        data class CardNo(val value: String) : Signal
        data object Destroy : Signal
    }

    private val signals = LinkedBlockingQueue<Signal>()

    @Volatile
    private var enrolledEvent: OnCardNo? = null
    @Volatile
    private var destroyed = false
    @Volatile
    private var running = false

    open fun event(str: String) = Unit

    @Synchronized
    fun enroll(event: OnCardNo): OnCardNo {
        check(!destroyed) { "OnCardNo has been destroyed" }
        require(event !== this) { "OnCardNo cannot enroll itself" }
        check(!event.destroyed) { "Cannot enroll a destroyed OnCardNo event" }
        enrolledEvent = event
        return this
    }

    fun run() {
        synchronized(this) {
            check(!destroyed) { "OnCardNo has been destroyed" }
            check(enrolledEvent != null) {
                "OnCardNo.run() requires enroll(event) before listening"
            }
            check(!running) { "OnCardNo.run() is already listening" }
            running = true
        }

        try {
            while (true) {
                val signal = try {
                    signals.take()
                } catch (_: InterruptedException) {
                    if (destroyed) break
                    continue
                }
                if (signal === Signal.Destroy) break
                val callback = synchronized(this) {
                    if (destroyed) return
                    enrolledEvent
                        ?: throw IllegalStateException("OnCardNo event is not enrolled")
                }
                callback.emit((signal as Signal.CardNo).value)
            }
        } finally {
            running = false
        }
    }

    fun destroy() {
        val eventToDestroy = synchronized(this) {
            if (destroyed) return
            destroyed = true
            enrolledEvent.also { enrolledEvent = null }
        }
        signals.clear()
        signals.offer(Signal.Destroy)
        eventToDestroy?.destroy()
    }

    internal fun publish(cardno: String) {
        synchronized(this) {
            check(!destroyed) { "OnCardNo has been destroyed" }
        }
        signals.offer(Signal.CardNo(cardno))
    }

    private fun emit(str: String) {
        synchronized(this) {
            check(!destroyed) { "OnCardNo event has been destroyed" }
        }
        event(str)
    }
}

fun NewCardNoEventListener(): OnCardNo = OnCardNo()
