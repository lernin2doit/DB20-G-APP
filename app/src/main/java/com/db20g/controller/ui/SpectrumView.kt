package com.db20g.controller.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.log10
import kotlin.math.max

class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF333333.toInt()
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF888888.toInt()
        textSize = 24f
    }
    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF6F00.toInt()
        strokeWidth = 2f
    }

    private var magnitudes: FloatArray = FloatArray(0)
    private var frequencies: FloatArray = FloatArray(0)
    private var peakHolds: FloatArray = FloatArray(0)
    private var maxFrequency: Float = 8000f // Only show up to 8kHz for voice

    private val fillPath = Path()

    fun updateSpectrum(mags: FloatArray, freqs: FloatArray) {
        magnitudes = mags
        frequencies = freqs
        updatePeakHolds()
        invalidate()
    }

    private fun updatePeakHolds() {
        if (peakHolds.size != magnitudes.size) {
            peakHolds = FloatArray(magnitudes.size)
        }
        for (i in magnitudes.indices) {
            if (magnitudes[i] > peakHolds[i]) {
                peakHolds[i] = magnitudes[i]
            } else {
                peakHolds[i] *= 0.98f // Slow decay
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 40f

        canvas.drawColor(0xFF0D0D0D.toInt())

        // Draw grid lines
        for (i in 1..3) {
            val y = padding + (h - padding * 2) * i / 4f
            canvas.drawLine(padding, y, w - padding, y, gridPaint)
        }

        // Frequency labels
        val freqLabels = listOf("0", "1k", "2k", "4k", "8k")
        val positions = listOf(0f, 0.125f, 0.25f, 0.5f, 1f)
        for (i in freqLabels.indices) {
            val x = padding + (w - padding * 2) * positions[i]
            canvas.drawText(freqLabels[i], x, h - 5f, labelPaint)
            canvas.drawLine(x, padding, x, h - padding, gridPaint)
        }

        if (magnitudes.isEmpty() || frequencies.isEmpty()) return

        // Find bins up to maxFrequency
        val maxBin = frequencies.indexOfLast { it <= maxFrequency }.coerceAtLeast(1)

        // Find max magnitude for scaling
        var maxMag = 1f
        for (i in 0..maxBin) {
            if (magnitudes[i] > maxMag) maxMag = magnitudes[i]
        }

        // Draw filled spectrum
        val gradient = LinearGradient(
            0f, padding, 0f, h - padding,
            intArrayOf(0xFF4CAF50.toInt(), 0xFF2196F3.toInt(), 0xFF1A237E.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        barPaint.shader = gradient
        barPaint.style = Paint.Style.FILL

        fillPath.reset()
        fillPath.moveTo(padding, h - padding)

        for (i in 0..maxBin) {
            val x = padding + (w - padding * 2) * (i.toFloat() / maxBin)
            val dbMag = if (magnitudes[i] > 0) 20f * log10(magnitudes[i] / maxMag) else -60f
            val normalized = ((dbMag + 60f) / 60f).coerceIn(0f, 1f)
            val y = h - padding - normalized * (h - padding * 2)
            fillPath.lineTo(x, y)
        }

        fillPath.lineTo(w - padding, h - padding)
        fillPath.close()
        canvas.drawPath(fillPath, barPaint)

        // Draw peak hold dots
        for (i in 0..maxBin) {
            val x = padding + (w - padding * 2) * (i.toFloat() / maxBin)
            val dbPeak = if (peakHolds[i] > 0) 20f * log10(peakHolds[i] / maxMag) else -60f
            val normalized = ((dbPeak + 60f) / 60f).coerceIn(0f, 1f)
            val y = h - padding - normalized * (h - padding * 2)
            if (i % 4 == 0) { // Sparse peak dots to reduce overdraw
                canvas.drawCircle(x, y, 2f, peakPaint)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = 200
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val h = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(heightSize)
            else -> desiredHeight
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h)
    }
}
