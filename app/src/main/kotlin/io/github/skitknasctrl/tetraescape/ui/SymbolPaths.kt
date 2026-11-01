package io.github.skitknasctrl.tetraescape.ui

import android.graphics.Matrix
import android.graphics.Path
import io.github.skitknasctrl.tetraescape.engine.Symbol
import kotlin.math.cos
import kotlin.math.sin

/**
 * Symbol shapes drawn in "face coordinates": an equilateral triangle with corners
 * (0,0), (1,0), (0.5, H). The shapes are centred on the triangle's centroid, so a
 * single affine map (3 points → 3 points) places a symbol on any drawn triangle,
 * on the board or on a face of the tipping piece.
 */
object SymbolPaths {
    const val H = 0.8660254f
    const val CX = 0.5f
    const val CY = H / 3f

    /** Outline shapes (to be stroked). */
    val stroke: Map<Symbol, Path> = mapOf(
        Symbol.TRIANGLE to triangle(0.20f),
        Symbol.CIRCLE to circle(0.17f),
    )

    /** Filled shapes. */
    val fill: Map<Symbol, Path> = mapOf(
        Symbol.STAR to star(0.24f, 0.045f),
        Symbol.DOT to circle(0.11f),
    )

    private fun triangle(r: Float): Path = Path().apply {
        // same way up as the face it sits on
        val a = floatArrayOf(90f, 210f, 330f)
        a.forEachIndexed { k, deg ->
            val x = CX + r * cos(Math.toRadians(deg.toDouble())).toFloat()
            val y = CY + r * sin(Math.toRadians(deg.toDouble())).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun circle(r: Float): Path = Path().apply { addCircle(CX, CY, r, Path.Direction.CW) }

    /** Three-pointed star: three slim spikes, 120° apart. */
    private fun star(len: Float, halfWidth: Float): Path = Path().apply {
        for (k in 0..2) {
            val ang = Math.toRadians(90.0 + 120.0 * k)
            val dx = cos(ang).toFloat(); val dy = sin(ang).toFloat()
            val nx = -dy; val ny = dx
            moveTo(CX + nx * halfWidth, CY + ny * halfWidth)
            lineTo(CX + dx * len, CY + dy * len)
            lineTo(CX - nx * halfWidth, CY - ny * halfWidth)
            close()
        }
        addCircle(CX, CY, halfWidth * 1.6f, Path.Direction.CW)
    }

    /** Matrix mapping the canonical face triangle onto the three given screen points. */
    fun faceMatrix(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float): Matrix {
        val m = Matrix()
        m.setPolyToPoly(floatArrayOf(0f, 0f, 1f, 0f, CX, H), 0, floatArrayOf(x0, y0, x1, y1, x2, y2), 0, 3)
        return m
    }
}
