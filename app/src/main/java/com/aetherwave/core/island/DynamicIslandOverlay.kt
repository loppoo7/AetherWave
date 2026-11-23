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
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.animation.LayoutTransition
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.aetherwave.R
import com.aetherwave.domain.model.LyricLine
import com.aetherwave.playback.PlaybackController
import com.aetherwave.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Dynamic Island: a floating pill over other apps while a song plays.
 *
 * - Compact: equaliser, title and artist, and a play or pause button.
 * - Tap it to expand. The card shows the current lyric, the next line, the progress, and skip buttons.
 * - Drag it anywhere on the screen. Its position is saved when you let go.
 *
 * It is added while AetherWave is in the background, and removed when the app comes back.
 */
object DynamicIslandOverlay {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var manager: WindowManager? = null
    private var island: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expandedPart: LinearLayout? = null
    private var title: TextView? = null
    private var artist: TextView? = null
    private var lyricNow: TextView? = null
    private var lyricNext: TextView? = null
    private var progressBar: View? = null
    private var equalizer: EqualizerView? = null
    private var playButton: ImageButton? = null
    private var expanded = false
    private var stateJob: Job? = null

    /** Adds the island if the overlay permission is granted. */
    fun show(context: Context, controller: PlaybackController) {
        val app = context.applicationContext
        if (island != null || !Settings.canDrawOverlays(app)) return
        val wm = app.getSystemService(WindowManager::class.java) ?: return
        val view = buildIsland(app, controller)
        val (fx, fy) = IslandPrefs.read(app)
        val metrics = app.resources.displayMetrics
        val lp = WindowManager.LayoutParams(
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
            x = (fx * metrics.widthPixels / 2f).roundToInt()
            y = statusBarHeight(app) + (fy * metrics.heightPixels).roundToInt()
        }
        view.setOnTouchListener(dragOrTapListener(app, wm))
        view.alpha = 0f
        view.translationY = -dp(app, 28).toFloat()
        wm.addView(view, lp)
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(420L)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
        manager = wm
        island = view
        params = lp
        stateJob = scope.launch {
            combine(controller.state, IslandLyrics.lines) { playback, lines -> playback to lines }
                .collect { (playback, lines) -> render(playback, lines, controller) }
        }
    }

    /** Removes the island and stops following playback. Safe to call when it is not shown. */
    fun hide() {
        stateJob?.cancel()
        stateJob = null
        val view = island
        val wm = manager
        island = null
        manager = null
        params = null
        expandedPart = null
        title = null
        artist = null
        lyricNow = null
        lyricNext = null
        progressBar = null
        equalizer = null
        playButton = null
        expanded = false
        if (view != null && wm != null) runCatching { wm.removeView(view) }
    }

