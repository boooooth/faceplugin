package com.faceplugin.facerecognitionsdk.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Plain white screen with a circular window cut out for the camera, plus a
 * progress ring around the circle.
 */
class LivenessOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 0f..1f — how much of the ring is filled. */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var ringColor: Int = Color.parseColor("#111827")
        set(value) {
            field = value
            ringPaint.color = value
            invalidate()
        }

    /** Vertical position of the circle center as a fraction of view height. */
    var centerYFraction: Float = 0.40f

    private val density = resources.displayMetrics.density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * density
        color = Color.parseColor("#E5E7EB")
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * density
        strokeCap = Paint.Cap.ROUND
        color = ringColor
    }
    private val arcRect = RectF()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    val circleRadius: Float get() = width * 0.38f
    val circleCenterY: Float get() = height * centerYFraction

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = circleCenterY
        val r = circleRadius

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        canvas.drawCircle(cx, cy, r, clearPaint)

        val ringR = r + 10f * density
        arcRect.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        canvas.drawOval(arcRect, trackPaint)
        if (progress > 0f) {
            canvas.drawArc(arcRect, -90f, 360f * progress, false, ringPaint)
        }
    }
}
