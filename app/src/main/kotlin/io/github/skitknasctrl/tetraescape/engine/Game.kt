package io.github.skitknasctrl.tetraescape.engine

enum class Status { PLAYING, WON }

/** What happened when the piece landed — used for feedback/animation. */
data class LandingEffect(
    val keyTaken: Int = 0,
    val spun: Int = 0,
    /** Set when a portal moved the piece: the cell it came out of. */
    val portalFrom: Cell? = null,
    /** Landed on the next key (or the goal) with the wrong symbol down. */
    val requirementFailed: Boolean = false,
)

/**
 * The rules applied when the piece lands on [cell] with orientation [orient] holding [keys] keys.
 * Shared by the game and the solver so they can never disagree.
 */
object Rules {
    data class Landing(val cell: Cell, val orient: Int, val keys: Int, val effect: LandingEffect, val won: Boolean)

    fun land(level: Level, landed: Cell, orient: Int, keys: Int, ignoreRequire: Boolean = false): Landing {
        var cell = landed
        var portalFrom: Cell? = null
        level.portalPartner(cell)?.let { portalFrom = cell; cell = it }
        val sp = level.specials[cell]
        var o = orient
        var k = keys
        var taken = 0
        var spun = 0
        var failed = false
        var won = false
        if (sp != null) {
            val ok = ignoreRequire || sp.require == null || Orientation.bottomSymbol(o) == sp.require
            if (sp.spin != 0) {
                o = Orientation.spin(o, sp.spin > 0, cell.up)
                spun = sp.spin
            }
            if (sp.key > 0) {
                if (sp.key == k + 1) {
                    if (ok) { k++; taken = sp.key } else failed = true
                }
            }
            if (cell == level.goal) {
                if (k == level.keyCount && ok) won = true else failed = true
            }
        } else if (cell == level.goal) {
            won = k == level.keyCount
        }
        return Landing(cell, o, k, LandingEffect(taken, spun, portalFrom, failed), won)
    }
}

/** Immutable game state; [move] returns the next state. */
data class GameState(
    val level: Level,
    val cell: Cell,
    val orient: Int,
    val keys: Int,
    val moves: Int,
    val status: Status,
    val lastEffect: LandingEffect = LandingEffect(),
    val lastColor: Int = -1,
) {
    val bottomSymbol: Symbol get() = Orientation.bottomSymbol(orient)

    val goalOpen: Boolean get() = keys == level.keyCount

    fun isKeyTaken(c: Cell): Boolean {
        val n = level.specials[c]?.key ?: 0
        return n in 1..keys
    }

    fun canMove(color: Int): Boolean =
        status == Status.PLAYING && level.isOpen(cell.neighborAcross(color))

    fun legalMoves(): List<Int> = (0..2).filter { canMove(it) }

    fun move(color: Int): GameState? {
        if (!canMove(color)) return null
        val next = cell.neighborAcross(color)
        val o = Orientation.tip(orient, color)
        val l = Rules.land(level, next, o, keys)
        return GameState(level, l.cell, l.orient, l.keys, moves + 1, if (l.won) Status.WON else Status.PLAYING, l.effect, color)
    }

    companion object {
        fun start(level: Level): GameState =
            GameState(level, level.start, level.startOrientation, 0, 0, Status.PLAYING)
    }
}
