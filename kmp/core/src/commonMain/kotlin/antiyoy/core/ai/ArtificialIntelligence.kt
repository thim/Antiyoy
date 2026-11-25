package antiyoy.core.ai

import antiyoy.core.Difficulty
import antiyoy.core.Game
import antiyoy.core.GameUnit
import antiyoy.core.Hex
import antiyoy.core.Obj
import antiyoy.core.Province
import antiyoy.core.Rules

fun createAi(game: Game, fraction: Int, difficulty: Int): ArtificialIntelligence = when (difficulty) {
    Difficulty.EASY -> AiEasy(game, fraction)
    Difficulty.NORMAL -> AiNormal(game, fraction)
    Difficulty.HARD -> AiHard(game, fraction)
    Difficulty.EXPERT -> AiExpert(game, fraction)
    Difficulty.BALANCER -> AiBalancer(game, fraction)
    else -> AiEasy(game, fraction)
}

/** Port of ai.ArtificialIntelligence (+ ArtificialIntelligenceGeneric where noted). */
abstract class ArtificialIntelligence(protected val game: Game, val fraction: Int) {
    protected val random get() = game.random
    protected val nearbyProvinces = ArrayList<Province>()
    private val unitsReadyToMove = ArrayList<GameUnit>()

    fun perform() = makeMove()

    protected abstract fun makeMove()

    private fun updateUnitsReadyToMove() {
        unitsReadyToMove.clear()
        for (p in game.provinces) {
            if (p.fraction != fraction) continue
            for (k in p.hexList.indices.reversed()) {
                val u = p.hexList[k].unit ?: continue
                if (u.isReadyToMove) unitsReadyToMove.add(u)
            }
        }
    }

    protected fun moveUnits() {
        updateUnitsReadyToMove()
        for (unit in unitsReadyToMove.toList()) {
            if (!unit.isReadyToMove || !isAlive(unit)) continue
            val moveZone = game.detectMoveZone(unit.currentHex, unit.strength, Rules.UNIT_MOVE_LIMIT)
            excludeFriendlyBuildingsFromMoveZone(moveZone)
            excludeFriendlyUnitsFromMoveZone(moveZone)
            if (moveZone.isEmpty()) continue
            decideAboutUnit(unit, moveZone, game.getProvinceByHex(unit.currentHex))
        }
    }

    private fun isAlive(unit: GameUnit) = unit.currentHex.unit === unit

    protected fun spendMoneyAndMergeUnits() {
        var i = 0
        while (i < game.provinces.size) {
            val p = game.provinces[i]
            if (p.fraction == fraction) {
                spendMoney(p)
                mergeUnits(p)
            }
            i++
        }
    }

    protected fun moveAfkUnits() {
        updateUnitsReadyToMove()
        for (unit in unitsReadyToMove.toList()) {
            if (!unit.isReadyToMove || !isAlive(unit)) continue
            val province = game.getProvinceByHex(unit.currentHex) ?: continue
            if (province.hexList.size > 20) moveAfkUnit(province, unit)
        }
    }

    protected open fun moveAfkUnit(province: Province, unit: GameUnit) {
        val moveZone = game.detectMoveZone(unit.currentHex, unit.strength, Rules.UNIT_MOVE_LIMIT)
        excludeFriendlyUnitsFromMoveZone(moveZone)
        excludeFriendlyBuildingsFromMoveZone(moveZone)
        if (moveZone.isEmpty()) return
        game.moveUnit(unit, moveZone[random.nextInt(moveZone.size)], province)
    }

    protected open fun mergeUnits(province: Province) {
        var i = 0
        while (i < province.hexList.size) {
            val u = province.hexList[i].unit
            if (u != null && u.isReadyToMove) tryToMergeWithSomeone(province, u)
            i++
        }
    }

    private fun tryToMergeWithSomeone(province: Province, unit: GameUnit) {
        val moveZone = game.detectMoveZone(unit.currentHex, unit.strength, Rules.UNIT_MOVE_LIMIT)
        for (hex in moveZone) {
            if (!mergeConditions(province, unit, hex)) continue
            game.moveUnit(unit, hex, province)
            break
        }
    }

    protected open fun mergeConditions(province: Province, unit: GameUnit, hex: Hex): Boolean {
        val other = hex.unit ?: return false
        if (!hex.sameFraction(unit.currentHex)) return false
        if (!other.isReadyToMove) return false
        if (unit === other) return false
        if (!game.canMergeUnits(unit.strength, other.strength)) return false
        if (!province.canAiAffordUnit(unit.strength + other.strength)) return false
        return true
    }

    protected open fun spendMoney(province: Province) {
        tryToBuildTowers(province)
        tryToBuildUnits(province)
    }

