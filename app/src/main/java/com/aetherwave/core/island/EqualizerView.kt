package com.aetherwave.core.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/**
 * Four bars that rise and fall while a song plays, and sit still when it is paused. The motion runs
 * on the frame clock, so it works even when animations are turned off in developer options.
 */
class EqualizerView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private var phase = 0f
    private var playing = false
    private var startNs = 0L

    private val frame = Choreographer.FrameCallback { frameNs ->
        if (!playing) return@FrameCallback
        if (startNs == 0L) startNs = frameNs
        phase = ((frameNs - startNs) / 1_000_000f) * (2f * PI.toFloat() / 900f)
        invalidate()
        Choreographer.getInstance().postFrameCallback(frame)
    }

    fun setPlaying(isPlaying: Boolean) {
        if (isPlaying == playing) return
        playing = isPlaying
        if (isPlaying) {
            startNs = 0L
            Choreographer.getInstance().postFrameCallback(frame)
        } else {
            Choreographer.getInstance().removeFrameCallback(frame)
            phase = 0f
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        playing = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    override fun onDraw(canvas: Canvas) {
        val bars = 4
        val slot = width / (bars * 2f + 1f)
        for (i in 0 until bars) {
            val level = if (playing) 0.3f + 0.7f * ((sin(phase + i * 1.4f) + 1f) / 2f) else 0.25f
            val barHeight = height * level
            val left = slot + i * slot * 2f
            val top = (height - barHeight) / 2f
            canvas.drawRoundRect(left, top, left + slot, top + barHeight, slot, slot, paint)
        }
    }
}
