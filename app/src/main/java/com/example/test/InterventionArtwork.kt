package com.example.test

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Native overlays use the same symbols and mode colors as the Compose preview. */
internal object InterventionArtwork {
    val colors = intArrayOf(Color.rgb(38,115,96), Color.rgb(172,117,64), Color.rgb(104,125,162), Color.rgb(139,115,150))
    val tints = intArrayOf(Color.rgb(231,238,227), Color.rgb(246,234,217), Color.rgb(232,237,245), Color.rgb(238,232,242))
    fun symbol(canvas: Canvas, mode: Int, x: Float, y: Float, size: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = tints[mode]
        canvas.drawRoundRect(x,y,x+size,y+size,size*.32f,size*.32f,p)
        val save = canvas.save()
        canvas.translate(x+size*.24f,y+size*.24f)
        val w = size*.52f
        p.color = colors[mode]; p.style = Paint.Style.STROKE; p.strokeWidth = size*.04f; p.strokeCap = Paint.Cap.ROUND
        fun line(a: Float,b: Float,c: Float,d: Float) = canvas.drawLine(a*w,b*w,c*w,d*w,p)
        when(mode) {
            1 -> {
                val path = android.graphics.Path().apply { moveTo(w*.2f,w*.74f); lineTo(w*.28f,w*.58f); lineTo(w*.28f,w*.35f); cubicTo(w*.28f,w*.05f,w*.72f,w*.05f,w*.72f,w*.35f); lineTo(w*.72f,w*.58f); lineTo(w*.8f,w*.74f); close() }
                canvas.drawPath(path,p); line(.43f,.9f,.57f,.9f)
            }
            2 -> { line(.5f,.85f,.5f,.2f); line(.5f,.2f,.32f,.38f); line(.5f,.2f,.68f,.38f); canvas.drawArc(RectF(w*.15f,w*.5f,w*.85f,w*.9f),0f,180f,false,p) }
            3 -> { line(.2f,.1f,.8f,.1f); line(.2f,.9f,.8f,.9f); line(.25f,.12f,.25f,.25f); line(.75f,.12f,.75f,.25f); line(.25f,.25f,.75f,.75f); line(.75f,.25f,.25f,.75f); line(.25f,.75f,.25f,.88f); line(.75f,.75f,.75f,.88f) }
        }
        canvas.restoreToCount(save)
    }
}
