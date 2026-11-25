package antiyoy.core.ai

import antiyoy.core.Game
import antiyoy.core.GameUnit
import antiyoy.core.Hex
import antiyoy.core.Obj
import antiyoy.core.Province
import antiyoy.core.Rules

/** Port of ai.AiEasy. */
class AiEasy(game: Game, fraction: Int) : ArtificialIntelligence(game, fraction) {
    override fun makeMove() {
        moveUnits()
        spendMoneyAndMergeUnits()
    }

    override fun decideAboutUnit(unit: GameUnit, moveZone: ArrayList<Hex>, province: Province?) {
        if (checkToCleanSomeTrees(unit, moveZone, province)) return
        game.moveUnit(unit, moveZone[random.nextInt(moveZone.size)], province)
    }

    override fun tryToBuildUnits(province: Province) {
        for (i in 1..4) {
            if (!province.canAiAffordUnit(i)) break
            while (canProvinceBuildUnit(province, i)) {
                if (!tryToBuiltUnitInsideProvince(province, i)) break
            }
        }
        if (canProvinceBuildUnit(province, 1) && howManyUnitsInProvince(province) <= 1) {
            tryToAttackWithStrength(province, 1)
        }
    }

    override fun tryToBuildTowers(province: Province) {
        // easy AI can't build towers
    }

    override fun mergeConditions(province: Province, unit: GameUnit, hex: Hex) =
        super.mergeConditions(province, unit, hex) && unit.strength == 1 && hex.unit?.strength == 1

    override fun mergeUnits(province: Province) {
        if (random.nextDouble() < 0.25) super.mergeUnits(province)
    }
}

/** Port of ai.AiNormalGenericRules. */
class AiNormal(game: Game, fraction: Int) : ArtificialIntelligenceGeneric(game, fraction) {
    override fun makeMove() {
        moveUnits()
        spendMoneyAndMergeUnits()
    }

    override fun decideAboutUnit(unit: GameUnit, moveZone: ArrayList<Hex>, province: Province?) {
        if (checkChance(0.5)) return
        super.decideAboutUnit(unit, moveZone, province)
    }

    override fun tryToBuildUnits(province: Province) {
        tryToBuildUnitsOnPalms(province)
        for (i in 1..4) {
            if (!province.canAiAffordUnit(i)) break
            while (canProvinceBuildUnit(province, i)) {
                if (!tryToBuiltUnitInsideProvince(province, i)) break
            }
        }
        if (canProvinceBuildUnit(province, 1) && howManyUnitsInProvince(province) <= 1) {
            tryToAttackWithStrength(province, 1)
        }
    }
}

/** Port of ai.AiHardGenericRules. */
class AiHard(game: Game, fraction: Int) : ArtificialIntelligenceGeneric(game, fraction) {
    override fun makeMove() {
        moveUnits()
        spendMoneyAndMergeUnits()
        moveAfkUnits()
    }

    override fun tryToBuildTowers(province: Province) {
        while (province.hasMoneyForTower()) {
            val hex = findHexThatNeedsTower(province) ?: return
            if (province.hasMoneyForStrongTower()) {
                game.buildStrongTower(province, hex)
                continue
            }
            game.buildTower(province, hex)
        }
    }
}

/** Port of ai.AiExpertGenericRules. */
open class AiExpert(game: Game, fraction: Int) : ArtificialIntelligenceGeneric(game, fraction) {
    override fun makeMove() {
        moveUnits()
        spendMoneyAndMergeUnits()
        moveAfkUnits()
    }

    override fun decideAboutUnit(unit: GameUnit, moveZone: ArrayList<Hex>, province: Province?) {
        if (unit.strength <= 2 && checkToCleanSomePalms(unit, moveZone, province)) return
        val attackable = findAttackableHexes(unit.fraction, moveZone)
        if (attackable.isNotEmpty()) {
            if (province != null) tryToAttackSomething(unit, province, attackable)
        } else {
            if (!checkToCleanSomeTrees(unit, moveZone, province) && unit.currentHex.isInPerimeter() && province != null) {
                pushUnitToBetterDefense(unit, province)
            }
        }
    }