    protected open fun tryToBuildTowers(province: Province) {
        while (province.hasMoneyForTower()) {
            val hex = findHexThatNeedsTower(province) ?: return
            game.buildTower(province, hex)
        }
    }

    protected fun findHexThatNeedsTower(province: Province): Hex? = province.hexList.firstOrNull { needTowerOnHex(it) }

    protected open fun needTowerOnHex(hex: Hex): Boolean {
        if (!hex.active || !hex.isFree()) return false
        return getPredictedDefenseGainByNewTower(hex) >= 5
    }

    protected fun getPredictedDefenseGainByNewTower(hex: Hex): Int {
        var c = 0
        if (hex.active && !hex.isDefendedByTower()) c++
        for (i in 0 until 6) {
            val adj = hex.adjacent(i)
            if (adj.active && hex.sameFraction(adj) && !adj.isDefendedByTower()) c++
            if (adj.containsTower()) c--
        }
        return c
    }

    protected fun updateNearbyProvinces(src: Province) {
        nearbyProvinces.clear()
        for (hex in src.hexList) for (i in 0 until 6) checkToAddNearbyProvince(hex, hex.adjacent(i))
    }

    protected fun updateNearbyProvinces(src: Hex) {
        nearbyProvinces.clear()
        for (i in 0 until 6) {
            val adj = src.adjacent(i)
            if (!adj.active) continue
            val adj2 = adj.adjacent(i)
            val adj3 = adj.adjacent((i + 1) % 6)
            checkToAddNearbyProvince(src, adj)
            checkToAddNearbyProvince(src, adj2)
            checkToAddNearbyProvince(src, adj3)
        }
    }

    private fun checkToAddNearbyProvince(src: Hex, adj: Hex) {
        if (!adj.active || adj.isNeutral || adj.sameFraction(src)) return
        val p = game.getProvinceByHex(adj) ?: return
        if (!nearbyProvinces.contains(p)) nearbyProvinces.add(0, p)
    }

    protected fun tryToBuiltUnitInsideProvince(province: Province, strength: Int): Boolean {
        for (hex in province.hexList.toList()) {
            if (!hex.nothingBlocksWayForUnit()) continue
            buildUnit(province, hex, strength)
            return true
        }
        return false
    }

    protected fun buildUnit(province: Province, hex: Hex, strength: Int) {
        game.buildUnit(province, hex, strength)
    }

    protected fun tryToAttackWithStrength(province: Province, strength: Int): Boolean {
        val moveZone = game.detectMoveZone(province.getCapital(), strength)
        val attackable = findAttackableHexes(province.fraction, moveZone)
        if (attackable.isEmpty()) return false
        val best = findMostAttractiveHex(attackable, province, strength) ?: return false
        buildUnit(province, best, strength)
        return true
    }

    protected fun tryToBuildUnitsOnPalms(province: Province) {
        if (!province.canAiAffordUnit(1)) return
        while (canProvinceBuildUnit(province, 1)) {
            val moveZone = game.detectMoveZone(province.getCapital(), 1)
            var killedPalm = false
            for (hex in moveZone) {
                if (hex.objectInside != Obj.PALM || !hex.sameFraction(province)) continue
                buildUnit(province, hex, 1)
                killedPalm = true
            }
            if (!killedPalm) break
        }
    }

    protected fun canProvinceBuildUnit(province: Province, strength: Int) = province.canBuildUnit(strength)

    protected open fun tryToBuildUnits(province: Province) {
        tryToBuildUnitsOnPalms(province)
        for (i in 1..4) {
            if (!province.canAiAffordUnit(i)) break
            while (canProvinceBuildUnit(province, i)) {
                if (!tryToAttackWithStrength(province, i)) break
            }
        }
        if (canProvinceBuildUnit(province, 1) && howManyUnitsInProvince(province) <= 1) {
            tryToAttackWithStrength(province, 1)
        }
    }

    protected fun checkToCleanSomeTrees(unit: GameUnit, moveZone: List<Hex>, province: Province?): Boolean {
        for (hex in moveZone) {
            if (hex.containsTree() && hex.sameFraction(unit.currentHex)) {
                game.moveUnit(unit, hex, province)
                return true
            }
        }
        return false
    }

    protected fun checkToCleanSomePalms(unit: GameUnit, moveZone: List<Hex>, province: Province?): Boolean {
        for (hex in moveZone) {
            if (hex.objectInside == Obj.PALM && hex.sameFraction(unit.currentHex)) {
                game.moveUnit(unit, hex, province)
                return true
            }
        }
        return false
    }

