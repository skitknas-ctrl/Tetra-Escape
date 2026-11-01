package io.github.skitknasctrl.tetraescape.engine

/**
 * A lattice point of the triangular grid.
 *
 * Geometry (board coordinates, y pointing UP):
 *   x = (i + j / 2) * side,   y = j * height,   height = side * sqrt(3) / 2
 *
 * Every lattice point has a colour 0..2 such that the three corners of any
 * triangle have three different colours. That colour is what the tetrahedron
 * logic is built on: the corner opposite the edge you tip over has the same
 * colour in both the old and the new cell.
 */
data class Pt(val i: Int, val j: Int) {
    val color: Int get() = Math.floorMod(i - j, 3)
}

/**
 * One triangular cell of the board.
 *
 * Up cell  (i, j): corners (i, j), (i+1, j), (i, j+1)   — points up
 * Down cell(i, j): corners (i+1, j), (i+1, j+1), (i, j+1) — points down
 */
data class Cell(val i: Int, val j: Int, val up: Boolean) {

    /** The three corners, as lattice points. */
    val corners: List<Pt>
        get() = if (up) listOf(Pt(i, j), Pt(i + 1, j), Pt(i, j + 1))
        else listOf(Pt(i + 1, j), Pt(i + 1, j + 1), Pt(i, j + 1))

    /** The corner that has the given colour. */
    fun cornerOfColor(color: Int): Pt = corners.first { it.color == color }

    /**
     * The neighbouring cell across the edge opposite the corner with the given colour.
     * The opposite corner in that neighbour has the same colour.
     */
    fun neighborAcross(color: Int): Cell {
        val c0 = Pt(i, j).color
        val k = Math.floorMod(color - c0, 3) // 0,1,2 relative colour
        return if (up) {
            when (k) {
                0 -> Cell(i, j, false)       // opposite (i, j)
                1 -> Cell(i - 1, j, false)   // opposite (i+1, j)
                else -> Cell(i, j - 1, false) // opposite (i, j+1)
            }
        } else {
            when (k) {
                1 -> Cell(i, j + 1, true)    // opposite (i+1, j)
                0 -> Cell(i, j, true)        // opposite (i+1, j+1)
                else -> Cell(i + 1, j, true) // opposite (i, j+1)
            }
        }
    }

    /** All three neighbours, paired with the colour of the edge's opposite corner. */
    fun neighbors(): List<Pair<Int, Cell>> = (0..2).map { c -> c to neighborAcross(c) }

    /** Centre of the cell in board coordinates (y up), for a unit side length. */
    fun center(): Pair<Double, Double> {
        val pts = corners
        val x = pts.sumOf { it.i + it.j / 2.0 } / 3.0
        val y = pts.sumOf { it.j * H } / 3.0
        return x to y
    }

    companion object {
        /** Row height for side length 1. */
        const val H = 0.8660254037844386

        fun ptX(p: Pt): Double = p.i + p.j / 2.0
        fun ptY(p: Pt): Double = p.j * H
    }
}
