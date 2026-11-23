package com.aetherwave.core.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/** Four bars that rise and fall while a song plays, and sit still when it is paused. */
class EqualizerView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private var phase = 0f
    private var playing = false
    private var animator: ValueAnimator? = null

    fun setPlaying(isPlaying: Boolean) {
        if (isPlaying == playing) return
        playing = isPlaying
        if (isPlaying) {
            val running = animator ?: ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                duration = 900L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                animator = this
            }
            running.start()
        } else {
            animator?.cancel()
            phase = 0f
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
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
