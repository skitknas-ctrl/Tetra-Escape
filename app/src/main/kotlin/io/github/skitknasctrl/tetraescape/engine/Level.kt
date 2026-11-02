package io.github.skitknasctrl.tetraescape.engine

/**
 * What a special cell does when the piece lands on it. A cell has at most one role.
 *
 * - [key]: key number 1..n. Keys must be taken in order, and only when the piece lands
 *   with [require] on the board. The goal opens when every key has been taken.
 * - [spin]: +1 rotates the piece clockwise, -1 counter-clockwise, every time you land here.
 * - [portal]: pair id 1..n. Landing here moves the piece instantly to the other cell with the
 *   same id, orientation unchanged.
 * - [require] on the goal cell: the symbol that must lie on the board to finish.
 */
data class Special(
    val key: Int = 0,
    val spin: Int = 0,
    val portal: Int = 0,
    val require: Symbol? = null,
)

/**
 * A level: a board of triangles with a start, a goal and special cells. There are no
 * blocked cells and no step or time limit; the challenge is to finish in as few
 * moves as possible. [par] is the fewest moves possible, found by the solver.
 *
 * Rows are 0 until [rows]; in row j the cells have i in i0(j) until i0(j)+cols,
 * which gives a rectangle-ish board with a zig-zag left/right edge.
 */
class Level(
    val rows: Int,
    val cols: Int,
    val start: Cell,
    val goal: Cell,
    val specials: Map<Cell, Special>,
    val seed: Long,
    val difficulty: Int,
    val startOrientation: Int = Orientation.INITIAL,
    /** One optimal solution, as the colours of the edges tipped over. */
    val solution: List<Int> = emptyList(),
) {
    val par: Int get() = solution.size

    fun i0(j: Int): Int = -(j / 2)

    fun inBounds(c: Cell): Boolean =
        c.j in 0 until rows && c.i >= i0(c.j) && c.i < i0(c.j) + cols

    /** A cell you may stand on (every cell on the board). */
    fun isOpen(c: Cell): Boolean = inBounds(c)

    /** All cells in a stable order. */
    val allCells: List<Cell> by lazy {
        val list = ArrayList<Cell>(rows * cols * 2)
        for (j in 0 until rows) for (i in i0(j) until i0(j) + cols) {
            list.add(Cell(i, j, true)); list.add(Cell(i, j, false))
        }
        list
    }

    val openCells: List<Cell> get() = allCells

    /** Number of keys on the board. */
    val keyCount: Int by lazy { specials.values.maxOfOrNull { it.key } ?: 0 }

    fun keyCell(n: Int): Cell? = specials.entries.firstOrNull { it.value.key == n }?.key

    /** The other end of a portal. */
    fun portalPartner(c: Cell): Cell? {
        val id = specials[c]?.portal ?: return null
        if (id == 0) return null
        return specials.entries.firstOrNull { it.key != c && it.value.portal == id }?.key
    }

    fun goalRequirement(): Symbol? = specials[goal]?.require
}
