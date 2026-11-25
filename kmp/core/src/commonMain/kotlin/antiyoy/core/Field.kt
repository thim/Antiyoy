package antiyoy.core

import kotlin.math.sqrt

/** Hex matrix and geometry (the geometric half of the original FieldManager). */
class Field {
    val fWidth = 85
    val fHeight = 55

    val hexSize = 0.05f * VirtualScreen.WIDTH
    val hexStep1 = sqrt(3f) * hexSize
    val hexStep2 = distance(0f, 0f, 1.5f * hexSize, 0.5f * hexStep1)

    /** Rendering radius of a hex, as in GameView.hexViewSize. */
    val hexViewSize = 1.04f * hexSize

    val fieldPosX = 0f
    val fieldPosY = -1.1f * VirtualScreen.HEIGHT

    val nullHex = Hex(-1, -1, this)

    var cells: Array<Array<Hex>> = createMatrix()
        private set

    /** Active (land) hexes. New hexes are inserted at the front, mirroring the original ListIterator.add. */
    val activeHexes = ArrayList<Hex>()

    private fun createMatrix() = Array(fWidth) { i -> Array(fHeight) { j -> Hex(i, j, this) } }

    internal fun recreateMatrix() {
        cells = createMatrix()
        activeHexes.clear()
    }

    operator fun get(i: Int, j: Int): Hex = cells[i][j]

    fun getHex(i: Int, j: Int): Hex? {
        if (i < 0 || i > fWidth - 1 || j < 0 || j > fHeight - 1) return null
        return cells[i][j]
    }

    fun adjacentHex(hex: Hex, direction: Int): Hex {
        if (hex.isNullHex) return nullHex
        val i = hex.index1
        val j = hex.index2
        return when (direction) {
            0 -> if (i >= fWidth - 1) nullHex else cells[i + 1][j]
            1 -> if (j >= fHeight - 1) nullHex else cells[i][j + 1]
            2 -> if (i <= 0 || j >= fHeight - 1) nullHex else cells[i - 1][j + 1]
            3 -> if (i <= 0) nullHex else cells[i - 1][j]
            4 -> if (j <= 0) nullHex else cells[i][j - 1]
            5 -> if (i >= fWidth - 1 || j <= 0) nullHex else cells[i + 1][j - 1]
            else -> nullHex
        }
    }

    /**
     * Port of FieldManager.getHexByPos. Note the original subtracts hexViewSize from the
     * point before the nearest-neighbour search; callers converting a touch position must
     * add it first (see [hexAtPoint]).
     */
    fun getHexByPos(px: Double, py: Double): Hex? {
        val j = ((px - fieldPosX) / (hexStep2 * Hex.SIN60)).toInt()
        val i = ((py - fieldPosY - hexStep2 * j * Hex.COS60) / hexStep1).toInt()
        if (i < 0 || i > fWidth - 1 || j < 0 || j > fHeight - 1) return null
        var resHex = cells[i][j]
        val x = px - hexViewSize
        val y = py - hexViewSize
        var minDistance = distance(resHex.x.toDouble(), resHex.y.toDouble(), x, y)
        for (k in 0 until 6) {
            val adj = adjacentHex(cells[i][j], k)
            if (!adj.active) continue
            val d = distance(adj.x.toDouble(), adj.y.toDouble(), x, y)
            if (d < minDistance) {
                minDistance = d
                resHex = adj
            }
        }
        return resHex
    }

    /** Hex under a point given in field coordinates (y up). */
    fun hexAtPoint(px: Float, py: Float): Hex? {
        // nearest centre among the candidate and its neighbours, including inactive ones
        val guess = getHexByPos((px + hexViewSize).toDouble(), (py + hexViewSize).toDouble()) ?: return null
        var best = guess
        var bestD = distance(guess.x, guess.y, px, py)
        for (k in 0 until 6) {
            val adj = adjacentHex(guess, k)
            if (adj.isNullHex) continue
            val d = distance(adj.x, adj.y, px, py)
            if (d < bestD) {
                bestD = d
                best = adj
            }
        }
        return best
    }

    companion object {
        fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
            val dx = x2 - x1
            val dy = y2 - y1
            return sqrt(dx * dx + dy * dy)
        }

        fun distance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
            val dx = x2 - x1
            val dy = y2 - y1
            return sqrt(dx * dx + dy * dy)
        }
    }
}
