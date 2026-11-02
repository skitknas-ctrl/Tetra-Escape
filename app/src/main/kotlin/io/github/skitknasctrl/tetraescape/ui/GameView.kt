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
import io.github.skitknasctrl.tetraescape.engine.Pt
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
    // board look: cream tiles, gold grid with a gold bead at every vertex,
    // coloured glass for special tiles
    private val cellFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF8F3E8.toInt(); style = Paint.Style.FILL }
    private val cellInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF; style = Paint.Style.STROKE }
    private val cellLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC49A3C.toInt(); style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val cellLineHi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFE9A8.toInt(); style = Paint.Style.STROKE }
    private val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD4AA48.toInt(); style = Paint.Style.FILL }
    private val dotHi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFF2C2.toInt(); style = Paint.Style.FILL }
    private val dotEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8E6A1E.toInt(); style = Paint.Style.STROKE }
    private val boardShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x16000000; style = Paint.Style.FILL }
    private val legalLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E9E66.toInt(); style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val legalTint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2E3CC07A; style = Paint.Style.FILL }
    private val keyFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD27A.toInt() }
    private val keyInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A4B00.toInt(); style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val keyInkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A4B00.toInt(); style = Paint.Style.FILL }
    private val keyBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8A4B00.toInt(); style = Paint.Style.FILL }
    private val keyBadgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF4D6.toInt(); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val portalFill1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA6E6E3.toInt() }
    private val portalInk1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E7C78.toInt(); style = Paint.Style.STROKE }
    private val portalFill2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC6A8.toInt() }
    private val portalInk2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB4461A.toInt(); style = Paint.Style.STROKE }
    private val lockedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD9CFB8.toInt() }
    private val lockedInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6E5A30.toInt(); style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val lockedInkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6E5A30.toInt(); style = Paint.Style.FILL }
    private val spinFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDDB9F5.toInt() }
    private val spinInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6E2FB0.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val goalFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5C844.toInt() }
    private val goalInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7A5600.toInt(); style = Paint.Style.STROKE }
    private val goalInkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7A5600.toInt(); style = Paint.Style.FILL }
    private val usedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE4E0D6.toInt() }
    private val usedInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFADA89E.toInt(); style = Paint.Style.STROKE }
    private val usedInkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFADA89E.toInt(); style = Paint.Style.FILL }
    private val usedBadge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFADA89E.toInt(); style = Paint.Style.FILL }
    private val glassHi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF; style = Paint.Style.FILL }
    private val startInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8E6A1E.toInt(); style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B5FC2.toInt(); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val previewInk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x662E9E66; style = Paint.Style.STROKE }
    private val previewInkFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x662E9E66; style = Paint.Style.FILL }
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
        val lw = scale * 0.032f
        cellLine.strokeWidth = lw
        cellLineHi.strokeWidth = lw * 0.35f
        cellInner.strokeWidth = scale * 0.05f
        dotEdge.strokeWidth = scale * 0.012f
        legalLine.strokeWidth = lw * 2.2f
        keyInk.strokeWidth = scale * 0.04f
        portalInk1.strokeWidth = scale * 0.05f
        portalInk2.strokeWidth = scale * 0.05f
        lockedInk.strokeWidth = scale * 0.04f
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

        drawBoardShadow(canvas, lv)
        for (c in lv.allCells) drawCell(canvas, st, c, legalCells.contains(c))
        drawGrid(canvas, lv)
        for (c in legalCells) { canvas.drawPath(cellPath(c), legalLine) }
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

    /** Soft shadow of the whole board, offset down-right. */
    private fun drawBoardShadow(canvas: Canvas, lv: Level) {
        val union = Path()
        union.fillType = Path.FillType.WINDING
        for (c in lv.allCells) {
            c.corners.forEachIndexed { k, p ->
                if (k == 0) union.moveTo(sx(Cell.ptX(p)), sy(Cell.ptY(p))) else union.lineTo(sx(Cell.ptX(p)), sy(Cell.ptY(p)))
            }
            union.close()
        }
        for (i in 1..3) {
            canvas.save()
            canvas.translate(scale * 0.03f * i, scale * 0.045f * i)
            canvas.drawPath(union, boardShadow)
            canvas.restore()
        }
    }

    /** Gold grid lines over every edge and a gold bead on every vertex. */
    private fun drawGrid(canvas: Canvas, lv: Level) {
        val edges = HashSet<Long>()
        val verts = HashSet<Pt>()
        val lines = Path()
        for (c in lv.allCells) {
            val pts = c.corners
            for (k in 0..2) {
                val a = pts[k]; val b = pts[(k + 1) % 3]
                verts.add(a)
                val key = edgeKey(a, b)
                if (edges.add(key)) {
                    lines.moveTo(sx(Cell.ptX(a)), sy(Cell.ptY(a)))
                    lines.lineTo(sx(Cell.ptX(b)), sy(Cell.ptY(b)))
                }
            }
        }
        canvas.drawPath(lines, cellLine)
        canvas.drawPath(lines, cellLineHi)
        val r = scale * 0.055f
        for (v in verts) {
            val x = sx(Cell.ptX(v)); val y = sy(Cell.ptY(v))
            canvas.drawCircle(x, y, r, dotFill)
            canvas.drawCircle(x, y, r, dotEdge)
            canvas.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.4f, dotHi)
        }
    }

    private fun edgeKey(a: Pt, b: Pt): Long {
        val (p, q) = if (a.i < b.i || (a.i == b.i && a.j < b.j)) a to b else b to a
        return (((p.i + 1000L) * 4096 + (p.j + 1000L)) * 4096 + (q.i + 1000L)) * 4096 + (q.j + 1000L)
    }

    private fun drawCell(canvas: Canvas, st: GameState, c: Cell, legal: Boolean) {
        val lv = st.level
        val sp = lv.specials[c]
        val p = cellPath(c)
        val taken = sp != null && sp.key > 0 && st.isKeyTaken(c)
        val isNextKey = sp != null && sp.key == st.keys + 1
        val special = c == lv.goal || sp != null
        val fill = when {
            c == lv.goal -> if (st.goalOpen) goalFill else lockedFill
            taken -> usedFill
            sp != null && sp.key > 0 -> keyFill
            sp != null && sp.spin != 0 -> spinFill
            sp != null && sp.portal == 1 -> portalFill1
            sp != null && sp.portal >= 2 -> portalFill2
            else -> cellFill
        }
        canvas.drawPath(p, fill)
        if (special && !taken) {
            // glass highlight: a lighter wedge in the apex half of the tile
            canvas.save(); canvas.clipPath(p)
            val (gx, gy) = c.center()
            val apex = c.corners.first { pt -> if (c.up) pt.j == c.j + 1 else pt.j == c.j }
            canvas.drawCircle(sx((Cell.ptX(apex) + gx) / 2), sy((Cell.ptY(apex) + gy) / 2), scale * 0.28f, glassHi)
            canvas.restore()
        }
        if (legal) canvas.drawPath(p, legalTint)

        val (cx, cy) = c.center()
        val scx = sx(cx); val scy = sy(cy)
        val towardWide = if (c.up) -1f else 1f // screen-y direction towards the wide side of the triangle

        if (c == lv.start) {
            canvas.drawCircle(scx, scy, scale * 0.09f, startInk)
        }
        if (c == lv.goal) {
            val req = sp?.require
            val ink = if (st.goalOpen) goalInk else lockedInk
            val inkFill = if (st.goalOpen) goalInkFill else lockedInkFill
            if (req != null) {
                drawSymbolInCell(canvas, c, req, ink, inkFill, shift = if (st.goalOpen) 0f else 0.12f, size = if (st.goalOpen) 1f else 0.8f)
            } else {
                canvas.drawCircle(scx, scy, scale * 0.17f, ink)
                canvas.drawCircle(scx, scy, scale * 0.07f, inkFill)
            }
            if (!st.goalOpen) drawLock(canvas, c, inkFill)
            return
        }
        if (sp == null) return
        if (sp.key > 0) {
            val ink = if (taken) usedInk else keyInk
            val inkFill = if (taken) usedInkFill else keyInkFill
            // symbol in the apex part, the key number in a badge near the wide base
            sp.require?.let { drawSymbolInCell(canvas, c, it, ink, inkFill, shift = 0.14f, size = 0.78f) }
            val pt = floatArrayOf(SymbolPaths.CX, 0.15f)
            cellMatrix(c).mapPoints(pt)
            val r = scale * 0.11f
            canvas.drawCircle(pt[0], pt[1], r, if (taken) usedBadge else keyBadge)
            if (isNextKey) canvas.drawCircle(pt[0], pt[1], r + scale * 0.025f, keyInk)
            keyBadgeText.textSize = r * 1.5f
            canvas.drawText(if (taken) "✓" else sp.key.toString(), pt[0], pt[1] + keyBadgeText.textSize * 0.36f, keyBadgeText)
        }
        if (sp.portal > 0) {
            val ink = if (sp.portal == 1) portalInk1 else portalInk2
            canvas.drawCircle(scx, scy, scale * 0.2f, ink)
            canvas.drawCircle(scx, scy, scale * 0.11f, ink)
            canvas.drawCircle(scx, scy, scale * 0.035f, Paint(ink).apply { style = Paint.Style.FILL })
        }
        if (sp.spin != 0) {
            drawSpinArrow(canvas, scx, scy, sp.spin > 0, scale * 0.2f, towardWide, false)
        }
    }

    /** Small padlock near the wide base of the goal cell while keys are missing. */
    private fun drawLock(canvas: Canvas, c: Cell, ink: Paint) {
        val pt = floatArrayOf(SymbolPaths.CX, 0.16f)
        cellMatrix(c).mapPoints(pt)
        val w = scale * 0.16f; val h = scale * 0.12f
        val body = RectF(pt[0] - w / 2, pt[1] - h / 2, pt[0] + w / 2, pt[1] + h / 2)
        canvas.drawRoundRect(body, scale * 0.02f, scale * 0.02f, ink)
        val sh = Paint(ink).apply { style = Paint.Style.STROKE; strokeWidth = scale * 0.03f }
        val r = w * 0.3f
        canvas.drawArc(RectF(pt[0] - r, pt[1] - h / 2 - r * 1.6f, pt[0] + r, pt[1] - h / 2 + r * 0.4f), 180f, 180f, false, sh)
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
        pendingState = next
        tipColor = color
        tipT = 0f
        // pick the rotation sign that brings the apex down onto the new corner
        val target = st.cell.neighborAcross(color).cornerOfColor(color)
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
