package io.github.skitknasctrl.tetraescape.engine

/**
 * Orientation of the tetrahedron, packed into an Int.
 *
 * v[c] (c = 0..2) = which tetrahedron vertex (0..3) sits on the board corner of colour c.
 * v[3]            = which tetrahedron vertex is the apex (pointing up).
 *
 * The face that lies on the board is the face opposite the apex, i.e. face v[3].
 * The side face across the edge opposite corner colour c is face v[c].
 *
 * Packed as v[0] | v[1] shl 2 | v[2] shl 4 | v[3] shl 6.
 */
object Orientation {

    /** Vertex 0,1,2 on colours 0,1,2 and vertex 3 as apex → DOT on the board. */
    const val INITIAL: Int = 0 or (1 shl 2) or (2 shl 4) or (3 shl 6)

    fun get(o: Int, slot: Int): Int = (o shr (2 * slot)) and 3

    fun pack(v0: Int, v1: Int, v2: Int, v3: Int): Int = v0 or (v1 shl 2) or (v2 shl 4) or (v3 shl 6)

    private fun set(o: Int, slot: Int, value: Int): Int =
        (o and (3 shl (2 * slot)).inv()) or (value shl (2 * slot))

    /** Tetrahedron vertex on the corner with the given colour. */
    fun vertexAtColor(o: Int, color: Int): Int = get(o, color)

    fun apex(o: Int): Int = get(o, 3)

    /** The face currently lying on the board (face index = opposite vertex). */
    fun bottomFace(o: Int): Int = apex(o)

    fun bottomSymbol(o: Int): Symbol = Symbol.FACE_SYMBOLS[bottomFace(o)]

    /** The face that would land on the board after tipping over the edge opposite colour [color]. */
    fun sideFace(o: Int, color: Int): Int = get(o, color)

    /**
     * Tip the piece over the edge opposite the corner of colour [color].
     * The two vertices on that edge stay; the apex comes down onto the new corner
     * (which has colour [color] as well) and the old corner vertex becomes the apex.
     */
    fun tip(o: Int, color: Int): Int {
        val a = get(o, color)
        val b = get(o, 3)
        return set(set(o, color, b), 3, a)
    }

    /**
     * Rotate the piece 120° about its vertical axis without moving it.
     * [clockwise] is as seen on screen looking down at the board (board y pointing up).
     *
     * For an UP cell, going clockwise around the corners means colour c → c-1.
     * For a DOWN cell it means colour c → c+1.
     * (Both follow from the lattice colouring; verified by the geometry test.)
     */
    fun spin(o: Int, clockwise: Boolean, cellUp: Boolean): Int {
        val step = if (clockwise == cellUp) -1 else 1
        var r = set(o, 3, get(o, 3))
        for (c in 0..2) {
            val target = Math.floorMod(c + step, 3)
            r = set(r, target, get(o, c))
        }
        return r
    }

    fun toArray(o: Int): IntArray = IntArray(4) { get(o, it) }

    override fun toString(): String = "Orientation"

    fun describe(o: Int): String = toArray(o).joinToString(",", "[", "]")
}
