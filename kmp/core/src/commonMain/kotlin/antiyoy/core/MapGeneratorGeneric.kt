package antiyoy.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Random map generator for the generic ruleset.
 * Port of gameplay.MapGenerator + gameplay.MapGeneratorGeneric (only the code paths the generic rules use).
 */
internal class MapGeneratorGeneric(private val game: Game, private val random: Random) {
    private val field = game.field
    private val fWidth = field.fWidth
    private val fHeight = field.fHeight
    private val w = VirtualScreen.WIDTH
    private val h = VirtualScreen.HEIGHT
    private val boundWidth = game.boundWidth
    private val boundHeight = game.boundHeight
    private val islandCenters = ArrayList<Hex>()
    private val links = ArrayList<Pair<Hex, Hex>>()
    private var flagNeutrals = false

    private val active get() = field.activeHexes

    fun generateMap() {
        field.recreateMatrix()
        createLand()
        addTrees()
        var guard = 0
        while (!hasGreenProvince() && guard++ < 1000) genericBalance()
        field.nullHex.active = false
    }

    // ------------------------------------------------------------ land

    private fun createLand() {
        var attempts = 0
        while (!isGood()) {
            check(attempts++ < 500) { "Map generation failed" }
            deactivateHexes()
            repeat(numberOfIslandsByLevelSize()) {
                val hex = getRandomHexInsideBounds()
                islandCenters.add(hex)
                spawnIsland(hex, 7)
            }
            uniteIslandsWithRoads()
            centerLand()
            cutOffHexesOutsideOfBounds()
        }
    }

    private fun numberOfIslandsByLevelSize() = when (game.config.levelSize) {
        LevelSize.MEDIUM -> 4
        LevelSize.BIG -> 20
        LevelSize.HUGE -> 35
        else -> 2
    }

    private fun isGood() = isLinked() && active.size > 0.25 * numberOfAvailableHexes()

    private fun numberOfAvailableHexes(): Int {
        var c = 0
        for (row in field.cells) for (hex in row) if (isHexInsideBounds(hex)) c++
        return c
    }

    private fun isLinked(): Boolean {
        clearGenFlags()
        val start = findActiveHex() ?: return false
        val queue = ArrayDeque<Hex>()
        queue.add(start)
        start.genFlag = true
        while (queue.isNotEmpty()) {
            val hex = queue.removeFirst()
            for (i in 0 until 6) {
                val adj = hex.adjacent(i)
                if (adj.active && !adj.genFlag) {
                    adj.genFlag = true
                    queue.add(adj)
                }
            }
        }
        for (row in field.cells) for (hex in row) if (hex.active && !hex.genFlag) return false
        return true
    }

    private fun findActiveHex(): Hex? {
        for (row in field.cells) for (hex in row) if (hex.active) return hex
        return null
    }

    private fun isHexInsideBounds(hex: Hex?): Boolean {
        if (hex == null) return false
        if (hex.x <= 0.1f * w) return false
        if (hex.x >= boundWidth - 0.1f * w) return false
        if (hex.y <= 0.15f * h) return false
        if (hex.y >= boundHeight - 0.1f * h) return false
        return true
    }

    private fun getRandomAngle() = random.nextDouble() * 2.0 * PI

    private fun getRandomHexInsideBounds(): Hex {
        while (true) {
            val hex: Hex? = if (game.config.levelSize <= LevelSize.MEDIUM) {
                field[random.nextInt(fWidth), random.nextInt(fHeight)]
            } else {
                val r = random.nextDouble() * random.nextDouble() * 0.5 * boundHeight
                val a = getRandomAngle()
                field.getHexByPos(boundWidth / 2.0 + r * cos(a), boundHeight / 2.0 + r * sin(a))
            }
            if (isHexInsideBounds(hex)) return hex!!
        }
    }

    private fun activateHex(hex: Hex): Boolean {
        if (hex.active) return false
        hex.active = true
        hex.setFractionTracked(Rules.NEUTRAL_FRACTION)
        active.add(0, hex)
        return true
    }

