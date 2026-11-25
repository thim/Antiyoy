package antiyoy.core

/** A soldier (strength 1..4: peasant, spearman, baron, knight). Port of gameplay.Unit. */
class GameUnit internal constructor(currentHex: Hex, var strength: Int) {
    var currentHex: Hex = currentHex
        internal set
    var lastHex: Hex = currentHex
        internal set
    var isReadyToMove = false
        internal set

    /** Incremented every time the unit moves, so views can animate the transition. */
    var moveCounter = 0
        internal set

    val fraction get() = currentHex.fraction

    override fun toString() = "[Unit: ${currentHex.index1} ${currentHex.index2} $strength $isReadyToMove]"
}
