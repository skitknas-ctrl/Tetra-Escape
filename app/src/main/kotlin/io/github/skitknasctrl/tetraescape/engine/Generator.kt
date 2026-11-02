package io.github.skitknasctrl.tetraescape.engine

import kotlin.random.Random

/**
 * Random level generator. Deterministic for a given (seed, difficulty), so a
 * level can be shared or replayed by its number.
 *
 * Every candidate is checked with the solver:
 *  - it must be solvable,
 *  - par (the fewest moves) must fit the difficulty,
 *  - the symbol requirements must matter: ignoring them must give a shorter route,
 *  - from difficulty 2 the optimal route must pass at least one spin cell.
 */
object Generator {

    class Params(
        val rows: Int,
        val cols: Int,
        val keys: Int,
        val spinCells: IntRange,
        val portalPairs: IntRange,
        val par: IntRange,
        val minSymbolGain: Int,
        val requireSpinUsed: Boolean,
    )

    fun params(difficulty: Int): Params = when (difficulty.coerceIn(1, 3)) {
        // few, large cells: the board is scaled to the screen width, so cell size is set by cols
        1 -> Params(5, 2, 1, 1..2, 0..0, 6..12, 1, false)
        2 -> Params(6, 2, 2, 2..3, 1..1, 12..20, 2, true)
        else -> Params(7, 3, 3, 3..4, 1..2, 20..32, 2, true)
    }

    fun generate(seed: Long, difficulty: Int, maxAttempts: Int = 500): Level {
        val p = params(difficulty)
        val rnd = Random(seed * 31 + difficulty)
        var fallback: Level? = null
        repeat(maxAttempts) {
            val cand = candidate(rnd, p, seed, difficulty) ?: return@repeat
            val lvl = validate(cand, p)
            if (lvl != null) return lvl
            if (fallback == null) {
                Solver.shortestSolution(cand)?.let { fallback = withSolution(cand, it) }
            }
        }
        return fallback ?: generate(seed + 1, difficulty, maxAttempts)
    }

    private fun candidate(rnd: Random, p: Params, seed: Long, difficulty: Int): Level? {
        val base = Level(p.rows, p.cols, Cell(0, 0, true), Cell(0, 0, true), emptyMap(), seed, difficulty)
        val all = base.allCells
        // start in the bottom third, goal in the top third
        val start = all.filter { it.j < p.rows / 3 }.random(rnd)
        val goal = all.filter { it.j >= p.rows - p.rows / 3 }.random(rnd)
        val free = all.filter { it != start && it != goal && !adjacent(it, start) && !adjacent(it, goal) }
            .shuffled(rnd).toMutableList()
        val specials = HashMap<Cell, Special>()
        val nSpin = p.spinCells.random(rnd)
        val nPortal = p.portalPairs.random(rnd)
        if (free.size < p.keys + nSpin + 2 * nPortal) return null
        for (k in 1..p.keys) {
            specials[free.removeAt(0)] = Special(key = k, require = Symbol.entries.random(rnd))
        }
        repeat(nSpin) {
            specials[free.removeAt(0)] = Special(spin = if (rnd.nextBoolean()) 1 else -1)
        }
        for (id in 1..nPortal) {
            // the two ends should not be neighbours of each other
            val a = free.removeAt(0)
            val bi = free.indexOfFirst { !adjacent(it, a) && it != a }
            if (bi < 0) return null
            val b = free.removeAt(bi)
            specials[a] = Special(portal = id)
            specials[b] = Special(portal = id)
        }
        specials[goal] = Special(require = Symbol.entries.random(rnd))
        return Level(p.rows, p.cols, start, goal, specials, seed, difficulty)
    }

    private fun adjacent(a: Cell, b: Cell): Boolean = a.neighbors().any { it.second == b }

    private fun validate(cand: Level, p: Params): Level? {
        val sol = Solver.shortestSolution(cand) ?: return null
        if (sol.moves.size !in p.par) return null
        val free = Solver.shortestSolution(cand, ignoreRequire = true) ?: return null
        if (sol.moves.size - free.moves.size < p.minSymbolGain) return null
        if (p.requireSpinUsed && sol.cells.none { (cand.specials[it]?.spin ?: 0) != 0 }) return null
        return withSolution(cand, sol)
    }

    private fun withSolution(cand: Level, sol: Solver.Path): Level = Level(
        cand.rows, cand.cols, cand.start, cand.goal, cand.specials,
        cand.seed, cand.difficulty, cand.startOrientation, sol.moves,
    )
}
