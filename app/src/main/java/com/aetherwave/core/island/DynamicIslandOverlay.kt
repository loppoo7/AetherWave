package com.aetherwave.core.island

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
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
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Dynamic Island, styled like the Auralys preview.
 *
 * Compact: a cover with an equaliser on the left, and bars on the right.
 * Tap it to expand: the cover, title, artist, the current lyric line, a progress bar with times,
 * and previous, play or pause, and next buttons. Pressing the island blurs the content behind it
 * slightly. Drag it anywhere on the screen. The position is saved when you let go.
 *
 * All motion runs on [FrameTween], so it does not depend on the system animation setting.
 */
object DynamicIslandOverlay {
    private const val ENTER_MS = 420L
    private const val EXPAND_MS = 260L
    private const val COLLAPSE_MS = 180L
    private const val BLUR_MS = 180L
    private const val BLUR_DP = 10f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var manager: WindowManager? = null
    private var island: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var expandedPart: LinearLayout? = null
    private var compactPart: LinearLayout? = null
    private var title: TextView? = null
    private var artist: TextView? = null
    private var lyricLine: TextView? = null
    private var elapsedView: TextView? = null
    private var remainingView: TextView? = null
    private var progressBar: View? = null
    private var playButton: ImageButton? = null
    private var equalizers: List<EqualizerView> = emptyList()
    private var coverEqualizers: List<EqualizerView> = emptyList()
    private var coverViews: List<ImageView> = emptyList()
    private var coverBoxes: List<View> = emptyList()
    private var spinning = false
    private var spinStartNs = 0L
    private val spinFrame: Choreographer.FrameCallback = Choreographer.FrameCallback { frameNs ->
        if (!spinning) return@FrameCallback
        if (spinStartNs == 0L) spinStartNs = frameNs
        // One full turn every 6 seconds, like a record or a cassette reel.
        val angle = ((frameNs - spinStartNs) / 1_000_000f % 6000f) / 6000f * 360f
        coverBoxes.forEach { it.rotation = angle }
        Choreographer.getInstance().postFrameCallback(spinFrame)
    }
    private var artworkUrl: String? = null
    private var artworkJob: Job? = null
    private val http by lazy {
        OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    }
    private var expanded = false
    private var stateJob: Job? = null
    private var savedContext: Context? = null
    private var savedController: PlaybackController? = null
    private var attachedOnce = false
    private var detachedBySystem = false
    private var expandTween: FrameTween? = null
    private var blurTween: FrameTween? = null
    private var blurPx = 0

