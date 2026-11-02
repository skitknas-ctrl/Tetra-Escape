package io.github.skitknasctrl.tetraescape.engine

/**
 * Exhaustive breadth-first solver over (cell, orientation, keys taken).
 * Used by the generator to guarantee that every level can be finished and to find par.
 */
object Solver {

    data class Path(val moves: List<Int>, val cells: List<Cell>)

    /** Fewest-moves solution, or null if the level cannot be finished. */
    fun shortestSolution(level: Level, ignoreRequire: Boolean = false, maxMoves: Int = 400): Path? {
        val cells = level.allCells
        val idx = HashMap<Cell, Int>().also { m -> cells.forEachIndexed { i, c -> m[c] = i } }
        val nKeys = level.keyCount + 1
        fun key(ci: Int, orient: Int, keys: Int): Int = (ci * 256 + orient) * nKeys + keys
        val size = cells.size * 256 * nKeys
        val parent = IntArray(size) { -1 }
        val parentColor = ByteArray(size)
        val startK = key(idx[level.start]!!, level.startOrientation, 0)
        parent[startK] = startK
        var frontier = ArrayList<Int>().apply { add(startK) }
        var depth = 0
        while (frontier.isNotEmpty() && depth < maxMoves) {
            depth++
            val next = ArrayList<Int>()
            for (k in frontier) {
                val keys = k % nKeys
                val rest = k / nKeys
                val orient = rest % 256
                val cell = cells[rest / 256]
                for (color in 0..2) {
                    val n = cell.neighborAcross(color)
                    if (!level.isOpen(n)) continue
                    val o = Orientation.tip(orient, color)
                    val l = Rules.land(level, n, o, keys, ignoreRequire)
                    val nk = key(idx[l.cell]!!, l.orient, l.keys)
                    if (parent[nk] != -1) continue
                    parent[nk] = k; parentColor[nk] = color.toByte()
                    if (l.won) return reconstruct(nk, startK, parent, parentColor, cells, nKeys)
                    next.add(nk)
                }
            }
            frontier = next
        }
        return null
    }

    private fun reconstruct(goalK: Int, startK: Int, parent: IntArray, parentColor: ByteArray, cells: List<Cell>, nKeys: Int): Path {
        val colors = ArrayList<Int>()
        val path = ArrayList<Cell>()
        var cur = goalK
        while (cur != startK) {
            colors.add(parentColor[cur].toInt())
            path.add(cells[(cur / nKeys) / 256])
            cur = parent[cur]
        }
        colors.reverse(); path.reverse()
        return Path(colors, path)
    }
}
