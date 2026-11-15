package de.erikedits.pocketbot.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import de.erikedits.pocketbot.engine.modules.ImageRenderer
import java.io.ByteArrayOutputStream

/** Draws welcome and rank cards with Android's Canvas (PNG). */
class AndroidImageRenderer : ImageRenderer {
    private fun opaque(color: Int) = color or 0xFF000000.toInt()

    private fun background(canvas: Canvas, w: Float, h: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, w, h, opaque(color), Color.rgb(30, 31, 34), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(0f, 0f, w, h), 36f, 36f, paint)
    }

    private fun avatar(canvas: Canvas, bytes: ByteArray?, cx: Float, cy: Float, r: Float) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(cx, cy, r + 6f, ring)
        val bmp = bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
        if (bmp == null) {
            canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(88, 101, 242) })
            return
        }
        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = (2 * r) / minOf(bmp.width, bmp.height)
        shader.setLocalMatrix(Matrix().apply { setScale(scale, scale); postTranslate(cx - bmp.width * scale / 2, cy - bmp.height * scale / 2) })
        canvas.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
    }

    private fun text(size: Float, bold: Boolean, color: Int = Color.WHITE) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun fit(paint: Paint, text: String, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    private fun png(bmp: Bitmap): ByteArray = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    override fun welcomeCard(avatar: ByteArray?, title: String, name: String, subtitle: String, color: Int): ByteArray {
        val w = 1024; val h = 400
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        background(c, w.toFloat(), h.toFloat(), color)
        avatar(c, avatar, 200f, 200f, 120f)
        val t1 = text(64f, true); val t2 = text(48f, true); val t3 = text(32f, false, Color.argb(220, 255, 255, 255))
        c.drawText(fit(t1, title, 640f), 360f, 160f, t1)
        c.drawText(fit(t2, name, 640f), 360f, 235f, t2)
        c.drawText(fit(t3, subtitle, 640f), 360f, 295f, t3)
        return png(bmp)
    }

    override fun rankCard(avatar: ByteArray?, name: String, level: Int, xp: Long, needed: Long, rank: Int, color: Int): ByteArray {
        val w = 1000; val h = 300
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        background(c, w.toFloat(), h.toFloat(), color)
        avatar(c, avatar, 150f, 150f, 100f)
        val tName = text(52f, true); val tInfo = text(34f, false, Color.argb(230, 255, 255, 255)); val tBig = text(44f, true)
        c.drawText(fit(tName, name, 520f), 290f, 110f, tName)
        c.drawText("Level $level", 290f, 165f, tBig)
        if (rank > 0) c.drawText("#$rank", w - 60f - tBig.measureText("#$rank"), 110f, tBig)
        val barL = 290f; val barR = w - 60f; val barT = 200f; val barB = 240f
        c.drawRoundRect(RectF(barL, barT, barR, barB), 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(90, 255, 255, 255) })
        val frac = if (needed <= 0) 0f else (xp.toFloat() / needed).coerceIn(0f, 1f)
        if (frac > 0f) c.drawRoundRect(RectF(barL, barT, barL + (barR - barL) * frac, barB), 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
        val prog = "$xp / $needed XP"
        c.drawText(prog, barR - tInfo.measureText(prog), barT - 12f, tInfo)
        return png(bmp)
    }
}
