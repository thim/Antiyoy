package antiyoy.core

/** Full copy of the mutable match state, used for undo (replaces SnapshotManager/LevelSnapshot). */
class Snapshot private constructor(
    private val turn: Int,
    private val hexStates: List<HexState>,
    private val provinceStates: List<ProvinceState>,
) {
    private class HexState(
        val i: Int, val j: Int, val fraction: Int, val obj: Int,
        val unitStrength: Int, val unitReady: Boolean, val blockTree: Boolean,
    )

    private class ProvinceState(val hexes: List<Pair<Int, Int>>, val money: Int)

    internal fun restore(game: Game) {
        val field = game.field
        game.units.clear()
        for (s in hexStates) {
            val hex = field[s.i, s.j]
            hex.fraction = s.fraction
            hex.objectInside = s.obj
            hex.unit = null
            hex.blockToTreeFromExpanding = s.blockTree
            if (s.unitStrength > 0) game.putUnit(hex, s.unitStrength, s.unitReady)
        }
        game.provinces.clear()
        for (p in provinceStates) {
            game.provinces.add(game.newProvince(p.hexes.map { field[it.first, it.second] }, p.money))
        }
        game.restoreInternals(turn)
    }

    companion object {
        internal fun capture(game: Game): Snapshot {
            val hexes = game.field.activeHexes.map {
                HexState(
                    it.index1, it.index2, it.fraction, it.objectInside,
                    it.unit?.strength ?: 0, it.unit?.isReadyToMove ?: false, it.blockToTreeFromExpanding,
                )
            }
            val provinces = game.provinces.map { p ->
                ProvinceState(p.hexList.map { it.index1 to it.index2 }, p.money)
            }
            return Snapshot(game.turn, hexes, provinces)
        }
    }
}