    protected open fun isHexDefendedBySomethingElse(hex: Hex, unit: GameUnit): Boolean {
        for (i in 0 until 6) {
            val adj = hex.adjacent(i)
            if (adj.active && adj.sameFraction(hex)) {
                if (adj.containsBuilding()) return true
                val u = adj.unit
                if (u != null && u !== unit) return true
            }
        }
        return false
    }

    protected fun unitCanMoveSafely(unit: GameUnit): Boolean {
        var leftBehind = 0
        for (i in 0 until 6) {
            val adj = unit.currentHex.adjacent(i)
            if (adj.active && adj.sameFraction(unit.currentHex) && !isHexDefendedBySomethingElse(adj, unit) && adj.isInPerimeter()) {
                leftBehind++
            }
        }
        return leftBehind <= 3
    }

    protected open fun tryToAttackSomething(unit: GameUnit, province: Province, attackable: ArrayList<Hex>) {
        if (!unitCanMoveSafely(unit)) return
        val target = findMostAttractiveHex(attackable, province, unit.strength) ?: return
        game.moveUnit(unit, target, province)
    }

    private fun findRandomHexInPerimeter(province: Province): Hex? {
        val perimeter = province.hexList.filter { it.isInPerimeter() }
        if (perimeter.isEmpty()) return null
        return perimeter[random.nextInt(perimeter.size)]
    }

    override fun moveAfkUnit(province: Province, unit: GameUnit) {
        val target = findRandomHexInPerimeter(province) ?: return
        val before = unit.currentHex
        marchUnit(unit, target)
        if (before === unit.currentHex) super.moveAfkUnit(province, unit) // to prevent infinite loop
    }

    /** Port of MassMarchManager.performForSingleUnit. */
    private fun marchUnit(unit: GameUnit, target: Hex) {
        for (h in game.field.activeHexes) {
            h.algoLink = null
            h.algoValue = 0
        }
        val queue = ArrayDeque<Hex>()
        queue.add(target)
        target.algoLink = target
        target.algoValue = 0
        while (queue.isNotEmpty()) {
            val hex = queue.removeFirst()
            for (dir in 0 until 6) {
                val adj = hex.adjacent(dir)
                if (adj.isNullHex || !adj.active || adj.fraction != hex.fraction || adj.algoLink != null) continue
                queue.add(adj)
                adj.algoLink = hex
                adj.algoValue = hex.algoValue + 1
            }
        }
        if (unit.currentHex.algoLink == null) return
        val moveZone = game.detectMoveZone(unit.currentHex, unit.strength, Rules.UNIT_MOVE_LIMIT)
        var best: Hex? = null
        for (hex in moveZone) {
            if (!hex.isEmpty() || hex.fraction != unit.fraction) continue
            if (best == null || hex.algoValue < best.algoValue) best = hex
        }
        if (best != null) game.moveUnit(unit, best, game.getProvinceByHex(unit.currentHex))
    }

    override fun tryToBuildUnits(province: Province) {
        tryToBuildUnitsOnPalms(province)
        var i = 1
        var guard = 0
        while (i <= 4 && guard++ < 1000) {
            if (!province.canAiAffordUnit(i)) break
            var attacked = false
            if (canProvinceBuildUnit(province, i)) attacked = tryToAttackWithStrength(province, i)
            i = if (attacked) 1 else i + 1
        }
        if (canProvinceBuildUnit(province, 1) && howManyUnitsInProvince(province) <= 1) {
            tryToAttackWithStrength(province, 1)
        }
    }

