package com.surendramaran.yolov8tflite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import java.util.LinkedList
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results = listOf<BoundingBox>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var bounds = Rect()

    init {
        initPaints()
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 40f

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        boxPaint.strokeWidth = 8F
        boxPaint.style = Paint.Style.STROKE
    }

    private fun getColorForClass(clsName: String): Int {
        return when (clsName.lowercase()) {
            "clavel" -> Color.rgb(255, 11, 85);
            "crisantemo" -> Color.rgb(126, 48, 225);
            "delphinio" -> Color.rgb(84, 9, 218)
            "girasol" -> Color.rgb(255, 164, 27);
            "iris" -> Color.CYAN;
            "rosa" -> Color.rgb(227, 23, 10)
            "tulipan" -> Color.rgb(209, 17, 73)
            else -> Color.rgb(12, 206, 107)
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        results.forEach {
            val left = it.x1 * width
            val top = it.y1 * height
            val right = it.x2 * width
            val bottom = it.y2 * height
            val drawableText = "${it.clsName} ${(it.cnf * 100).toInt()}%"

            // Asigna color según clase
            boxPaint.color = getColorForClass(it.clsName)

            // Dibuja rectángulo redondeado
            val cornerRadius = 20f
            canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, boxPaint)

            // Medidas del texto
            textBackgroundPaint.color = getColorForClass(it.clsName);
            textBackgroundPaint.getTextBounds(drawableText, 0, drawableText.length, bounds)
            val textWidth = textPaint.measureText(drawableText) //CAMBIADO
            val fontMetrics = textPaint.fontMetrics
            val textHeight = fontMetrics.bottom - fontMetrics.top //CAMBIADO

            // Fondo del texto (redondeado y transparente)
            val bgLeft = left
            val bgTop = top - textHeight - 2 * BOUNDING_RECT_TEXT_PADDING
            val bgRight = left + textWidth + 2 * BOUNDING_RECT_TEXT_PADDING
            val bgBottom = top

            canvas.drawRoundRect(
                bgLeft,
                bgTop,
                bgRight,
                bgBottom,
                12f, 12f,
                textBackgroundPaint

            )

            // Texto
            canvas.drawText(
                drawableText,
                left + BOUNDING_RECT_TEXT_PADDING,
                top - fontMetrics.bottom - BOUNDING_RECT_TEXT_PADDING,
                textPaint
            )
        }
    }

    fun setResults(boundingBoxes: List<BoundingBox>) {
        results = boundingBoxes
        invalidate()
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}