package antiyoy.core

/** A single cell of the hexagonal field. Port of yio.tro.antiyoy.gameplay.Hex. */
class Hex internal constructor(val index1: Int, val index2: Int, internal val field: Field) {
    var active = false
    var fraction = 0
    var previousFraction = 0
    var objectInside = Obj.NONE
        internal set
    var unit: GameUnit? = null
        internal set

    /** Position in logical field coordinates (y grows upwards, as in libGDX). */
    val x: Float = field.fieldPosX + field.hexStep2 * index2 * SIN60
    val y: Float = field.fieldPosY + field.hexStep1 * index1 + field.hexStep2 * index2 * COS60

    /** Deterministic pick between the three farm sprites. */
    val visualDiversityIndex = ((101 * index1 * index2 + 7 * index2) % 3 + 3) % 3

    // Algorithm scratch fields
    internal var flag = false
    internal var genFlag = false
    internal var genPotential = 0
    internal var moveZoneNumber = 0
    internal var algoLink: Hex? = null
    internal var algoValue = 0
    var inMoveZone = false
        internal set
    internal var blockToTreeFromExpanding = false
    internal var canContainObjects = true

    val isNullHex get() = index1 == -1 && index2 == -1
    val isNeutral get() = fraction == Rules.NEUTRAL_FRACTION

    fun adjacent(direction: Int): Hex = field.adjacentHex(this, direction)

    internal fun setFractionTracked(newFraction: Int) {
        previousFraction = fraction
        fraction = newFraction
    }

    fun isFree() = !containsObject() && !containsUnit()
    fun isEmpty() = isFree()
    fun nothingBlocksWayForUnit() = !containsUnit() && !containsBuilding()
    fun containsTree() = objectInside == Obj.PALM || objectInside == Obj.PINE
    fun containsObject() = objectInside > 0
    fun containsTower() = objectInside == Obj.TOWER || objectInside == Obj.STRONG_TOWER
    fun containsBuilding() = objectInside == Obj.TOWN || objectInside == Obj.TOWER ||
            objectInside == Obj.FARM || objectInside == Obj.STRONG_TOWER
    fun containsUnit() = unit != null

    fun sameFraction(other: Hex) = fraction == other.fraction
    fun sameFraction(otherFraction: Int) = fraction == otherFraction
    fun sameFraction(province: Province) = fraction == province.fraction

    fun isInProvince(): Boolean {
        for (i in 0 until 6) {
            val adj = adjacent(i)
            if (adj.active && adj.sameFraction(this)) return true
        }
        return false
    }

    fun isNearWater(): Boolean {
        if (!active) return false
        for (i in 0 until 6) if (!adjacent(i).active) return true
        return false
    }

    fun numberOfActiveHexesNearby() = numberOfFriendlyHexesNearby() + howManyEnemyHexesNear()

    fun noProvincesNearby(): Boolean {
        if (numberOfFriendlyHexesNearby() > 0) return false
        for (i in 0 until 6) {
            val adj = adjacent(i)
            if (adj.active && adj.numberOfFriendlyHexesNearby() > 0) return false
        }
        return true
    }

    fun numberOfFriendlyHexesNearby(): Int {
        var c = 0
        for (i in 0 until 6) {
            val adj = adjacent(i)
            if (adj.isNullHex || adj.isNeutral || !adj.active || !adj.sameFraction(this)) continue
            c++
        }
        return c
    }

    fun getDefenseNumber(ignoreUnit: GameUnit? = null): Int {
        var defense = 0
        fun accumulate(h: Hex) {
            when (h.objectInside) {
                Obj.TOWN -> defense = maxOf(defense, 1)
                Obj.TOWER -> defense = maxOf(defense, 2)
                Obj.STRONG_TOWER -> defense = maxOf(defense, 3)
            }
            val u = h.unit
            if (u != null && u !== ignoreUnit) defense = maxOf(defense, u.strength)
        }
        accumulate(this)
        for (i in 0 until 6) {
            val n = adjacent(i)
            if (!(n.active && n.sameFraction(this))) continue
            accumulate(n)
        }
        return defense
    }

    fun isInPerimeter(): Boolean {
        for (i in 0 until 6) {
            val adj = adjacent(i)
            if (adj.active && !adj.sameFraction(this) && adj.isInProvince()) return true
        }
        return false
    }

    /** True if this hex or a same-fraction neighbour holds [objectIndex]. */
    fun hasThisObjectNearby(objectIndex: Int): Boolean {
        if (objectInside == objectIndex) return true
        for (i in 0 until 6) {
            val adj = adjacent(i)
            if (adj.fraction != fraction) continue
            if (adj.active && adj.objectInside == objectIndex) return true
        }
        return false
    }

    internal fun hasPalmReadyToExpandNearby(): Boolean {
        for (i in 0 until 6) {
            val adj = adjacent(i)
            if (!adj.blockToTreeFromExpanding && adj.objectInside == Obj.PALM) return true
        }
        return false
    }

    internal fun hasPineReadyToExpandNearby(): Boolean {
        for (i in 0 until 6) {
            val adj = adjacent(i)
            if (!adj.blockToTreeFromExpanding && adj.objectInside == Obj.PINE) return true
        }
        return false
    }

    fun howManyEnemyHexesNear(): Int {
        var c = 0
        for (i in 0 until 6) {
            val adj = adjacent(i)
            if (adj.active && !adj.sameFraction(this)) c++
        }
        return c
    }

    fun isDefendedByTower(): Boolean {
        for (i in 0 until 6) {
            val adj = adjacent(i)
            if (adj.active && adj.sameFraction(this) && adj.containsTower()) return true
        }
        return false
    }

    fun howManyTreesNearby(): Int {
        if (!active) return 0
        var c = 0
        for (i in 0 until 6) if (adjacent(i).containsTree()) c++
        return c
    }

    fun distanceTo(other: Hex): Float {
        val dx = x - other.x
        val dy = y - other.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    override fun toString() = "[Hex: f$fraction ($index1, $index2)]"

    companion object {
        val COS60 = kotlin.math.cos(kotlin.math.PI / 3.0).toFloat()
        val SIN60 = kotlin.math.sin(kotlin.math.PI / 3.0).toFloat()
    }
}