    /** Adds the island if the overlay permission is granted. */
    fun show(context: Context, controller: PlaybackController) {
        val app = context.applicationContext
        if (island != null && !detachedBySystem) return
        if (!Settings.canDrawOverlays(app)) return
        teardown()
        savedContext = app
        savedController = controller
        val wm = app.getSystemService(WindowManager::class.java) ?: return
        val look = IslandPrefs.readLook(app)
        val view = buildIsland(app, controller, look)
        val (fx, fy) = IslandPrefs.read(app)
        val metrics = app.resources.displayMetrics
        blurPx = dpF(app, BLUR_DP)
        val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (blurSupported) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0),
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = (fx * metrics.widthPixels / 2f).roundToInt()
            y = statusBarHeight(app) + (fy * metrics.heightPixels).roundToInt()
            if (blurSupported) blurBehindRadius = 0
        }
        view.setOnTouchListener(dragOrTapListener(app, wm))
        view.alpha = 0f
        view.translationY = -dpF(app, 24f).toFloat()
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                attachedOnce = true
            }

            override fun onViewDetachedFromWindow(v: View) {
                // Only a detach after the view was attached counts. This ignores the first layout pass.
                if (attachedOnce) detachedBySystem = true
            }
        })
        val added = runCatching { wm.addView(view, lp) }.isSuccess
        if (!added) {
            teardown()
            return
        }
        FrameTween(ENTER_MS) { t ->
            view.alpha = t
            view.translationY = -dpF(app, 24f) * (1f - t)
        }.start()
        manager = wm
        island = view
        params = lp
        stateJob = scope.launch {
            combine(controller.state, IslandLyrics.lines) { playback, lines -> playback to lines }
                .collect { (playback, lines) -> render(playback, lines) }
        }
    }

    /** Removes the island and stops following playback. Safe to call when it is not shown. */
    fun hide() {
        val view = island
        val wm = manager
        teardown()
        savedContext = null
        savedController = null
        if (view != null && wm != null) runCatching { wm.removeView(view) }
    }

    /** Stops every animation and forgets the views. It does not touch the window. */
    private fun teardown() {
        stateJob?.cancel()
        stateJob = null
        expandTween?.cancel()
        blurTween?.cancel()
        artworkJob?.cancel()
        setSpinning(false)
        island = null
        manager = null
        params = null
        expandedPart = null
        compactPart = null
        title = null
        artist = null
        lyricLine = null
        elapsedView = null
        remainingView = null
        progressBar = null
        playButton = null
        equalizers = emptyList()
        coverEqualizers = emptyList()
        coverViews = emptyList()
        coverBoxes = emptyList()
        artworkUrl = null
        expanded = false
        attachedOnce = false
        detachedBySystem = false
    }

    private fun render(playback: PlaybackState, lines: List<LyricLine>) {
        val track = playback.currentMusicTrack
        if (track == null) {
            hide()
            return
        }
        if (detachedBySystem) {
            // The system took the island away, for example while a game or another app was in front.
            // Build it again instead of leaving it missing.
            val context = savedContext
            val controller = savedController
            teardown()
            if (context != null && controller != null) show(context, controller)
            return
        }
        title?.text = track.title
        artist?.text = track.artist
        updateArtwork(track.artworkUri)
        equalizers.forEach { it.setPlaying(playback.isPlaying) }
        setSpinning(playback.isPlaying)
        playButton?.setImageResource(
            if (playback.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )

        val index = lines.indexOfLast { it.timestampMs <= playback.position }
        val current = if (lines.isEmpty()) {
            island?.context?.getString(R.string.island_no_lyrics).orEmpty()
        } else {
            lines.getOrNull(index)?.text.orEmpty()
        }
        setTextFading(lyricLine, current)

        elapsedView?.text = formatTime(playback.position)
        remainingView?.text = "-" + formatTime((playback.duration - playback.position).coerceAtLeast(0L))
        val fraction = if (playback.duration > 0L) {
            (playback.position.toFloat() / playback.duration).coerceIn(0f, 1f)
        } else {
            0f
        }
        progressBar?.scaleX = fraction
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000).coerceAtLeast(0L)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun setSpinning(on: Boolean) {
        if (on == spinning) return
        spinning = on
        if (on) {
            spinStartNs = 0L
            Choreographer.getInstance().postFrameCallback(spinFrame)
        } else {
            Choreographer.getInstance().removeFrameCallback(spinFrame)
        }
    }

    /** Loads the song's cover once per track. Until it arrives, the equaliser stays visible. */
    private fun updateArtwork(url: String?) {
        if (url == artworkUrl) return
        artworkUrl = url
        artworkJob?.cancel()
        if (url.isNullOrBlank()) {
            showCover(null)
            return
        }
        artworkJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) { runCatching { fetchBitmap(url) }.getOrNull() }
            if (artworkUrl == url) showCover(bitmap)
        }
    }

    private fun fetchBitmap(url: String): Bitmap? =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                response.body?.byteStream()?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = 2 })
                }
            }
        }

    private fun showCover(bitmap: Bitmap?) {
        coverViews.forEach { view ->
            view.setImageBitmap(bitmap)
            view.visibility = if (bitmap != null) View.VISIBLE else View.GONE
        }
        coverEqualizers.forEach { eq ->
            eq.visibility = if (bitmap != null) View.GONE else View.VISIBLE
        }
    }

    private fun setTextFading(view: TextView?, text: String) {
        if (view == null || view.text.toString() == text) return
        view.text = text
        FrameTween(280L) { t -> view.alpha = 0.25f + 0.75f * t }.start()
    }

    private fun setExpanded(open: Boolean) {
        expanded = open
        val part = expandedPart ?: return
        val compact = compactPart
        expandTween?.cancel()
        if (open) {
            // The compact pill fades and shrinks away, and the card grows in its place.
            part.visibility = View.VISIBLE
            compact?.let { c ->
                FrameTween(COLLAPSE_MS, onEnd = { c.visibility = View.GONE }) { t ->
                    c.alpha = 1f - t
                    c.scaleX = 1f - 0.12f * t
                    c.scaleY = 1f - 0.12f * t
                }.start()
            }
            expandTween = FrameTween(EXPAND_MS, easing = ::easeOutBack) { t ->
                part.alpha = t.coerceIn(0f, 1f)
                part.scaleY = 0.8f + 0.2f * t
                part.translationY = -dpF(part.context, 6f) * (1f - t)
            }.also { it.start() }
        } else {
            // The card shrinks away, and the compact pill comes back.
            compact?.let { c ->
                c.visibility = View.VISIBLE
                FrameTween(EXPAND_MS, easing = ::easeOutBack) { t ->
                    c.alpha = t.coerceIn(0f, 1f)
                    c.scaleX = 0.88f + 0.12f * t
                    c.scaleY = 0.88f + 0.12f * t
                }.start()
            }
            expandTween = FrameTween(COLLAPSE_MS, onEnd = { part.visibility = View.GONE }) { t ->
                part.alpha = 1f - t
                part.scaleY = 1f - 0.2f * t
                part.translationY = -dpF(part.context, 6f) * t
            }.also { it.start() }
        }
    }

    /** Blurs the content behind the island while it is pressed. Needs Android 12 or newer. */
    private fun setPressBlur(pressed: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val lp = params ?: return
        val view = island ?: return
        val wm = manager ?: return
        val from = lp.blurBehindRadius
        val to = if (pressed) blurPx else 0
        blurTween?.cancel()
        blurTween = FrameTween(BLUR_MS) { t ->
            lp.blurBehindRadius = (from + (to - from) * t).roundToInt()
            runCatching { wm.updateViewLayout(view, lp) }
        }.also { it.start() }
    }

    /** A tap expands or collapses the card. A drag moves the island and saves the new place. */
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
                    setPressBlur(true)
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
                    setPressBlur(false)
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
                MotionEvent.ACTION_CANCEL -> {
                    setPressBlur(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun roundedFill(context: Context, color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(context, radiusDp).toFloat()
            setColor(color)
        }

    /** A rounded square that shows the cover, with a small equaliser as the fallback. */
    private fun coverBox(context: Context, sizeDp: Int): Triple<FrameLayout, EqualizerView, ImageView> {
        val eq = EqualizerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(context, 20), dp(context, 16), Gravity.CENTER)
        }
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val box = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x33FFFFFF)
            }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
            addView(image)
            addView(eq)
        }
        return Triple(box, eq, image)
    }

    private fun buildIsland(context: Context, controller: PlaybackController, look: IslandPrefs.Look): LinearLayout {
        val glass = look.style == IslandPrefs.STYLE_GLASS
        val pillColor = if (glass) 0xCC1E2128.toInt() else Color.BLACK

        // Compact row: cover on the left, equaliser bars on the right.
        val (compactCover, compactEq, compactImage) = coverBox(context, 30)
        val compactBars = EqualizerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 22), dp(context, 16)).apply {
                leftMargin = dp(context, 8)
            }
        }
        val compactRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 6), dp(context, 5), dp(context, 10), dp(context, 5))
            addView(compactCover)
            addView(View(context), LinearLayout.LayoutParams(dp(context, (IslandPrefs.readWidth(context) - 76).coerceAtLeast(0)), 1))
            addView(compactBars)
        }

        // Expanded card.
        val (expandedCover, expandedEq, expandedImage) = coverBox(context, 58)
        val titleView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
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
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(expandedCover)
            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(titleView)
                    addView(artistView)
                },
                LinearLayout.LayoutParams(dp(context, 182), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    leftMargin = dp(context, 12)
                },
            )
        }
        val lyricView = TextView(context).apply {
            setTextColor(0x8CFFFFFF.toInt())
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val elapsed = TextView(context).apply {
            setTextColor(0x99FFFFFF.toInt())
            textSize = 10f
        }
        val remaining = TextView(context).apply {
            setTextColor(0x99FFFFFF.toInt())
            textSize = 10f
        }
        val bar = View(context).apply {
            background = roundedFill(context, Color.WHITE, 2f)
            pivotX = 0f
            scaleX = 0f
        }
        val track = FrameLayout(context).apply {
            background = roundedFill(context, 0x33FFFFFF, 2f)
            addView(
                bar,
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
        }
        val progressRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(elapsed)
            addView(
                track,
                LinearLayout.LayoutParams(dp(context, 170), dp(context, 3)).apply {
                    leftMargin = dp(context, 8)
                    rightMargin = dp(context, 8)
                },
            )
            addView(remaining)
        }
        val previous = iconButton(context, android.R.drawable.ic_media_previous, context.getString(R.string.island_previous)) {
            controller.seekToPrevious()
        }
        val play = iconButton(context, android.R.drawable.ic_media_pause, context.getString(R.string.island_play_pause)) {
            controller.playPause()
        }
        val next = iconButton(context, android.R.drawable.ic_media_next, context.getString(R.string.island_next)) {
            controller.seekToNext()
        }
        val controls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(previous, LinearLayout.LayoutParams(dp(context, 48), dp(context, 40)))
            addView(play, LinearLayout.LayoutParams(dp(context, 56), dp(context, 44)))
            addView(next, LinearLayout.LayoutParams(dp(context, 48), dp(context, 40)))
        }
        val expandedColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            pivotY = 0f
            minimumWidth = dp(context, 280)
            setPadding(dp(context, 14), dp(context, 4), dp(context, 14), dp(context, 12))
            visibility = View.GONE
            addView(header)
            addView(
                lyricView,
                LinearLayout.LayoutParams(dp(context, 252), LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(context, 8) },
            )
            addView(
                progressRow,
                LinearLayout.LayoutParams(dp(context, 252), LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(context, 12) },
            )
            addView(
                controls,
                LinearLayout.LayoutParams(dp(context, 252), LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(context, 4) },
            )
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = dp(context, IslandPrefs.readWidth(context))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(context, IslandPrefs.cornerDp(look.corner)).toFloat()
                setColor(pillColor)
                if (glass) setStroke(2, 0x1AFFFFFF)
            }
            addView(compactRow)
            addView(expandedColumn)
        }

        title = titleView
        artist = artistView
        lyricLine = lyricView
        elapsedView = elapsed
        remainingView = remaining
        progressBar = bar
        playButton = play
        expandedPart = expandedColumn
        compactPart = compactRow
        equalizers = listOf(compactEq, compactBars, expandedEq)
        coverEqualizers = listOf(compactEq, expandedEq)
        coverViews = listOf(compactImage, expandedImage)
        coverBoxes = listOf(compactCover, expandedCover)
        return root
    }

    /**
     * A flat icon button. It bounces when tapped, and it reports presses to the island so the
     * background blurs. It does not consume touches, so the click still reaches the button.
     */
    private fun iconButton(context: Context, drawable: Int, description: String, onClick: () -> Unit): ImageButton =
        ImageButton(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setImageResource(drawable)
            setColorFilter(Color.WHITE)
            contentDescription = description
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> setPressBlur(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setPressBlur(false)
                }
                false
            }
            setOnClickListener {
                onClick()
                FrameTween(230L) { t ->
                    val scale = if (t < 0.5f) 1f - 0.15f * (t * 2f) else 0.85f + 0.15f * ((t - 0.5f) * 2f)
                    scaleX = scale
                    scaleY = scale
                }.start()
            }
        }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else dp(context, 24)
    }

    private fun dp(context: Context, value: Int): Int = dpF(context, value.toFloat())

    private fun dpF(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()
}
