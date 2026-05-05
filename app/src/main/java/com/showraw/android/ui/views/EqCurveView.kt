package com.showraw.android.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.showraw.android.audio.Equalizer
import com.showraw.android.presets.EqBand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.sin
import kotlin.math.sqrt

class EqCurveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val SR          = 48_000
    private val FREQ_MIN    = 20f
    private val FREQ_MAX    = 20_000f
    private val DB_MAX      = 15f
    private val POINTS      = 512
    private val PRIMARY     = Color.parseColor("#EF9F27")

    private var bandCoeffs  = emptyList<Equalizer.Coeffs>()

    private val paintBg     = Paint().apply { color = Color.parseColor("#111111") }
    private val paintGrid   = Paint().apply { color = Color.parseColor("#2A2A2A"); strokeWidth = 1f; isAntiAlias = true }
    private val paintZero   = Paint().apply { color = Color.parseColor("#444444"); strokeWidth = 1.5f; isAntiAlias = true }
    private val paintLabel  = Paint().apply { color = Color.parseColor("#555555"); textSize = 22f; isAntiAlias = true }
    private val paintCurve  = Paint().apply { color = PRIMARY; strokeWidth = 3f; style = Paint.Style.STROKE; isAntiAlias = true; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    private val curvePath   = Path()

    fun setBands(bands: List<EqBand>) {
        bandCoeffs = bands.map { Equalizer.computeCoeffsStatic(it, SR) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, paintBg)
        drawGrid(canvas, w, h)
        drawCurve(canvas, w, h)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        // Horizontal dB lines
        val dbLines = listOf(-12f, -6f, 0f, 6f, 12f)
        for (db in dbLines) {
            val y = dbToY(db, h)
            val paint = if (db == 0f) paintZero else paintGrid
            canvas.drawLine(0f, y, w, y, paint)
        }

        // Vertical frequency lines (octaves)
        val freqs = listOf(50f, 100f, 200f, 500f, 1_000f, 2_000f, 5_000f, 10_000f)
        for (f in freqs) {
            val x = freqToX(f, w)
            canvas.drawLine(x, 0f, x, h, paintGrid)
            val label = if (f >= 1000f) "${(f / 1000).toInt()}k" else "${f.toInt()}"
            canvas.drawText(label, x + 4f, h - 6f, paintLabel)
        }
        // dB labels
        for (db in listOf(-12f, -6f, 6f, 12f)) {
            val y = dbToY(db, h)
            val label = if (db > 0f) "+${db.toInt()}" else "${db.toInt()}"
            canvas.drawText(label, 6f, y - 4f, paintLabel)
        }
    }

    private fun drawCurve(canvas: Canvas, w: Float, h: Float) {
        if (bandCoeffs.isEmpty()) {
            // flat line at 0 dB
            val y = dbToY(0f, h)
            canvas.drawLine(0f, y, w, y, paintCurve)
            return
        }

        curvePath.reset()
        var first = true
        for (i in 0 until POINTS) {
            val t    = i.toFloat() / (POINTS - 1)
            val freq = FREQ_MIN * (FREQ_MAX / FREQ_MIN).pow(t)
            val db   = totalMagnitudeDb(freq).coerceIn(-DB_MAX, DB_MAX)
            val x    = freqToX(freq, w)
            val y    = dbToY(db, h)
            if (first) { curvePath.moveTo(x, y); first = false } else curvePath.lineTo(x, y)
        }
        canvas.drawPath(curvePath, paintCurve)
    }

    private fun totalMagnitudeDb(freq: Float): Float {
        val w = 2.0 * PI * freq / SR
        return bandCoeffs.sumOf { biquadMagnitudeDb(w, it).toDouble() }.toFloat()
    }

    private fun biquadMagnitudeDb(w: Double, c: Equalizer.Coeffs): Float {
        val cosW  = cos(w);  val cos2W = cos(2 * w)
        val sinW  = sin(w);  val sin2W = sin(2 * w)
        val numSq = (c.b0 + c.b1 * cosW + c.b2 * cos2W).pow2() + (c.b1 * sinW + c.b2 * sin2W).pow2()
        val denSq = (1.0  + c.a1 * cosW + c.a2 * cos2W).pow2() + (c.a1 * sinW + c.a2 * sin2W).pow2()
        if (denSq < 1e-30) return 0f
        return (10.0 * log10(numSq / denSq)).toFloat()
    }

    private fun Double.pow2() = this * this

    private fun freqToX(freq: Float, w: Float) =
        w * (log2(freq / FREQ_MIN) / log2(FREQ_MAX / FREQ_MIN))

    private fun dbToY(db: Float, h: Float) =
        h / 2f - (db / DB_MAX) * (h / 2f - 8f)

    private fun Float.pow(exp: Float) = Math.pow(this.toDouble(), exp.toDouble()).toFloat()
}
