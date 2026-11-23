package com.aetherwave.core.island

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.aetherwave.playback.PlaybackController
import com.aetherwave.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * A floating pill at the top of the screen that shows the current song over other apps. It is
 * added while AetherWave is in the background and removed when the app comes back to the front.
 * Tapping the pill opens the app. The button plays or pauses the song.
 */
object DynamicIslandOverlay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var pill: View? = null
    private var titleView: TextView? = null
    private var artistView: TextView? = null
    private var playButton: ImageButton? = null
    private var stateJob: Job? = null

    /** Adds the pill if the overlay permission is granted and a song is loaded. */
    fun show(context: Context, controller: PlaybackController) {
        val app = context.applicationContext
        if (pill != null || !Settings.canDrawOverlays(app)) return
        val manager = app.getSystemService(WindowManager::class.java) ?: return
        val root = buildPill(app, controller)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeight(app) + dp(app, 8)
        }
        manager.addView(root, params)
        windowManager = manager
        pill = root
        stateJob = scope.launch {
            controller.state.collect { render(it) }
        }
    }

    /** Removes the pill and stops listening to playback. Safe to call when it is not shown. */
    fun hide() {
        stateJob?.cancel()
        stateJob = null
        pill?.let { view -> runCatching { windowManager?.removeView(view) } }
        pill = null
        windowManager = null
        titleView = null
        artistView = null
        playButton = null
    }

    private fun render(state: PlaybackState) {
        val track = state.currentMusicTrack
        if (track == null) {
            hide()
            return
        }
        titleView?.text = track.title
        artistView?.text = track.artist
        playButton?.setImageResource(
            if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
    }

    private fun buildPill(context: Context, controller: PlaybackController): View {
        val title = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val artist = TextView(context).apply {
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(artist)
        }
        val button = ImageButton(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(Color.WHITE)
            contentDescription = "Play or pause"
            setOnClickListener { controller.playPause() }
        }
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 999).toFloat()
            setColor(0xE6101216.toInt())
            setStroke(2, 0x33FFFFFF)
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16), dp(context, 8), dp(context, 8), dp(context, 8))
            minimumWidth = dp(context, 220)
            this.background = background
            addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(button, LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)))
            setOnClickListener { openApp(context) }
        }
        titleView = title
        artistView = artist
        playButton = button
        return row
    }

    private fun openApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(intent)
    }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(context, 24)
    }

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
}
