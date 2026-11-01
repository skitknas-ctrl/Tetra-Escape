package io.github.skitknasctrl.tetraescape.engine

enum class Status { PLAYING, WON, LOST }

/** What happened when the piece landed on a cell — used for feedback/animation. */
data class LandingEffect(
    val bonusGained: Int = 0,
    val spun: Int = 0,
    val requirementFailed: Boolean = false,
)

/**
 * The rules applied when the piece lands on [cell] with orientation [orient].
 * Shared by the game and the solver so they can never disagree.
 */
object Rules {
    data class Landing(val orient: Int, val stepsLeft: Int, val mask: Int, val effect: LandingEffect, val won: Boolean)

    fun land(level: Level, cell: Cell, orient: Int, stepsLeft: Int, mask: Int, ignoreBonus: Boolean = false): Landing {
        val sp = level.specials[cell]
        var o = orient
        var s = stepsLeft
        var m = mask
        var gained = 0
        var spun = 0
        var failed = false
        var ok = true
        if (sp != null) {
            ok = sp.require == null || Orientation.bottomSymbol(o) == sp.require
            if (ok) {
                if (sp.bonus > 0 && !ignoreBonus) {
                    val bit = 1 shl level.bonusIndex(cell)
                    if (m and bit == 0) {
                        m = m or bit
                        s += sp.bonus
                        gained = sp.bonus
                    }
                }
                if (sp.spin != 0) {
                    o = Orientation.spin(o, sp.spin > 0, cell.up)
                    spun = sp.spin
                }
            } else {
                failed = true
            }
        }
        val won = cell == level.goal && ok
        return Landing(o, s, m, LandingEffect(gained, spun, failed), won)
    }
}

/** Immutable game state; [move] returns the next state. */
data class GameState(
    val level: Level,
    val cell: Cell,
    val orient: Int,
    val stepsLeft: Int,
    val mask: Int,
    val moves: Int,
    val status: Status,
    val lastEffect: LandingEffect = LandingEffect(),
    val lastColor: Int = -1,
) {
    val bottomSymbol: Symbol get() = Orientation.bottomSymbol(orient)

    fun isCollected(c: Cell): Boolean {
        val idx = level.bonusIndex(c)
        return idx >= 0 && (mask and (1 shl idx)) != 0
    }

    fun canMove(color: Int): Boolean =
        status == Status.PLAYING && stepsLeft > 0 && level.isOpen(cell.neighborAcross(color))

    fun legalMoves(): List<Int> = (0..2).filter { canMove(it) }

    fun move(color: Int): GameState? {
        if (!canMove(color)) return null
        val next = cell.neighborAcross(color)
        val o = Orientation.tip(orient, color)
        val l = Rules.land(level, next, o, stepsLeft - 1, mask)
        val st = GameState(level, next, l.orient, l.stepsLeft, l.mask, moves + 1, Status.PLAYING, l.effect, color)
        val status = when {
            l.won -> Status.WON
            st.legalMoves().isEmpty() -> Status.LOST
            else -> Status.PLAYING
        }
        return st.copy(status = status)
    }

    companion object {
        fun start(level: Level): GameState {
            val st = GameState(level, level.start, level.startOrientation, level.steps, 0, 0, Status.PLAYING)
            return if (st.legalMoves().isEmpty()) st.copy(status = Status.LOST) else st
        }
    }
}
