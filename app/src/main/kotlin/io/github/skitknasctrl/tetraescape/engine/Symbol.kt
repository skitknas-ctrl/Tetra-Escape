package io.github.skitknasctrl.tetraescape.engine

/**
 * The four symbols on the tetrahedron. All are invariant under 120° rotation
 * (and mirroring), so the piece can be shown from any side without the symbol
 * looking "wrong".
 */
enum class Symbol {
    TRIANGLE, STAR, CIRCLE, DOT;

    companion object {
        /** Face f (the face opposite tetrahedron vertex f) carries FACE_SYMBOLS[f]. */
        val FACE_SYMBOLS: Array<Symbol> = arrayOf(TRIANGLE, STAR, CIRCLE, DOT)
    }
}
