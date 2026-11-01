package io.github.skitknasctrl.tetraescape.engine

/**
 * What a special cell does when the piece lands on it.
 *
 * - [require]: if set, the effects only happen when this symbol is the one lying on the board.
 *   On the goal cell it means: you must arrive with this symbol down to finish.
 * - [bonus]: extra steps, granted once per level.
 * - [spin]: +1 rotates the piece clockwise, -1 counter-clockwise, every time you land here.
 */
data class Special(
    val bonus: Int = 0,
    val spin: Int = 0,
    val require: Symbol? = null,
) {
    val isEmpty: Boolean get() = bonus == 0 && spin == 0 && require == null
}

/**
 * A level: a board of triangles with holes, a start, a goal and special cells.
 *
 * Rows are 0 until [rows]; in row j the up-cells have i in i0(j) until i0(j)+cols,
 * and the down-cells the same i range, which gives a rectangle-ish board with a
 * zig-zag left/right edge.
 */
class Level(
    val rows: Int,
    val cols: Int,
    val holes: Set<Cell>,
    val start: Cell,
    val goal: Cell,
    val specials: Map<Cell, Special>,
    /** Initial step budget. */
    val steps: Int,
    /** Time limit in seconds. */
    val timeSeconds: Int,
    val seed: Long,
    val difficulty: Int,
    val startOrientation: Int = Orientation.INITIAL,
    /** One known solution, as the colours of the edges tipped over. May be empty. */
    val solution: List<Int> = emptyList(),
) {
    fun i0(j: Int): Int = -(j / 2)

    fun inBounds(c: Cell): Boolean =
        c.j in 0 until rows && c.i >= i0(c.j) && c.i < i0(c.j) + cols

    /** A cell you may stand on. */
    fun isOpen(c: Cell): Boolean = inBounds(c) && c !in holes

    /** All in-bounds cells, holes included, in a stable order. */
    val allCells: List<Cell> by lazy {
        val list = ArrayList<Cell>(rows * cols * 2)
        for (j in 0 until rows) for (i in i0(j) until i0(j) + cols) {
            list.add(Cell(i, j, true)); list.add(Cell(i, j, false))
        }
        list
    }

    val openCells: List<Cell> by lazy { allCells.filter { it !in holes } }

    /** Bonus cells in a stable order (index = bit in the "collected" mask). */
    val bonusCells: List<Cell> by lazy {
        allCells.filter { (specials[it]?.bonus ?: 0) > 0 }
    }

    fun bonusIndex(c: Cell): Int = bonusCells.indexOf(c)

    fun goalRequirement(): Symbol? = specials[goal]?.require

    /** Board width/height in side-length units (for layout). */
    val widthUnits: Double get() = cols + 0.5
    val heightUnits: Double get() = rows * Cell.H
}
