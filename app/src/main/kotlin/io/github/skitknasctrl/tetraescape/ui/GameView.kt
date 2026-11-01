package io.github.skitknasctrl.tetraescape.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import io.github.skitknasctrl.tetraescape.engine.Cell
import io.github.skitknasctrl.tetraescape.engine.GameState
import io.github.skitknasctrl.tetraescape.engine.Level
import io.github.skitknasctrl.tetraescape.engine.Orientation
import io.github.skitknasctrl.tetraescape.engine.Status
import io.github.skitknasctrl.tetraescape.engine.Symbol
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws the board and the glass tetrahedron, animates tips and spins, and turns taps
 * on neighbouring cells into moves.
 */
class GameView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        /** Called after every completed move (animation finished). */
        fun onMoved(state: GameState)
        /** Called when the player makes the first move of a level. */
        fun onFirstMove()
    }

    var listener: Listener? = null
    var showPreview = true

    /** True while the stored solution is being played back automatically. */
    var isAutoPlaying = false
        private set
    private val autoQueue = ArrayDeque<Int>()
    private val autoNext = Runnable { autoStep() }

    var state: GameState? = null
        private set
    private val level: Level? get() = state?.level

    // ---- layout ----
    private var scale = 1f
    private var ox = 0f
    private var oy = 0f

    // ---- animation ----
    private var tipAnim: ValueAnimator? = null
    private var tipColor = -1
    private var tipT = 0f
    private var tipSign = 1f
    private var pendingState: GameState? = null
    private var spinAnim: ValueAnimator? = null
    private var spinAngle = 0f // degrees, math angle in board coords (y up)
    private var bumpT = 0f // small "fail" feedback
    private var downX = 0f
    private var downY = 0f

    // ---- paints ----
    private val cellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFAF8F3.toInt(); style = Paint.Style.FILL }
    private val holeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9D5CC.toInt(); style = Paint.Style.FILL }
    private val cellLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB9B4A9.toInt(); style = Paint.Style.STROKE }
    private val legalLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3C8D5A.toInt(); style = Paint.Style.STROKE }
    private val bonusFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDCEBFA.toInt() }
    private val bonusInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E66C8.toInt(); style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val bonusInkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E66C8.toInt(); style = Paint.Style.FILL }
    private val spinFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEADCF7.toInt() }
    private val spinInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7B3FB5.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val goalFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE082.toInt() }
    private val goalInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A6400.toInt(); style = Paint.Style.STROKE }
    private val goalInkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A6400.toInt(); style = Paint.Style.FILL }
    private val usedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE6E3DC.toInt() }
    private val usedInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB0ACA3.toInt(); style = Paint.Style.STROKE }
    private val usedInkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB0ACA3.toInt(); style = Paint.Style.FILL }
    private val startInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6F6A60.toInt(); style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E66C8.toInt(); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val previewInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x553C8D5A; style = Paint.Style.STROKE }
    private val previewInkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x553C8D5A; style = Paint.Style.FILL }
    private val faceFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x5CFFFFFF; style = Paint.Style.FILL }
    private val faceEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC5E6E7E.toInt(); style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val symInkBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF222222.toInt(); style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val symFillBottom = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF222222.toInt(); style = Paint.Style.FILL }
    private val symInkSide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x77222222; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val symFillSide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x77222222; style = Paint.Style.FILL }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000; style = Paint.Style.FILL }

    private val path = Path()

    fun setLevel(level: Level) {
        cancelAnimations()
        state = GameState.start(level)
        pendingState = null
        computeLayout()
        invalidate()
    }

    fun restart() {
        level?.let { setLevel(it) }
    }

    /** Restart the level and play its stored solution move by move. */
    fun playSolution() {
        val lv = level ?: return
        if (lv.solution.isEmpty()) return
        setLevel(lv)
        autoQueue.clear(); autoQueue.addAll(lv.solution)
        isAutoPlaying = true
        postDelayed(autoNext, 600)
    }

    private fun autoStep() {
        if (!isAutoPlaying) return
        val color = autoQueue.removeFirstOrNull()
        if (color == null || state?.status != Status.PLAYING) { isAutoPlaying = false; return }
        startTip(color)
    }

    private fun cancelAnimations() {
        tipAnim?.cancel(); tipAnim = null
        spinAnim?.cancel(); spinAnim = null
        tipColor = -1; spinAngle = 0f; bumpT = 0f
        removeCallbacks(autoNext)
        autoQueue.clear()
        isAutoPlaying = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeLayout()
    }

    private fun computeLayout() {
        val lv = level ?: return
        if (width == 0 || height == 0) return
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (c in lv.allCells) for (p in c.corners) {
            minX = min(minX, Cell.ptX(p)); maxX = max(maxX, Cell.ptX(p))
            minY = min(minY, Cell.ptY(p)); maxY = max(maxY, Cell.ptY(p))
        }
        val pad = 0.35
        val bw = (maxX - minX) + 2 * pad
        val bh = (maxY - minY) + 2 * pad
        scale = min(width / bw, height / bh).toFloat()
        ox = (width - (maxX - minX).toFloat() * scale) / 2f - minX.toFloat() * scale
        oy = height - (height - (maxY - minY).toFloat() * scale) / 2f + minY.toFloat() * scale
        val lw = scale * 0.025f
        cellLine.strokeWidth = lw
        legalLine.strokeWidth = lw * 2.6f
        bonusInk.strokeWidth = scale * 0.04f
        spinInk.strokeWidth = scale * 0.045f
        goalInk.strokeWidth = scale * 0.04f
        usedInk.strokeWidth = scale * 0.04f
        startInk.strokeWidth = scale * 0.03f
        previewInk.strokeWidth = scale * 0.04f
        faceEdge.strokeWidth = scale * 0.022f
        text.textSize = scale * 0.2f
        // symbol strokes are drawn under an affine map whose scale is ≈ `scale`
        symInkBottom.strokeWidth = 0.055f
        symInkSide.strokeWidth = 0.05f
    }

    private fun sx(x: Double) = ox + x.toFloat() * scale
    private fun sy(y: Double) = oy - y.toFloat() * scale

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        val st = state ?: return
        val lv = st.level
        if (scale <= 1f) computeLayout()

        val legal = if (st.status == Status.PLAYING && tipAnim == null) st.legalMoves() else emptyList()
        val legalCells = legal.map { st.cell.neighborAcross(it) }

        for (c in lv.allCells) drawCell(canvas, st, c, legalCells.contains(c))
        // preview of what lands where
        if (showPreview) for (color in legal) {
            val n = st.cell.neighborAcross(color)
            val face = Orientation.sideFace(st.orient, color)
            drawSymbolInCell(canvas, n, Symbol.FACE_SYMBOLS[face], previewInk, previewInkFill)
        }
        drawPiece(canvas, st)
    }

    private fun cellPath(c: Cell): Path {
        path.reset()
        c.corners.forEachIndexed { k, p ->
            if (k == 0) path.moveTo(sx(Cell.ptX(p)), sy(Cell.ptY(p))) else path.lineTo(sx(Cell.ptX(p)), sy(Cell.ptY(p)))
        }
        path.close()
        return path
    }

    private fun drawCell(canvas: Canvas, st: GameState, c: Cell, legal: Boolean) {
        val lv = st.level
        val sp = lv.specials[c]
        val p = cellPath(c)
        if (c in lv.holes) {
            canvas.drawPath(p, holeFill)
            return
        }
        val collected = sp != null && sp.bonus > 0 && st.isCollected(c)
        val fill = when {
            c == lv.goal -> goalFill
            collected -> usedFill
            sp != null && sp.bonus > 0 -> bonusFill
            sp != null && sp.spin != 0 -> spinFill
            else -> cellFill
        }
        canvas.drawPath(p, fill)
        canvas.drawPath(p, cellLine)
        if (legal) canvas.drawPath(p, legalLine)

        val (cx, cy) = c.center()
        val scx = sx(cx); val scy = sy(cy)
        val towardWide = if (c.up) -1f else 1f // screen-y direction towards the wide side of the triangle

        if (c == lv.start) {
            canvas.drawCircle(scx, scy, scale * 0.09f, startInk)
        }
        if (c == lv.goal) {
            val req = sp?.require
            if (req != null) {
                drawSymbolInCell(canvas, c, req, goalInk, goalInkFill)
            } else {
                canvas.drawCircle(scx, scy, scale * 0.17f, goalInk)
                canvas.drawCircle(scx, scy, scale * 0.07f, goalInkFill)
            }
            return
        }
        if (sp == null) return
        if (sp.bonus > 0) {
            val ink = if (collected) usedInk else bonusInk
            val inkFill = if (collected) usedInkFill else bonusInkFill
            // symbol in the upper (apex) part, the number near the wide base – no overlap
            sp.require?.let { drawSymbolInCell(canvas, c, it, ink, inkFill, shift = 0.14f, size = 0.78f) }
            val pt = floatArrayOf(SymbolPaths.CX, 0.13f)
            cellMatrix(c).mapPoints(pt)
            text.color = ink.color
            text.textSize = scale * 0.17f
            canvas.drawText("+${sp.bonus}", pt[0], pt[1] + text.textSize * 0.36f, text)
        }
        if (sp.spin != 0) {
            drawSpinArrow(canvas, scx, scy, sp.spin > 0, if (sp.bonus > 0) scale * 0.13f else scale * 0.2f, towardWide, sp.bonus > 0)
        }
    }

    /** Circular arrow around the centroid; clockwise on screen when [cw]. */
    private fun drawSpinArrow(canvas: Canvas, cx: Float, cy: Float, cw: Boolean, r: Float, towardWide: Float, offset: Boolean) {
        val cyy = if (offset) cy - towardWide * scale * 0.16f else cy
        val rect = RectF(cx - r, cyy - r, cx + r, cyy + r)
        // Android sweeps clockwise on screen for positive angles.
        val startDeg = if (cw) -150f else -30f
        val sweep = if (cw) 270f else -270f
        canvas.drawArc(rect, startDeg, sweep, false, spinInk)
        val endDeg = Math.toRadians((startDeg + sweep).toDouble())
        val ex = cx + r * cos(endDeg).toFloat(); val ey = cyy + r * sin(endDeg).toFloat()
        // tangent direction of travel at the end point
        val tx = -sin(endDeg).toFloat() * (if (cw) 1f else -1f)
        val ty = cos(endDeg).toFloat() * (if (cw) 1f else -1f)
        val a = r * 0.55f
        val nx = -ty; val ny = tx
        path.reset()
        path.moveTo(ex + tx * a, ey + ty * a)
        path.lineTo(ex + nx * a * 0.6f, ey + ny * a * 0.6f)
        path.lineTo(ex - nx * a * 0.6f, ey - ny * a * 0.6f)
        path.close()
        val fillP = Paint(spinInk).apply { style = Paint.Style.FILL }
        canvas.drawPath(path, fillP)
    }

    /** Matrix from the canonical face triangle onto this cell on screen. */
    private fun cellMatrix(c: Cell): Matrix {
        val pts = c.corners
        return SymbolPaths.faceMatrix(
            sx(Cell.ptX(pts[0])), sy(Cell.ptY(pts[0])),
            sx(Cell.ptX(pts[1])), sy(Cell.ptY(pts[1])),
            sx(Cell.ptX(pts[2])), sy(Cell.ptY(pts[2])),
        )
    }

    /**
     * Draw a symbol in a cell. [shift] moves it from the centroid towards the cell's apex
     * (canonical units), [size] scales it about its own centre.
     */
    private fun drawSymbolInCell(canvas: Canvas, c: Cell, s: Symbol, ink: Paint, inkFill: Paint, shift: Float = 0f, size: Float = 1f) {
        val m = cellMatrix(c)
        canvas.save()
        canvas.concat(m)
        if (shift != 0f || size != 1f) {
            canvas.translate(0f, shift)
            canvas.scale(size, size, SymbolPaths.CX, SymbolPaths.CY)
        }
        val strokeW = ink.strokeWidth
        ink.strokeWidth = strokeW / scale
        SymbolPaths.stroke[s]?.let { canvas.drawPath(it, ink) }
        SymbolPaths.fill[s]?.let { canvas.drawPath(it, inkFill) }
        ink.strokeWidth = strokeW
        canvas.restore()
    }

    // ------------------------------------------------------------------ the piece

    /** 3D vertex positions (board units, z up) of tetrahedron vertices 0..3 resting on [cell]. */
    private fun restVertices(cell: Cell, orient: Int): Array<DoubleArray> {
        val out = Array(4) { DoubleArray(3) }
        val (cx, cy) = cell.center()
        for (u in 0..3) {
            if (u == Orientation.apex(orient)) {
                out[u][0] = cx; out[u][1] = cy; out[u][2] = APEX_H
            } else {
                val color = (0..2).first { Orientation.vertexAtColor(orient, it) == u }
                val p = cell.cornerOfColor(color)
                out[u][0] = Cell.ptX(p); out[u][1] = Cell.ptY(p); out[u][2] = 0.0
            }
        }
        return out
    }

    private fun rotateAboutEdge(v: DoubleArray, a: DoubleArray, u: DoubleArray, phi: Double): DoubleArray {
        val px = v[0] - a[0]; val py = v[1] - a[1]; val pz = v[2] - a[2]
        val c = cos(phi); val s = sin(phi)
        val dot = u[0] * px + u[1] * py + u[2] * pz
        val cx = u[1] * pz - u[2] * py
        val cy = u[2] * px - u[0] * pz
        val cz = u[0] * py - u[1] * px
        return doubleArrayOf(
            a[0] + px * c + cx * s + u[0] * dot * (1 - c),
            a[1] + py * c + cy * s + u[1] * dot * (1 - c),
            a[2] + pz * c + cz * s + u[2] * dot * (1 - c),
        )
    }

    private fun drawPiece(canvas: Canvas, st: GameState) {
        val cell = st.cell
        var verts = restVertices(cell, st.orient)
        var (pcx, pcy) = cell.center()

        if (tipColor >= 0) {
            // rotate about the edge opposite tipColor
            val a = cell.cornerOfColor((tipColor + 1) % 3)
            val b = cell.cornerOfColor((tipColor + 2) % 3)
            val ax = doubleArrayOf(Cell.ptX(a), Cell.ptY(a), 0.0)
            var ux = Cell.ptX(b) - ax[0]; var uy = Cell.ptY(b) - ax[1]
            val len = hypot(ux, uy); ux /= len; uy /= len
            val u = doubleArrayOf(ux, uy, 0.0)
            val phi = tipSign * TIP_ANGLE * tipT
            verts = Array(4) { rotateAboutEdge(verts[it], ax, u, phi) }
            val (ncx, ncy) = cell.neighborAcross(tipColor).center()
            pcx = pcx + (ncx - pcx) * tipT; pcy = pcy + (ncy - pcy) * tipT
        } else if (spinAngle != 0f) {
            val ang = Math.toRadians(spinAngle.toDouble())
            val c = cos(ang); val s = sin(ang)
            verts = Array(4) {
                val dx = verts[it][0] - pcx; val dy = verts[it][1] - pcy
                doubleArrayOf(pcx + dx * c - dy * s, pcy + dx * s + dy * c, verts[it][2])
            }
        }

        // soft shadow under the piece
        val lift = verts.maxOf { it[2] } / APEX_H
        canvas.drawCircle(sx(pcx + 0.05), sy(pcy - 0.05), scale * (0.36f + 0.06f * lift.toFloat()), shadow)

        // project with a little perspective (higher = pushed outward from the piece centre)
        val proj = Array(4) { FloatArray(2) }
        for (u in 0..3) {
            val k = 1.0 + PERSPECTIVE * verts[u][2]
            val x = pcx + (verts[u][0] - pcx) * k
            val y = pcy + (verts[u][1] - pcy) * k
            proj[u][0] = sx(x); proj[u][1] = sy(y)
        }

        // faces, farthest (lowest) first
        val faces = (0..3).sortedBy { f -> (0..3).filter { it != f }.sumOf { verts[it][2] } }
        for (f in faces) {
            val vs = (0..3).filter { it != f }
            val avgZ = vs.sumOf { verts[it][2] } / 3.0
            val isBottom = avgZ < 0.02
            path.reset()
            vs.forEachIndexed { k, u -> if (k == 0) path.moveTo(proj[u][0], proj[u][1]) else path.lineTo(proj[u][0], proj[u][1]) }
            path.close()
            if (!isBottom) {
                val shade = (0x5C + (avgZ / APEX_H * 0x40).toInt()).coerceIn(0, 0xB0)
                faceFill.color = (shade shl 24) or 0xFFFFFF
                canvas.drawPath(path, faceFill)
            }
            // symbol of this face
            val m = SymbolPaths.faceMatrix(
                proj[vs[0]][0], proj[vs[0]][1], proj[vs[1]][0], proj[vs[1]][1], proj[vs[2]][0], proj[vs[2]][1],
            )
            canvas.save()
            canvas.concat(m)
            val sym = Symbol.FACE_SYMBOLS[f]
            val ink = if (isBottom) symInkBottom else symInkSide
            val fill = if (isBottom) symFillBottom else symFillSide
            SymbolPaths.stroke[sym]?.let { canvas.drawPath(it, ink) }
            SymbolPaths.fill[sym]?.let { canvas.drawPath(it, fill) }
            canvas.restore()
            canvas.drawPath(path, faceEdge)
        }
    }

    // ------------------------------------------------------------------ input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; return true }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) > scale * 0.5f || abs(event.y - downY) > scale * 0.5f) return true
                handleTap(event.x, event.y)
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun handleTap(x: Float, y: Float) {
        val st = state ?: return
        if (st.status != Status.PLAYING || tipAnim != null || spinAnim != null || isAutoPlaying) return
        val bx = (x - ox) / scale
        val by = (oy - y) / scale
        for (color in st.legalMoves()) {
            val n = st.cell.neighborAcross(color)
            if (insideCell(n, bx.toDouble(), by.toDouble())) { startTip(color); return }
        }
    }

    private fun insideCell(c: Cell, x: Double, y: Double): Boolean {
        val p = c.corners.map { Cell.ptX(it) to Cell.ptY(it) }
        fun side(ax: Double, ay: Double, bx: Double, by: Double) = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
        val d1 = side(p[0].first, p[0].second, p[1].first, p[1].second)
        val d2 = side(p[1].first, p[1].second, p[2].first, p[2].second)
        val d3 = side(p[2].first, p[2].second, p[0].first, p[0].second)
        val neg = d1 < 0 || d2 < 0 || d3 < 0
        val pos = d1 > 0 || d2 > 0 || d3 > 0
        return !(neg && pos)
    }

    /** Programmatic move (used by tests / future hint feature). */
    fun startTip(color: Int) {
        val st = state ?: return
        val next = st.move(color) ?: return
        if (st.moves == 0) listener?.onFirstMove()
        pendingState = next
        tipColor = color
        tipT = 0f
        // pick the rotation sign that brings the apex down onto the new corner
        val target = next.cell.cornerOfColor(color)
        val tx = Cell.ptX(target); val ty = Cell.ptY(target)
        val a = st.cell.cornerOfColor((color + 1) % 3)
        val b = st.cell.cornerOfColor((color + 2) % 3)
        val ax = doubleArrayOf(Cell.ptX(a), Cell.ptY(a), 0.0)
        var ux = Cell.ptX(b) - ax[0]; var uy = Cell.ptY(b) - ax[1]
        val len = hypot(ux, uy); ux /= len; uy /= len
        val u = doubleArrayOf(ux, uy, 0.0)
        val apex = restVertices(st.cell, st.orient)[Orientation.apex(st.orient)]
        val plus = rotateAboutEdge(apex, ax, u, TIP_ANGLE)
        val minus = rotateAboutEdge(apex, ax, u, -TIP_ANGLE)
        tipSign = if (hypot(plus[0] - tx, plus[1] - ty) < hypot(minus[0] - tx, minus[1] - ty)) 1f else -1f

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { tipT = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { finishTip() }
            })
        }
        tipAnim = anim
        anim.start()
    }

    private fun finishTip() {
        tipAnim = null
        tipColor = -1
        val next = pendingState ?: return
        pendingState = null
        state = next
        invalidate()
        val spun = next.lastEffect.spun
        if (spun != 0) {
            spinAngle = 120f * spun
            val anim = ValueAnimator.ofFloat(spinAngle, 0f).apply {
                duration = 320
                interpolator = DecelerateInterpolator()
                addUpdateListener { spinAngle = it.animatedValue as Float; invalidate() }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        spinAnim = null; spinAngle = 0f; invalidate()
                        afterMove(next)
                    }
                })
            }
            spinAnim = anim
            anim.start()
        } else {
            afterMove(next)
        }
    }

    private fun afterMove(next: GameState) {
        listener?.onMoved(next)
        if (isAutoPlaying) {
            if (autoQueue.isEmpty() || next.status != Status.PLAYING) isAutoPlaying = false
            else postDelayed(autoNext, 450)
        }
    }

    companion object {
        /** Height of a regular tetrahedron with unit edge. */
        private val APEX_H = sqrt(2.0 / 3.0)
        /** Angle the piece turns when tipping over an edge: 180° − dihedral angle. */
        private val TIP_ANGLE = Math.PI - acos(1.0 / 3.0)
        private const val PERSPECTIVE = 0.22
    }
}