    private fun render(playback: PlaybackState, lines: List<LyricLine>, controller: PlaybackController) {
        val track = playback.currentMusicTrack
        if (track == null) {
            hide()
            return
        }
        title?.text = track.title
        artist?.text = track.artist
        equalizer?.setPlaying(playback.isPlaying)
        playButton?.let { button ->
            button.setImageResource(
                if (playback.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            )
        }

        val index = lines.indexOfLast { it.timestampMs <= playback.position }
        val now = if (lines.isEmpty()) {
            island?.context?.getString(R.string.island_no_lyrics).orEmpty()
        } else {
            lines.getOrNull(index)?.text ?: "…"
        }
        setTextAnimated(lyricNow, now)
        setTextAnimated(lyricNext, lines.getOrNull(index + 1)?.text.orEmpty())

        val fraction = if (playback.duration > 0L) {
            (playback.position.toFloat() / playback.duration).coerceIn(0f, 1f)
        } else {
            0f
        }
        progressBar?.scaleX = fraction
    }

    private fun setTextAnimated(view: TextView?, text: String) {
        if (view == null || view.text.toString() == text) return
        view.text = text
        view.alpha = 0.25f
        view.animate().alpha(1f).setDuration(280L).start()
    }

    private fun setExpanded(open: Boolean) {
        expanded = open
        val part = expandedPart ?: return
        if (open) {
            part.visibility = View.VISIBLE
            part.alpha = 0f
            part.translationY = -dp(part.context, 6).toFloat()
            part.animate().alpha(1f).translationY(0f).setDuration(260L).start()
        } else {
            part.animate().alpha(0f).translationY(-dp(part.context, 6).toFloat()).setDuration(180L)
                .withEndAction { part.visibility = View.GONE }
                .start()
        }
    }

    /** Taps expand or collapse the card. Drags move the island and save the new place. */
    private fun dragOrTapListener(app: Context, wm: WindowManager): View.OnTouchListener {
        val slop = ViewConfiguration.get(app).scaledTouchSlop
        val metrics = app.resources.displayMetrics
        val halfWidth = metrics.widthPixels / 2f
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        return View.OnTouchListener { view, event ->
            val lp = params ?: return@OnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        lp.x = (startX + dx).roundToInt().coerceIn(-halfWidth.toInt(), halfWidth.toInt())
                        lp.y = (startY + dy).roundToInt().coerceIn(0, metrics.heightPixels / 2)
                        wm.updateViewLayout(view, lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val top = statusBarHeight(app)
                        IslandPrefs.write(
                            app,
                            lp.x / halfWidth,
                            (lp.y - top).coerceAtLeast(0) / metrics.heightPixels.toFloat(),
                        )
                    } else {
                        setExpanded(!expanded)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun buildIsland(context: Context, controller: PlaybackController): LinearLayout {
        val titleView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val artistView = TextView(context).apply {
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val equalizerView = EqualizerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 18), dp(context, 16)).apply {
                rightMargin = dp(context, 10)
            }
        }
        val textColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView)
            addView(artistView)
        }
        val play = ImageButton(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(android.R.drawable.ic_media_pause)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.island_play_pause)
            setOnClickListener {
                controller.playPause()
                animate().scaleX(0.82f).scaleY(0.82f).setDuration(90L).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(160L).setInterpolator(OvershootInterpolator()).start()
                }.start()
            }
        }
        val compactRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(equalizerView)
            addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(play, LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)))
        }

        val nowView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        val nextView = TextView(context).apply {
            setTextColor(0x8CFFFFFF.toInt())
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val bar = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(context, 2).toFloat()
            }
            pivotX = 0f
            scaleX = 0f
        }
        val track = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(0x26FFFFFF)
                cornerRadius = dp(context, 2).toFloat()
            }
            addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val previous = ImageButton(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(android.R.drawable.ic_media_previous)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.island_previous)
            setOnClickListener { controller.seekToPrevious() }
        }
        val next = ImageButton(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(android.R.drawable.ic_media_next)
            setColorFilter(Color.WHITE)
            contentDescription = context.getString(R.string.island_next)
            setOnClickListener { controller.seekToNext() }
        }
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(previous, LinearLayout.LayoutParams(dp(context, 44), dp(context, 40)))
            addView(next, LinearLayout.LayoutParams(dp(context, 44), dp(context, 40)))
        }
        val expandedColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(context, 6), dp(context, 10), dp(context, 6), dp(context, 4))
            visibility = View.GONE
            addView(nowView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(nextView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(context, 4)
            })
            addView(track, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 3)).apply {
                topMargin = dp(context, 12)
            })
            addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(context, 4)
            })
        }

        val pill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 28).toFloat()
            setColor(0xE6101216.toInt())
            setStroke(2, 0x33FFFFFF)
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = pill
            minimumWidth = dp(context, 230)
            setPadding(dp(context, 16), dp(context, 8), dp(context, 8), dp(context, 8))
            layoutTransition = LayoutTransition().apply {
                enableTransitionType(LayoutTransition.CHANGING)
            }
            addView(compactRow)
            addView(expandedColumn)
        }
        title = titleView
        artist = artistView
        lyricNow = nowView
        lyricNext = nextView
        progressBar = bar
        equalizer = equalizerView
        playButton = play
        expandedPart = expandedColumn
        return root
    }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(context, 24)
    }

    private fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
}