    private fun deactivateHex(hex: Hex) {
        hex.active = false
        active.remove(hex)
    }

    private fun deactivateHexes() {
        for (row in field.cells) for (hex in row) if (hex.active) deactivateHex(hex)
    }

    private fun clearGenFlags() {
        for (row in field.cells) for (hex in row) hex.genFlag = false
    }

    private fun spawnIsland(startHex: Hex, size: Int) {
        clearGenFlags()
        startHex.genPotential = size
        val queue = ArrayList<Hex>()
        queue.add(startHex)
        while (queue.isNotEmpty()) {
            val hex = queue.removeAt(0)
            hex.genFlag = true
            if (random.nextInt(size) > hex.genPotential) continue
            val activated = activateHex(hex)
            if (hex.genPotential == 0 || !activated) continue
            for (i in 0 until 6) {
                val adj = hex.adjacent(i)
                if (!adj.genFlag && !adj.isNullHex && !queue.contains(adj)) {
                    adj.genPotential = hex.genPotential - 1
                    queue.add(adj)
                }
            }
        }
    }

    private fun uniteIslandsWithRoads() {
        for (i in islandCenters.indices) createRoadBetweenIslands(i, getClosestIslandIndex(i))
    }

    private fun createRoadBetweenIslands(one: Int, two: Int) {
        if (two == -1) return
        val start = islandCenters[one]
        val end = islandCenters[two]
        links.add(start to end)
        val distance = start.distanceTo(end).toDouble()
        val delta = field.hexSize / 2.0
        val a = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
        val n = (distance / delta).toInt()
        for (i in 0 until n) {
            val cx = start.x + delta * i * cos(a)
            val cy = start.y + delta * i * sin(a)
            val hex = field.getHexByPos(cx, cy) ?: continue
            spawnIsland(hex, 2)
        }
    }

    private fun areIslandsAlreadyUnited(p1: Hex, p2: Hex) =
        links.any { (a, b) -> (a === p1 || b === p1) && (a === p2 || b === p2) }

    private fun getClosestIslandIndex(searcher: Int): Int {
        val start = islandCenters[searcher]
        var closest = -1
        var minDistance = (fWidth * fHeight).toDouble()
        for (i in 1 until islandCenters.size) {
            if (i == searcher) continue
            if (areIslandsAlreadyUnited(start, islandCenters[i])) continue
            val d = start.distanceTo(islandCenters[i]).toDouble()
            if (d < minDistance) {
                minDistance = d
                closest = i
            }
        }
        return closest
    }

    private fun centerLand() {
        val center = field.getHexByPos(boundWidth / 2.0, boundHeight / 2.0) ?: return
        var left = center.index1
        var right = center.index1
        var up = center.index2
        var down = center.index2
        for (hex in active) {
            if (hex.index1 < left) left = hex.index1
            if (hex.index1 > right) right = hex.index1
            if (hex.index2 < down) down = hex.index2
            if (hex.index2 > up) up = hex.index2
        }
        relocateMap(center.index1 - (left + right) / 2, center.index2 - (down + up) / 2)
    }

    private fun relocateMap(deltaX: Int, deltaY: Int) {
        clearGenFlags()
        for (row in field.cells) for (hex in row) {
            hex.genFlag = hex.active
            hex.previousFraction = hex.fraction
        }
        active.clear()
        deactivateHexes()
        for (i in 0 until fWidth) for (j in 0 until fHeight) {
            val dx = i + deltaX
            val dy = j + deltaY
            if (dx in 0 until fWidth && dy in 0 until fHeight) {
                if (field[i, j].genFlag) activateHex(field[dx, dy])
            } else {
                deactivateHex(field[i, j])
            }
        }
    }

    private fun cutOffHexesOutsideOfBounds() {
        for (row in field.cells) for (hex in row) {
            if (hex.active && !isHexInsideBounds(hex)) {
                deactivateHex(hex)
                for (k in 0 until 6) maybeDeactivateIfPossible(hex.adjacent(k))
            }
        }
    }

