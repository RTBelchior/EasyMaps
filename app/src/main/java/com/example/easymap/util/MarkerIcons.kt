package com.example.easymap.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.easymap.model.Category

/** Desenha o ícone redondo (cor + letra) de cada categoria de ocorrência. */
object MarkerIcons {

    fun create(context: Context, category: Category): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (34 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f

        paint.color = Color.WHITE
        canvas.drawCircle(center, center, center, paint)
        paint.color = Color.parseColor(category.colorHex)
        canvas.drawCircle(center, center, center - 3 * density, paint)

        paint.color = Color.WHITE
        paint.textSize = size * 0.5f
        paint.textAlign = Paint.Align.CENTER
        paint.isFakeBoldText = true
        val baseline = center - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(category.symbol, center, baseline, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