    override fun tryToBuildTowers(province: Province) {
        while (province.hasMoneyForTower()) {
            val hex = findHexThatNeedsTower(province) ?: break
            if (!game.buildTower(province, hex)) break
        }
        while (provinceCanAffordStrongTower(province)) {
            val hex = findHexForStrongTower(province) ?: break
            if (!game.buildStrongTower(province, hex)) break
        }
    }

    private fun findHexForStrongTower(province: Province): Hex? =
        province.hexList.firstOrNull { it.objectInside == Obj.TOWER && needsStrongTowerOnHex(province, it) }

    private fun needsStrongTowerOnHex(province: Province, hex: Hex): Boolean {
        updateNearbyProvinces(hex)
        return nearbyProvinces.any { it.hexList.size > province.hexList.size / 2 }
    }

    private fun provinceCanAffordStrongTower(province: Province): Boolean {
        if (!province.hasMoneyForStrongTower()) return false
        if (province.profit - Rules.TAX_STRONG_TOWER < Rules.PRICE_UNIT / 2) return false
        return true
    }

    override fun needTowerOnHex(hex: Hex): Boolean {
        if (!hex.active || !hex.isFree()) return false
        return getPredictedDefenseGainByNewTower(hex) >= 4
    }
}

/** Port of ai.AiBalancerGenericRules. */
class AiBalancer(game: Game, fraction: Int) : AiExpert(game, fraction) {
    private var playerHexCount = IntArray(0)

    override fun makeMove() {
        moveUnits()
        spendMoneyAndMergeUnits()
        checkToKillRedundantUnits()
        moveAfkUnits()
    }

    private fun checkToKillRedundantUnits() {
        for (p in game.provinces.toList()) {
            if (!game.provinces.contains(p)) continue
            var detectedStrong = false
            var skip = false
            for (hex in p.hexList) {
                val u = hex.unit ?: continue
                if (!u.isReadyToMove) { skip = true; break }
                if (u.strength >= 3) detectedStrong = true
            }
            if (skip || !detectedStrong) continue
            killRedundantUnits(p)
        }
    }

    private fun killRedundantUnits(province: Province) {
        var guard = 0
        while (province.money >= Rules.PRICE_UNIT && province.profit >= 0 && guard++ < 100) {
            val unit = province.hexList.mapNotNull { it.unit }.filter { it.strength != 4 }.maxByOrNull { it.strength } ?: break
            if (!canProvinceBuildUnit(province, 1)) break
            if (!game.buildUnit(province, unit.currentHex, 1)) break
        }
    }

    override fun isOkToBuildNewFarm(src: Province): Boolean {
        if (src.money > 2 * src.currentFarmPrice) return true
        val srcArmy = getArmyStrength(src)
        updateNearbyProvinces(src)
        for (p in nearbyProvinces) {
            if (p === src) continue
            if (srcArmy < getArmyStrength(p) / 2) return false
        }
        if (findHexThatNeedsTower(src) != null) return false
        return true
    }

    override fun decideAboutUnit(unit: GameUnit, moveZone: ArrayList<Hex>, province: Province?) {
        if (!unit.isReadyToMove) return
        if (unit.strength <= 2 && checkToCleanSomePalms(unit, moveZone, province)) return
        if (checkToCleanSomeTrees(unit, moveZone, province)) return
        val attackable = findAttackableHexes(unit.fraction, moveZone)
        if (attackable.isNotEmpty()) {
            if (province != null) tryToAttackSomething(unit, province, attackable)
        } else if (unit.currentHex.isInPerimeter() && province != null) {
            pushUnitToBetterDefense(unit, province)
        }
    }

    override fun pushUnitToBetterDefense(unit: GameUnit, province: Province) {
        if (!unit.isReadyToMove) return
        for (i in 0 until 6) {
            val adj = unit.currentHex.adjacent(i)
            if (!adj.active || !adj.sameFraction(unit.currentHex) || !adj.isFree()) continue
            if (predictDefenseGainWithUnit(adj, unit) < 3) continue
            game.moveUnit(unit, adj, province)
            break
        }
    }