    private fun maybeDeactivateIfPossible(hex: Hex) {
        if (!hex.active) return
        if (random.nextDouble() > 0.8) return
        if (hex.numberOfActiveHexesNearby() == 4) deactivateHex(hex)
    }

    private fun addTrees() {
        for (hex in active) {
            if (random.nextDouble() < 0.1 && !hex.containsObject()) game.spawnTree(hex)
        }
    }

    // ------------------------------------------------------------ provinces

    private fun hasGreenProvince() = active.any { it.fraction == 0 && it.numberOfFriendlyHexesNearby() >= 2 }

    private fun genericBalance() {
        for (hex in active) hex.fraction = Rules.NEUTRAL_FRACTION
        flagNeutrals = true
        spawnProvinces()
        cutProvincesToSmallSizes()
        makeSingleHexesIntoProvinces()
        applyBalanceMeasures()
    }

    private fun provincesQuantity(): Int {
        if (game.config.genProvinces > 0) return game.config.genProvinces
        return when (game.config.levelSize) {
            LevelSize.MEDIUM -> 2
            LevelSize.BIG -> 3
            LevelSize.HUGE -> 4
            else -> 1
        }
    }

    private fun spawnProvinces() {
        repeat(provincesQuantity()) {
            for (fraction in 0 until game.fractionsQuantity) {
                val hex = findGoodPlaceForNewProvince() ?: continue
                hex.setFractionTracked(fraction)
                spawnProvince(hex, 2)
            }
        }
    }

    private fun allHexesAreNeutral(): Boolean {
        if (!flagNeutrals) return false
        if (active.any { !it.isNeutral }) {
            flagNeutrals = false
            return false
        }
        return true
    }

    private fun findGoodPlaceForNewProvince(): Hex? {
        if (allHexesAreNeutral()) return active[random.nextInt(active.size)]
        for (hex in active) hex.moveZoneNumber = if (hex.isNeutral) -1 else 0
        var step = 0
        while (true) {
            var expanded = false
            for (hex in active) {
                if (hex.moveZoneNumber != step) continue
                for (dir in 0 until 6) {
                    val adj = hex.adjacent(dir)
                    if (adj.isNullHex || !adj.active || adj.moveZoneNumber != -1) continue
                    adj.moveZoneNumber = step + 1
                    expanded = true
                }
            }
            if (!expanded) break
            step++
        }
        var result: Hex? = null
        for (hex in active) if (result == null || hex.moveZoneNumber > result.moveZoneNumber) result = hex
        return result
    }

    private fun spawnProvince(spawnHex: Hex, startingPotential: Int) {
        spawnHex.genPotential = startingPotential
        val queue = ArrayList<Hex>()
        queue.add(spawnHex)
        while (queue.isNotEmpty()) {
            val hex = queue.removeAt(0)
            if (random.nextInt(startingPotential) > hex.genPotential) continue
            hex.fraction = spawnHex.fraction
            if (hex.genPotential == 0) continue
            for (i in 0 until 6) {
                val adj = hex.adjacent(i)
                if (!queue.contains(adj) && adj.active && adj.fraction == Rules.NEUTRAL_FRACTION) {
                    adj.genPotential = hex.genPotential - 1
                    queue.add(adj)
                }
            }
        }
    }

    private fun detectProvince(start: Hex): ArrayList<Hex> {
        val result = ArrayList<Hex>()
        if (start.fraction == Rules.NEUTRAL_FRACTION) {
            result.add(start)
            return result
        }
        val seen = HashSet<Hex>()
        val queue = ArrayDeque<Hex>()
        queue.add(start)
        seen.add(start)
        while (queue.isNotEmpty()) {
            val hex = queue.removeFirst()
            result.add(hex)
            for (i in 0 until 6) {
                val adj = hex.adjacent(i)
                if (!adj.active || !adj.sameFraction(hex) || adj in seen) continue
                seen.add(adj)
                queue.add(adj)
            }
        }
        return result
    }

