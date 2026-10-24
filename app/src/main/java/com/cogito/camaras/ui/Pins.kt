package com.cogito.camaras.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.cogito.camaras.Kind

/** Pines estilo Google Maps: gota de color con un dibujo blanco adentro. */
object Pins {

    const val RED = 0xFFD93025.toInt()   // estatal
    const val GRAY = 0xFF5F6368.toInt()  // sin datos o privada
    const val BLUE = 0xFF1A73E8.toInt()

    fun all(density: Float): Map<String, Bitmap> = buildMap {
        for (state in listOf(true, false)) for (k in Kind.entries) {
            put("pin_" + (if (state) "s" else "o") + "_" + k.name.lowercase(), pin(density, if (state) RED else GRAY, k))
        }
    }

    fun pin(density: Float, color: Int, kind: Kind, scale: Float = 1f): Bitmap {
        val w = (30 * density * scale).toInt()
        val h = (40 * density * scale).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = w / 2f - density
        val cx = w / 2f
        val cy = r + density

        // Sombra suave
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x33000000 }
        c.drawOval(RectF(cx - r * 0.45f, h - density * 4, cx + r * 0.45f, h.toFloat()), shadow)

        // Gota
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val drop = Path().apply {
            addCircle(cx, cy, r, Path.Direction.CW)
            moveTo(cx - r * 0.62f, cy + r * 0.78f)
            lineTo(cx, h - density * 2.5f)
            lineTo(cx + r * 0.62f, cy + r * 0.78f)
            close()
        }
        c.drawPath(drop, body)
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0x40000000; style = Paint.Style.STROKE; strokeWidth = density * 0.8f
        }
        c.drawCircle(cx, cy, r, edge)

        // Dibujo blanco
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
        val u = r / 10f
        when (kind) {
            Kind.FIXED -> {
                // Cuerpo de cámara inclinado, lente y soporte
                c.save()
                c.rotate(-18f, cx, cy)
                c.drawRoundRect(RectF(cx - 5.5f * u, cy - 3f * u, cx + 3.5f * u, cy + 2f * u), u, u, white)
                c.drawRect(cx + 3.5f * u, cy - 2f * u, cx + 6f * u, cy + 1f * u, white)
                c.restore()
                c.drawRect(cx - 1.2f * u, cy + 1.5f * u, cx + 0.4f * u, cy + 5f * u, white)
                c.drawRect(cx - 4.5f * u, cy + 4f * u, cx + 0.4f * u, cy + 5.4f * u, white)
            }
            Kind.DOME -> {
                c.drawRect(cx - 6f * u, cy - 4f * u, cx + 6f * u, cy - 2.6f * u, white)
                c.drawArc(RectF(cx - 5f * u, cy - 7.5f * u, cx + 5f * u, cy + 4.5f * u), 0f, 180f, true, white)
                val lens = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                c.drawCircle(cx, cy + 0.6f * u, 1.6f * u, lens)
            }
            Kind.ALPR -> {
                // Patente
                val plate = RectF(cx - 6.5f * u, cy - 3.5f * u, cx + 6.5f * u, cy + 3.5f * u)
                c.drawRoundRect(plate, u, u, white)
                val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                c.drawRect(cx - 5f * u, cy - 1.4f * u, cx - 1.5f * u, cy + 1.4f * u, ink)
                c.drawRect(cx - 0.8f * u, cy - 1.4f * u, cx + 0.8f * u, cy + 1.4f * u, ink)
                c.drawRect(cx + 1.5f * u, cy - 1.4f * u, cx + 5f * u, cy + 1.4f * u, ink)
            }
        }
        return bmp
    }

    /** Punto azul con aro blanco, para el pin de "marcar acá". */
    fun placing(density: Float): Bitmap = pin(density, BLUE, Kind.FIXED, 1.25f)
}