    private fun predictDefenseGainWithUnit(hex: Hex, unit: GameUnit): Int {
        var gain = -hex.getDefenseNumber() + unit.strength
        for (i in 0 until 6) {
            val adj = unit.currentHex.adjacent(i)
            if (!adj.active || !adj.sameFraction(unit.currentHex)) continue
            gain -= adj.getDefenseNumber()
            gain += unit.strength
        }
        return gain
    }

    override fun tryToAttackSomething(unit: GameUnit, province: Province, attackable: ArrayList<Hex>) {
        if (!unitCanMoveSafely(unit)) return
        val target = findMostAttractiveHexFor(attackable, unit.fraction, unit.strength) ?: return
        game.moveUnit(unit, target, province)
    }

    override fun getAttackAllure(hex: Hex, fraction: Int): Int {
        var c = 0
        for (i in 0 until 6) {
            val adj = hex.adjacent(i)
            if (adj.active && adj.sameFraction(fraction)) c++
            if (adj.active && adj.sameFraction(fraction) && adj.objectInside == Obj.TOWN) c += 5
        }
        if (hex.objectInside == Obj.FARM) c *= 2
        return c
    }

    override fun tryToBuildUnits(province: Province) {
        tryToBuildUnitsOnPalms(province)
        tryToReinforceUnits(province)
        for (i in 1..4) {
            if (!province.canAiAffordUnit(i, 5)) break
            while (canProvinceBuildUnit(province, i)) {
                if (!tryToAttackWithStrength(province, i)) break
            }
        }
        if (canProvinceBuildUnit(province, 1) && howManyUnitsInProvince(province) <= 1) {
            tryToAttackWithStrength(province, 1)
        }
    }

    private fun tryToReinforceUnits(province: Province) {
        for (hex in province.hexList.toList()) {
            val unit = hex.unit ?: continue
            if (unitHasToBeReinforced(unit) && province.canAiAffordUnit(unit.strength + 1)) {
                buildUnit(province, hex, 1)
            }
        }
    }

    private fun unitHasToBeReinforced(unit: GameUnit): Boolean {
        if (unit.strength == 4) return false
        val moveZone = game.detectMoveZone(unit.currentHex, unit.strength)
        if (moveZone.none { !it.sameFraction(unit.fraction) }) return false
        return findAttackableHexes(unit.fraction, moveZone).isEmpty()
    }

    override fun isHexDefendedBySomethingElse(hex: Hex, unit: GameUnit): Boolean {
        if (hex.getDefenseNumber(unit) == 0) return false
        return hex.getDefenseNumber() - hex.getDefenseNumber(unit) < 2
    }

    override fun findAttackableHexes(attackerFraction: Int, moveZone: List<Hex>): ArrayList<Hex> {
        val result = ArrayList(moveZone.filter { it.fraction != attackerFraction })
        playerHexCount = game.hexCountByFraction()
        result.sortWith { a, b ->
            val ad = unitsNearby(a)
            val bd = unitsNearby(b)
            if (ad == bd) getHexCount(b.fraction) - getHexCount(a.fraction) else bd - ad
        }
        return result
    }

    private fun unitsNearby(hex: Hex): Int {
        var c = 0
        for (i in 0 until 6) {
            val adj = hex.adjacent(i)
            if (!adj.active || !adj.sameFraction(fraction)) continue
            if (!adj.containsUnit() || !adj.containsTower()) continue
            c++
        }
        return c
    }

    private fun getHexCount(index: Int) = if (index in playerHexCount.indices) playerHexCount[index] else 0

    override fun needTowerOnHex(hex: Hex): Boolean {
        if (!hex.active || !hex.isFree()) return false
        updateNearbyProvinces(hex)
        if (nearbyProvinces.isEmpty()) return false // build towers only at front line
        return getPredictedDefenseGainByNewTower(hex) >= 3
    }
}
