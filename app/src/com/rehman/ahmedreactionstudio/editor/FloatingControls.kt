package com.rehman.ahmedreactionstudio.editor

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.rehman.ahmedreactionstudio.R
import com.rehman.ahmedreactionstudio.util.UI

/**
 * Floating transport controls positioned on the canvas (bottom-right in
 * landscape, bottom-center in portrait). Three circular buttons stacked
 * vertically: Play/Pause, Stop, Record.
 *
 * These replace the old bottom-dock transport row so the canvas gets
 * maximum vertical space. Buttons are 48dp circles (56dp for record)
 * with semi-transparent dark backgrounds.
 */
class FloatingControls(context: Context) : LinearLayout(context) {

    private val playPauseBtn: View
    private val stopBtn: View
    private val recordBtn: View
    private var recordPulse: ValueAnimator? = null

    private var onPlayPause: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null
    private var onRecord: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        val gap = UI.dp(context, 8)

        // ── Play / Pause ──
        playPauseBtn = makeFab(48, R.drawable.ic_play, Color.WHITE,
            Color.argb(180, 14, 16, 22), "Play / Pause")
        addView(playPauseBtn, LayoutParams(UI.dp(context, 48), UI.dp(context, 48)))

        // ── Stop ──
        stopBtn = makeFab(48, R.drawable.ic_stop, Color.WHITE,
            Color.argb(180, 14, 16, 22), "Stop")
        addView(stopBtn, LayoutParams(UI.dp(context, 48), UI.dp(context, 48)).apply {
            topMargin = gap
        })

        // ── Record (larger, red accent) ──
        recordBtn = makeFab(56, R.drawable.ic_camera, Color.WHITE,
            Color.argb(220, 180, 30, 30), "Record")
        addView(recordBtn, LayoutParams(UI.dp(context, 56), UI.dp(context, 56)).apply {
            topMargin = gap
        })
    }

    fun bind(onPlayPause: () -> Unit, onStop: () -> Unit, onRecord: () -> Unit) {
        this.onPlayPause = onPlayPause
        this.onStop = onStop
        this.onRecord = onRecord
        playPauseBtn.setOnClickListener { onPlayPause() }
        stopBtn.setOnClickListener { onStop() }
        recordBtn.setOnClickListener { onRecord() }
    }

    /**
     * Update button states based on current playback/recording state.
     */
    fun update(playing: Boolean, recording: Boolean, recReady: Boolean) {
        // play/pause icon swap
        val ppIcon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
        val ppImg = playPauseBtn.findViewById<ImageView>(R.id.fab_icon)
            ?: playPauseBtn.tag as? ImageView
        ppImg?.setImageDrawable(Ic.get(context, ppIcon, Color.WHITE))
        playPauseBtn.contentDescription = if (playing) "Pause" else "Play"

        // stop enabled state
        stopBtn.alpha = if (recording || playing) 1f else 0.6f

        // record button
        val recColor = if (recording) Color.argb(230, 220, 40, 40)
                       else if (recReady) Color.argb(220, 180, 30, 30)
                       else Color.argb(150, 80, 40, 40)
        val recBg = recordBtn.background as? GradientDrawable
        recBg?.setColor(recColor)
        recordBtn.contentDescription =
            if (recording) "Stop recording" else "Start recording"

        // pulse animation on record while recording
        if (recording) {
            startRecordPulse()
        } else {
            stopRecordPulse()
        }
    }

    fun setVisible(show: Boolean) {
        if (show && visibility != View.VISIBLE) {
            visibility = View.VISIBLE
            alpha = 0f
            animate().alpha(1f).setDuration(150).start()
        } else if (!show && visibility == View.VISIBLE) {
            animate().alpha(0f).setDuration(150).withEndAction {
                visibility = View.GONE
            }.start()
        }
    }

    // ── helpers ──

    private fun makeFab(sizeDp: Int, icon: Int, iconColor: Int,
                        bgColor: Int, desc: String): View {
        val size = UI.dp(context, sizeDp)
        val container = FrameLayout(context).apply {
            contentDescription = desc
            isClickable = true
            isFocusable = true
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
                setStroke(UI.dp(context, 1), Color.argb(80, 255, 255, 255))
            }
            background = bg
        }
        val iv = ImageView(context).apply {
            id = R.id.fab_icon
            setImageDrawable(Ic.get(context, icon, iconColor))
            setPadding(UI.dp(context, sizeDp / 4), UI.dp(context, sizeDp / 4),
                UI.dp(context, sizeDp / 4), UI.dp(context, sizeDp / 4))
        }
        container.addView(iv, FrameLayout.LayoutParams(size, size))
        // store reference as tag too (in case id lookup fails in some build)
        container.tag = iv
        return container
    }

    private fun startRecordPulse() {
        if (recordPulse != null) return
        recordPulse = ValueAnimator.ofFloat(1f, 0.7f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                recordBtn.alpha = it.animatedValue as Float
            }
            start()
        }
    }

    private fun stopRecordPulse() {
        recordPulse?.cancel()
        recordPulse = null
        recordBtn.alpha = 1f
    }
}