    private fun atLeastOneProvinceIsTooBig() = active.any { detectProvince(it).size > SMALL_PROVINCE_SIZE }

    private fun cutProvincesToSmallSizes() {
        var loopLimit = 100
        while (atLeastOneProvinceIsTooBig() && loopLimit > 0) {
            for (hex in active) {
                val list = detectProvince(hex)
                if (list.size > SMALL_PROVINCE_SIZE) reduceProvinceSize(list)
            }
            loopLimit--
        }
    }

    private fun findHexToExcludeFromProvince(list: List<Hex>): Hex {
        var result: Hex? = null
        var minNumber = 0
        for (hex in list) {
            val n = hex.numberOfFriendlyHexesNearby()
            if (result == null || n < minNumber) {
                minNumber = n
                result = hex
            }
        }
        return result!!
    }

    private fun reduceProvinceSize(list: ArrayList<Hex>) {
        while (list.size > SMALL_PROVINCE_SIZE) {
            val hex = findHexToExcludeFromProvince(list)
            list.remove(hex)
            hex.fraction = Rules.NEUTRAL_FRACTION
        }
    }

    private fun makeSingleHexesIntoProvinces() {
        for (hex in active) {
            if (hex.isNeutral || hex.numberOfFriendlyHexesNearby() > 0) continue
            var c = 3
            for (i in 0 until 6) {
                val adj = hex.adjacent(i)
                if (!adj.active || !adj.isNeutral) continue
                adj.fraction = hex.fraction
                c--
                if (c == 0) break
            }
        }
    }

    private fun changesArray(): DoubleArray? = when (game.fractionsQuantity) {
        2 -> doubleArrayOf(-0.4, 0.3)
        3 -> doubleArrayOf(-0.5, 0.2, 0.4)
        4 -> doubleArrayOf(-0.6, 0.12, 0.3, 0.6)
        5 -> doubleArrayOf(-0.5, -0.35, 0.15, 0.32, 0.5)
        6 -> doubleArrayOf(-0.6, -0.75, 0.15, 0.25, 0.4, 0.6)
        7 -> doubleArrayOf(-0.4, -0.9, 0.2, 0.3, 0.45, 0.6, 0.8)
        else -> null
    }

    private fun applyBalanceMeasures() {
        val changes = changesArray() ?: return
        for (i in changes.indices) {
            when {
                changes[i] > 0 -> giveAdvantageToPlayer(i, changes[i])
                changes[i] < 0 -> giveDisadvantageToPlayer(i, changes[i])
            }
        }
    }

    private fun giveAdvantageToPlayer(index: Int, power: Double) {
        clearGenFlags()
        for (hex in active.toList()) {
            if (hex.genFlag || !hex.sameFraction(index)) continue
            increaseProvince(detectProvince(hex), power)
            for (h in detectProvince(hex)) h.genFlag = true
        }
    }

    private fun increaseProvince(list: List<Hex>, power: Double) {
        for (hex in list) for (i in 0 until 6) {
            val adj = hex.adjacent(i)
            if (adj.active && !adj.sameFraction(hex) && random.nextDouble() < power) adj.fraction = hex.fraction
        }
    }

    private fun giveDisadvantageToPlayer(index: Int, power: Double) {
        clearGenFlags()
        for (hex in active.toList()) {
            if (hex.genFlag || !hex.sameFraction(index)) continue
            val list = detectProvince(hex)
            for (h in list) h.genFlag = true
            // The original passes a negative power here, so this removes nothing. Kept for parity.
            val num = (power * list.size).toInt()
            repeat(maxOf(0, num)) {
                val excluded = findHexToExcludeFromProvince(list)
                list.remove(excluded)
                excluded.fraction = Rules.NEUTRAL_FRACTION
            }
        }
    }

    companion object {
        const val SMALL_PROVINCE_SIZE = 5
    }
}
