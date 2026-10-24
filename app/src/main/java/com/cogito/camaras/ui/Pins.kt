package com.cogito.camaras.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.cogito.camaras.Kind

/** Pines estilo Google Maps: gota de color con un dibujo blanco adentro. */
object Pins {

    const val RED = 0xFFD93025.toInt()
    const val PURPLE = 0xFF8E24AA.toInt()  // murales
    const val GREEN = 0xFF188038.toInt()   // puntos panorámicos
    const val BLUE = 0xFF1A73E8.toInt()    // estacionamientos

    fun colorOf(k: Kind) = when (k) {
        Kind.FIXED -> PURPLE
        Kind.DOME -> GREEN
        Kind.ALPR -> BLUE
    }

    fun all(density: Float): Map<String, Bitmap> =
        Kind.entries.associate { k -> "pin_" + k.name.lowercase() to pin(density, colorOf(k), k) }

    fun pin(density: Float, color: Int, kind: Kind?, scale: Float = 1f): Bitmap {
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
        if (kind == null) {
            // Marca de lugar clásica: círculo blanco en el centro
            val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF7A1A12.toInt() }
            c.drawCircle(cx, cy, r * 0.36f, dot)
            return bmp
        }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        when (kind) {
            Kind.FIXED -> {
                // Paleta de pintor
                c.drawCircle(cx, cy, 6.2f * u, white)
                c.drawCircle(cx + 2.6f * u, cy + 2.6f * u, 1.7f * u, ink)
                c.drawCircle(cx - 2.6f * u, cy - 1.8f * u, 1.1f * u, ink)
                c.drawCircle(cx + 0.6f * u, cy - 3.4f * u, 1.1f * u, ink)
                c.drawCircle(cx + 3.6f * u, cy - 1.2f * u, 1.1f * u, ink)
            }
            Kind.DOME -> {
                // Montañas
                val m = Path().apply {
                    moveTo(cx - 6.8f * u, cy + 4.2f * u)
                    lineTo(cx - 1.8f * u, cy - 4.6f * u)
                    lineTo(cx + 1.2f * u, cy + 0.6f * u)
                    lineTo(cx + 3f * u, cy - 2.4f * u)
                    lineTo(cx + 6.8f * u, cy + 4.2f * u)
                    close()
                }
                c.drawPath(m, white)
            }
            Kind.ALPR -> {
                // Letra P
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = 0xFFFFFFFF.toInt()
                    textSize = 12.5f * u
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                c.drawText("P", cx, cy + 4.4f * u, tp)
            }
        }
        return bmp
    }

    /** Marca de lugar simple (sin dibujo) para elegir dónde va el punto nuevo. */
    fun placing(density: Float): Bitmap = pin(density, RED, null, 1.25f)
}
