package io.github.skitknasctrl.tetraescape.engine

import kotlin.random.Random

/**
 * Random level generator. Deterministic for a given (seed, difficulty), so a
 * level can be shared or replayed by its seed.
 *
 * Every candidate is checked with the solver:
 *  - it must be solvable,
 *  - the bonus steps must be necessary (not solvable with the same budget if bonuses are ignored),
 *  - the shortest solution must have a length that fits the difficulty,
 *  - from difficulty 2 the shortest solution must pass at least one spin cell,
 *  - the goal always demands a specific symbol.
 */
object Generator {

    class Params(
        val rows: Int,
        val cols: Int,
        val holeFraction: Double,
        val bonusCells: IntRange,
        val spinCells: IntRange,
        val bonusAmount: IntRange,
        val minMoves: IntRange,
        val slack: Int,
        val requireSpinUsed: Boolean,
        val goalRequireChance: Double,
        val timeBase: Int,
        val timePerMove: Double,
    )

    fun params(difficulty: Int): Params = when (difficulty.coerceIn(1, 3)) {
        1 -> Params(4, 4, 0.12, 1..2, 1..2, 2..3, 6..10, 1, false, 1.0, 25, 4.0)
        2 -> Params(5, 5, 0.18, 2..3, 2..3, 2..4, 9..14, 0, true, 1.0, 20, 3.0)
        else -> Params(6, 6, 0.22, 3..3, 3..4, 2..4, 12..20, 0, true, 1.0, 15, 2.5)
    }

    fun generate(seed: Long, difficulty: Int, maxAttempts: Int = 400): Level {
        val p = params(difficulty)
        val rnd = Random(seed * 31 + difficulty)
        var fallback: Level? = null
        repeat(maxAttempts) {
            val cand = candidate(rnd, p, seed, difficulty) ?: return@repeat
            val lvl = validate(cand, p)
            if (lvl != null) return lvl
            if (fallback == null) {
                // keep something playable in case nothing meets all the quality rules
                val mb = Solver.minBudget(cand)
                if (mb != null) fallback = withBudget(cand, mb + p.slack, p)
            }
        }
        return fallback ?: generate(seed + 1, difficulty, maxAttempts)
    }

    private fun candidate(rnd: Random, p: Params, seed: Long, difficulty: Int): Level? {
        val base = Level(p.rows, p.cols, emptySet(), Cell(0, 0, true), Cell(0, 0, true), emptyMap(), 0, 0, seed, difficulty)
        val all = base.allCells
        // start on the left part, goal on the right part
        val leftish = all.filter { it.i - base.i0(it.j) < p.cols / 2 }
        val rightish = all.filter { it.i - base.i0(it.j) >= (p.cols + 1) / 2 }
        val start = leftish.random(rnd)
        val goal = rightish.random(rnd)
        val holes = HashSet<Cell>()
        val nHoles = (all.size * p.holeFraction).toInt()
        val pool = all.filter { it != start && it != goal }.shuffled(rnd)
        holes.addAll(pool.take(nHoles))
        val level0 = Level(p.rows, p.cols, holes, start, goal, emptyMap(), 0, 0, seed, difficulty)
        if (!connected(level0)) return null
        // specials
        val free = level0.openCells.filter { it != start && it != goal }.shuffled(rnd).toMutableList()
        val specials = HashMap<Cell, Special>()
        val nBonus = p.bonusCells.random(rnd)
        val nSpin = p.spinCells.random(rnd)
        if (free.size < nBonus + nSpin) return null
        repeat(nBonus) {
            val c = free.removeAt(0)
            specials[c] = Special(bonus = p.bonusAmount.random(rnd), require = Symbol.entries.random(rnd))
        }
        repeat(nSpin) {
            val c = free.removeAt(0)
            specials[c] = Special(spin = if (rnd.nextBoolean()) 1 else -1)
        }
        if (rnd.nextDouble() < p.goalRequireChance) {
            specials[goal] = Special(require = Symbol.entries.random(rnd))
        }
        return Level(p.rows, p.cols, holes, start, goal, specials, 0, 0, seed, difficulty)
    }

    private fun validate(cand: Level, p: Params): Level? {
        val mb = Solver.minBudget(cand) ?: return null
        val budget = mb + p.slack
        val mbNoBonus = Solver.minBudget(cand, ignoreBonus = true, maxBudget = 120) ?: Int.MAX_VALUE
        if (budget >= mbNoBonus) return null // bonuses not needed → boring
        val path = Solver.shortestSolution(cand, budget) ?: return null
        if (path.moves.size !in p.minMoves) return null
        if (p.requireSpinUsed) {
            val usesSpin = path.cells.any { (cand.specials[it]?.spin ?: 0) != 0 }
            if (!usesSpin) return null
        }
        return withBudget(cand, budget, p, path)
    }

    private fun withBudget(cand: Level, budget: Int, p: Params, path: Solver.Path? = null): Level {
        val sol = path ?: Solver.shortestSolution(cand, budget)
        val moves = sol?.moves?.size ?: budget
        val time = (p.timeBase + p.timePerMove * moves).toInt()
        return Level(
            cand.rows, cand.cols, cand.holes, cand.start, cand.goal, cand.specials,
            budget, time, cand.seed, cand.difficulty, cand.startOrientation, sol?.moves ?: emptyList(),
        )
    }

    /** Plain reachability start → goal, ignoring orientation and steps. */
    private fun connected(level: Level): Boolean {
        val seen = HashSet<Cell>()
        val q = ArrayDeque<Cell>()
        q.add(level.start); seen.add(level.start)
        while (q.isNotEmpty()) {
            val c = q.removeFirst()
            if (c == level.goal) return true
            for ((_, n) in c.neighbors()) if (level.isOpen(n) && seen.add(n)) q.add(n)
        }
        return false
    }
}
