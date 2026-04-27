package com.betpass.mc01pilot.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.graphics.toArgb
import com.betpass.mc01pilot.ui.theme.CockpitPalette
import java.io.File
import java.io.FileOutputStream

class DrawingPad @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = CockpitPalette.OnBackground.toArgb()
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val paths = mutableListOf<Path>()
    private var current = Path()
    private var baseBitmap: Bitmap? = null
    var onStrokeFinished: (() -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(CockpitPalette.SurfaceContainer.toArgb())
        baseBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }
        paths.forEach { canvas.drawPath(it, paint) }
        canvas.drawPath(current, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                current = Path().apply { moveTo(event.x, event.y) }
            }

            MotionEvent.ACTION_MOVE -> current.lineTo(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                current.lineTo(event.x, event.y)
                paths.add(current)
                current = Path()
                onStrokeFinished?.invoke()
            }
        }
        invalidate()
        return true
    }

    fun clear() {
        baseBitmap = null
        paths.clear()
        current = Path()
        invalidate()
    }

    fun savePng(file: File) {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        draw(canvas)
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun loadPng(file: File) {
        baseBitmap = if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        paths.clear()
        current = Path()
        invalidate()
    }
}
