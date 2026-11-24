package com.aetherwave.core.island

import android.view.Choreographer

/**
 * A short animation driven by the display's frame clock. It does not use the system animator scale,
 * so it still plays when animations are turned off in developer options.
 *
 * [easing] maps linear time (0 to 1) to the eased value. [onFrame] receives the eased value.
 */
class FrameTween(
    private val durationMs: Long,
    private val easing: (Float) -> Float = ::smoothStep,
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
        onFrame(easing(linear))
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

/** Smooth start and finish, no overshoot. */
fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)

/** Ends with a small overshoot and settles, like a spring. */
fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val u = t - 1f
    return 1f + c3 * u * u * u + c1 * u * u
}
