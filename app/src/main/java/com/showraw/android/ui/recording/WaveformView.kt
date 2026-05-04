package com.showraw.android.ui.recording

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val samples = FloatArray(COLUMNS)   // 0.0 – 1.0 amplitude
    private var head = 0

    private val paintGreen  = barPaint(Color.parseColor("#22C55E"))
    private val paintYellow = barPaint(Color.parseColor("#EAB308"))
    private val paintRed    = barPaint(Color.parseColor("#EF4444"))
    private val paintBg     = barPaint(Color.parseColor("#1A1A1A"))

    /** Empurra novo valor de amplitude (0.0–1.0 linear). Thread-safe via postInvalidate. */
    fun push(amplitude: Float) {
        samples[head] = amplitude.coerceIn(0f, 1f)
        head = (head + 1) % COLUMNS
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val colW = w / COLUMNS

        for (col in 0 until COLUMNS) {
            val idx = (head + col) % COLUMNS
            val amp = samples[idx]
            val x   = col * colW
            // background stripe
            canvas.drawRect(x, 0f, x + colW - 1f, h, paintBg)
            // amplitude bar (grows upward from center)
            val barH = amp * h
            val paint = when {
                amp > 0.70f -> paintRed
                amp > 0.35f -> paintYellow
                else        -> paintGreen
            }
            canvas.drawRect(x, h - barH, x + colW - 1f, h, paint)
        }
    }

    private fun barPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    companion object {
        private const val COLUMNS = 120
    }
}
