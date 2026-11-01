package io.github.skitknasctrl.tetraescape.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {

    @Test
    fun spinIsClockwiseOnScreen() {
        // board coords have y up, so a visually clockwise turn has a negative cross product
        for (cell in listOf(Cell(0, 0, true), Cell(0, 0, false), Cell(2, 3, true), Cell(-1, 3, false))) {
            val (cx, cy) = cell.center()
            for (cw in listOf(true, false)) {
                val o2 = Orientation.spin(Orientation.INITIAL, cw, cell.up)
                assertEquals(Orientation.apex(Orientation.INITIAL), Orientation.apex(o2))
                for (c in 0..2) {
                    val v = Orientation.vertexAtColor(Orientation.INITIAL, c)
                    val c2 = (0..2).first { Orientation.vertexAtColor(o2, it) == v }
                    val a = cell.cornerOfColor(c); val b = cell.cornerOfColor(c2)
                    val cross = (Cell.ptX(a) - cx) * (Cell.ptY(b) - cy) - (Cell.ptY(a) - cy) * (Cell.ptX(b) - cx)
                    assertTrue("cell=$cell cw=$cw", if (cw) cross < 0 else cross > 0)
                }
            }
        }
    }

    @Test
    fun neighboursShareAnEdgeAndKeepColour() {
        for (i in -3..3) for (j in -3..3) for (up in listOf(true, false)) {
            val c = Cell(i, j, up)
            for (col in 0..2) {
                val n = c.neighborAcross(col)
                assertEquals(c, n.neighborAcross(col))
                val shared = c.corners.toSet() intersect n.corners.toSet()
                assertEquals(2, shared.size)
                assertTrue(c.cornerOfColor(col) !in shared && n.cornerOfColor(col) !in shared)
            }
        }
    }

    @Test
    fun tipRollsSideFaceDown() {
        val o = Orientation.INITIAL
        for (c in 0..2) {
            val o2 = Orientation.tip(o, c)
            assertEquals(Orientation.sideFace(o, c), Orientation.bottomFace(o2))
            for (k in 0..2) if (k != c) assertEquals(Orientation.vertexAtColor(o, k), Orientation.vertexAtColor(o2, k))
            assertEquals(Orientation.vertexAtColor(o, c), Orientation.apex(o2))
        }
    }

    @Test
    fun orientationDependsOnlyOnCellWithoutSpins() {
        val big = Level(6, 6, emptySet(), Cell(0, 0, true), Cell(0, 5, false), emptyMap(), 0, 0, 0, 1)
        val seen = HashMap<Cell, Int>()
        val rnd = Random(1)
        repeat(20) {
            var cell = big.start; var or = Orientation.INITIAL
            repeat(200) {
                val col = rnd.nextInt(3)
                val n = cell.neighborAcross(col)
                if (big.isOpen(n)) {
                    cell = n; or = Orientation.tip(or, col)
                    val prev = seen.put(cell, or)
                    assertTrue(prev == null || prev == or)
                }
            }
        }
    }

    @Test
    fun generatedLevelsAreSolvableAndNeedBonuses() {
        for (d in 1..3) for (seed in 1L..6L) {
            val lvl = Generator.generate(seed, d)
            var st = GameState.start(lvl)
            for (m in lvl.solution) st = st.move(m) ?: throw AssertionError("illegal move seed=$seed d=$d")
            assertEquals("seed=$seed d=$d", Status.WON, st.status)
            val noBonus = Solver.minBudget(lvl, ignoreBonus = true, maxBudget = 120)
            assertTrue("bonus must be needed seed=$seed d=$d", noBonus == null || noBonus > lvl.steps)
            assertNotNull(Solver.shortestSolution(lvl))
            assertTrue(lvl.timeSeconds > 0)
        }
    }

    @Test
    fun generatorIsDeterministic() {
        val a = Generator.generate(42, 2)
        val b = Generator.generate(42, 2)
        assertEquals(a.holes, b.holes)
        assertEquals(a.specials, b.specials)
        assertEquals(a.solution, b.solution)
        assertEquals(a.steps, b.steps)
    }
}
