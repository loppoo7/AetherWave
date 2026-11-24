package com.aetherwave.core.island

import android.view.Choreographer

/**
 * A short eased animation driven by the display's frame clock. It does not use the system animator
 * scale, so it still plays when animations are turned off in developer options.
 *
 * [onFrame] receives a value from 0 to 1 that eases in and out.
 */
class FrameTween(
    private val durationMs: Long,
    private val onEnd: () -> Unit = {},
    private val onFrame: (Float) -> Unit,
) {
    private val choreographer = Choreographer.getInstance()
    private var startNs = 0L
    private var running = false

    private val callback = Choreographer.FrameCallback { frameNs ->
        if (!running) return@FrameCallback
        if (startNs == 0L) startNs = frameNs
        val linear = ((frameNs - startNs) / 1_000_000f / durationMs).coerceIn(0f, 1f)
        val eased = linear * linear * (3f - 2f * linear)
        onFrame(eased)
        if (linear < 1f) {
            choreographer.postFrameCallback(callback)
        } else {
            running = false
            onEnd()
        }
    }

    fun start() {
        cancel()
        running = true
        startNs = 0L
        choreographer.postFrameCallback(callback)
    }

    fun cancel() {
        running = false
        choreographer.removeFrameCallback(callback)
    }
}