    protected open fun decideAboutUnit(unit: GameUnit, moveZone: ArrayList<Hex>, province: Province?) {
        if (unit.strength <= 2 && checkToCleanSomePalms(unit, moveZone, province)) return
        val attackable = findAttackableHexes(unit.fraction, moveZone)
        if (attackable.isNotEmpty() && province != null) {
            val target = findMostAttractiveHex(attackable, province, unit.strength) ?: return
            game.moveUnit(unit, target, province)
        } else {
            if (!checkToCleanSomeTrees(unit, moveZone, province) && unit.currentHex.isInPerimeter() && province != null) {
                pushUnitToBetterDefense(unit, province)
            }
        }
    }

    protected fun checkChance(chance: Double) = random.nextDouble() < chance

    protected open fun pushUnitToBetterDefense(unit: GameUnit, province: Province) {
        for (i in 0 until 6) {
            val adj = unit.currentHex.adjacent(i)
            if (adj.active && adj.sameFraction(unit.currentHex) && adj.isFree() && adj.howManyEnemyHexesNear() == 0) {
                game.moveUnit(unit, adj, province)
                break
            }
        }
    }

    protected open fun getAttackAllure(hex: Hex, fraction: Int): Int {
        var c = 0
        for (i in 0 until 6) {
            val adj = hex.adjacent(i)
            if (adj.active && adj.sameFraction(fraction)) c++
        }
        return c
    }

    protected fun findHexAttractiveToBaron(attackable: List<Hex>, strength: Int): Hex? {
        for (h in attackable) {
            if (h.objectInside == Obj.TOWER) return h
            if (strength == 4 && h.objectInside == Obj.STRONG_TOWER) return h
        }
        for (h in attackable) if (h.isDefendedByTower()) return h
        return null
    }

    protected open fun findMostAttractiveHex(attackable: List<Hex>, province: Province, strength: Int): Hex? =
        findMostAttractiveHexFor(attackable, province.fraction, strength)

    protected fun findMostAttractiveHexFor(attackable: List<Hex>, fraction: Int, strength: Int): Hex? {
        if (strength == 3 || strength == 4) findHexAttractiveToBaron(attackable, strength)?.let { return it }
        var result: Hex? = null
        var currMax = -1
        for (h in attackable) {
            val n = getAttackAllure(h, fraction)
            if (n > currMax) {
                currMax = n
                result = h
            }
        }
        return result
    }

    protected open fun findAttackableHexes(attackerFraction: Int, moveZone: List<Hex>): ArrayList<Hex> =
        ArrayList(moveZone.filter { it.fraction != attackerFraction })

    private fun excludeFriendlyBuildingsFromMoveZone(moveZone: ArrayList<Hex>) {
        moveZone.removeAll { it.sameFraction(fraction) && it.containsBuilding() }
    }

    private fun excludeFriendlyUnitsFromMoveZone(moveZone: ArrayList<Hex>) {
        moveZone.removeAll { it.sameFraction(fraction) && it.containsUnit() }
    }

    protected fun howManyUnitsInProvince(province: Province) = province.hexList.count { it.containsUnit() }

    override fun toString() = "[AI: $fraction]"
}

/** Port of ai.ArtificialIntelligenceGeneric: adds farms. */
abstract class ArtificialIntelligenceGeneric(game: Game, fraction: Int) : ArtificialIntelligence(game, fraction) {

    override fun spendMoney(province: Province) {
        tryToBuildTowers(province)
        tryToBuildFarms(province)
        tryToBuildUnits(province)
    }

    private fun tryToBuildFarms(province: Province) {
        if (province.extraFarmCost > MAX_EXTRA_FARM_COST) return
        while (province.hasMoneyForFarm()) {
            if (!isOkToBuildNewFarm(province)) return
            val hex = findGoodHexForFarm(province) ?: return
            if (!game.buildFarm(province, hex)) return
        }
    }

    protected open fun isOkToBuildNewFarm(src: Province): Boolean {
        if (src.money > 2 * src.currentFarmPrice) return true
        if (findHexThatNeedsTower(src) != null) return false
        return true
    }

    protected fun getArmyStrength(province: Province) = province.hexList.sumOf { it.unit?.strength ?: 0 }

    private fun findGoodHexForFarm(province: Province): Hex? {
        val good = province.hexList.filter { isHexGoodForFarm(it) }
        if (good.isEmpty()) return null
        return good[random.nextInt(good.size)]
    }

    private fun isHexGoodForFarm(hex: Hex): Boolean {
        if (!hex.isFree()) return false
        return hex.hasThisObjectNearby(Obj.TOWN) || hex.hasThisObjectNearby(Obj.FARM)
    }

    companion object {
        const val MAX_EXTRA_FARM_COST = 80
    }
}
