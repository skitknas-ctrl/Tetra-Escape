package io.github.skitknasctrl.tetraescape.engine

import java.util.PriorityQueue

/**
 * Exhaustive solver. Used by the generator to guarantee that every level is
 * solvable, that the bonus steps are really needed, and to measure difficulty.
 */
object Solver {

    class Index(level: Level) {
        val cells: List<Cell> = level.openCells
        private val idx = HashMap<Cell, Int>().also { m -> cells.forEachIndexed { i, c -> m[c] = i } }
        val nBonus = level.bonusCells.size
        val maskCount = 1 shl nBonus
        fun of(c: Cell): Int = idx[c] ?: -1
        fun key(cellIdx: Int, orient: Int, mask: Int): Int = (cellIdx * 256 + orient) * maskCount + mask
        val size: Int get() = cells.size * 256 * maskCount
    }

    /**
     * Can the level be finished with [budget] initial steps?
     * Label-correcting search that keeps, for every (cell, orientation, collected-bonuses) state,
     * the maximum number of steps left on arrival. Bonus steps make the queue non-monotone,
     * so states are re-pushed when improved; termination is guaranteed because each bonus is
     * collected at most once.
     */
    fun feasible(level: Level, budget: Int, ignoreBonus: Boolean = false): Boolean {
        if (!level.isOpen(level.start) || !level.isOpen(level.goal)) return false
        val ix = Index(level)
        val best = IntArray(ix.size) { -1 }
        val pq = PriorityQueue<Long>(compareByDescending { it shr 32 })
        val startKey = ix.key(ix.of(level.start), level.startOrientation, 0)
        best[startKey] = budget
        pq.add((budget.toLong() shl 32) or startKey.toLong())
        if (level.start == level.goal && level.goalRequirement() == null) return true
        while (pq.isNotEmpty()) {
            val e = pq.poll()
            val steps = (e shr 32).toInt()
            val key = (e and 0xffffffffL).toInt()
            if (best[key] != steps) continue // stale entry
            val mask = key % ix.maskCount
            val rest = key / ix.maskCount
            val orient = rest % 256
            val cell = ix.cells[rest / 256]
            if (steps == 0) continue
            for (color in 0..2) {
                val next = cell.neighborAcross(color)
                if (!level.isOpen(next)) continue
                val o = Orientation.tip(orient, color)
                val l = Rules.land(level, next, o, steps - 1, mask, ignoreBonus)
                if (l.won) return true
                val nk = ix.key(ix.of(next), l.orient, l.mask)
                if (l.stepsLeft > best[nk]) {
                    best[nk] = l.stepsLeft
                    pq.add((l.stepsLeft.toLong() shl 32) or nk.toLong())
                }
            }
        }
        return false
    }

    /** Smallest initial budget that makes the level solvable, or null if none up to [maxBudget]. */
    fun minBudget(level: Level, ignoreBonus: Boolean = false, maxBudget: Int = 80): Int? {
        if (!feasible(level, maxBudget, ignoreBonus)) return null
        var lo = 0
        var hi = maxBudget
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (feasible(level, mid, ignoreBonus)) hi = mid else lo = mid + 1
        }
        return lo
    }

    data class Path(val moves: List<Int>, val cells: List<Cell>)

    /**
     * Shortest solution (fewest tips) with the level's own step budget, or null.
     * Breadth-first over (cell, orientation, collected, stepsLeft).
     */
    fun shortestSolution(level: Level, budget: Int = level.steps): Path? {
        val ix = Index(level)
        fun key(cellIdx: Int, orient: Int, mask: Int, steps: Int): Long =
            ix.key(cellIdx, orient, mask).toLong() * 256 + steps
        val parent = HashMap<Long, Long>()
        val parentColor = HashMap<Long, Int>()
        val startK = key(ix.of(level.start), level.startOrientation, 0, budget)
        val queue = ArrayDeque<Long>()
        queue.add(startK); parent[startK] = startK
        while (queue.isNotEmpty()) {
            val k = queue.removeFirst()
            val steps = (k % 256).toInt()
            val sk = (k / 256).toInt()
            val mask = sk % ix.maskCount
            val rest = sk / ix.maskCount
            val orient = rest % 256
            val cell = ix.cells[rest / 256]
            if (steps == 0) continue
            for (color in 0..2) {
                val next = cell.neighborAcross(color)
                if (!level.isOpen(next)) continue
                val o = Orientation.tip(orient, color)
                val l = Rules.land(level, next, o, steps - 1, mask)
                val nk = key(ix.of(next), l.orient, l.mask, minOf(l.stepsLeft, 255))
                if (parent.containsKey(nk)) continue
                parent[nk] = k; parentColor[nk] = color
                if (l.won) {
                    // reconstruct
                    val colors = ArrayList<Int>()
                    val cells = ArrayList<Cell>()
                    var cur = nk
                    while (cur != startK) {
                        colors.add(parentColor[cur]!!)
                        val csk = (cur / 256).toInt()
                        cells.add(ix.cells[(csk / ix.maskCount) / 256])
                        cur = parent[cur]!!
                    }
                    colors.reverse(); cells.reverse()
                    return Path(colors, cells)
                }
                queue.add(nk)
            }
        }
        return null
    }
}
