package com.ubermax.app.ui.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ubermax.app.R
import com.ubermax.app.data.db.dao.HourStat
import kotlin.math.max

/**
 * Gráfico de barras por hora dibujado con Canvas nativo (sin librerías extra).
 * Cada barra es la ganancia neta promedio de una hora con viajes aceptados.
 */
class HourBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_tertiary)
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private var hours: List<HourStat> = emptyList()

    fun setHours(stats: List<HourStat>) {
        hours = stats
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (hours.isEmpty()) return

        val labelSpace = 32f
        val chartHeight = height - labelSpace
        if (chartHeight <= 0f) return

        val maxProfit = max(hours.maxOf { it.avgProfit }, 0.01)
        val slotWidth = width.toFloat() / 24f
        val barWidth = slotWidth * 0.6f
        val gap = (slotWidth - barWidth) / 2f

        for (stat in hours) {
            val hour = stat.hour_of_day.coerceIn(0, 23)
            val barHeight = (stat.avgProfit / maxProfit * chartHeight).toFloat().coerceAtLeast(2f)
            val left = hour * slotWidth + gap
            val rect = RectF(left, chartHeight - barHeight, left + barWidth, chartHeight)
            canvas.drawRect(rect, barPaint)
            canvas.drawText("${hour}h", left + barWidth / 2f, height - 8f, labelPaint)
        }
    }
}
