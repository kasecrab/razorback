package io.github.kasecrab.razorback.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/** Pinch to zoom, drag to pan, double-tap to toggle; the bitmap is drawn through one matrix. */
class ZoomImageView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val values = FloatArray(9)
    private var minScale = 1f
    private var fitScale = 1f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val current = scale()
            var f = d.scaleFactor
            val next = (current * f).coerceIn(minScale, minScale * 6f)
            f = next / current
            matrix.postScale(f, f, d.focusX, d.focusY)
            clamp()
            invalidate()
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            matrix.postTranslate(-dx, -dy)
            clamp()
            invalidate()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val target = if (scale() > fitScale * 1.5f) fitScale else fitScale * 2.5f
            val f = target / scale()
            matrix.postScale(f, f, e.x, e.y)
            clamp()
            invalidate()
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTap?.invoke()
            return true
        }
    })

    var onTap: (() -> Unit)? = null

    fun setBitmap(b: Bitmap) {
        bitmap = b
        fit()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = fit()

    private fun fit() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        fitScale = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        minScale = fitScale
        matrix.reset()
        matrix.postScale(fitScale, fitScale)
        matrix.postTranslate((width - b.width * fitScale) / 2f, (height - b.height * fitScale) / 2f)
    }

    private fun scale(): Float {
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private fun clamp() {
        val b = bitmap ?: return
        matrix.getValues(values)
        val s = values[Matrix.MSCALE_X]
        val cw = b.width * s
        val ch = b.height * s
        var tx = values[Matrix.MTRANS_X]
        var ty = values[Matrix.MTRANS_Y]
        tx = if (cw <= width) (width - cw) / 2f else tx.coerceIn(width - cw, 0f)
        ty = if (ch <= height) (height - ch) / 2f else ty.coerceIn(height - ch, 0f)
        values[Matrix.MTRANS_X] = tx
        values[Matrix.MTRANS_Y] = ty
        matrix.setValues(values)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) gestures.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        bitmap?.let { canvas.drawBitmap(it, matrix, paint) }
    }
}
