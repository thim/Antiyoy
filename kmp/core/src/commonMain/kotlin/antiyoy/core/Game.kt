package antiyoy.core

import antiyoy.core.ai.ArtificialIntelligence
import antiyoy.core.ai.createAi
import kotlin.random.Random

data class GameConfig(
    /** Number of colors on the map (2..7). */
    val fractionsQuantity: Int = 5,
    /** How many of the first fractions are controlled by humans (hotseat). 0 = AI only. */
    val playersNumber: Int = 1,
    val difficulty: Int = Difficulty.NORMAL,
    val levelSize: Int = LevelSize.MEDIUM,
    val seed: Long = 0L,
    /** Overrides how many provinces each fraction gets at start (0 = by level size). */
    val genProvinces: Int = 0,
) {
    init {
        require(fractionsQuantity in 2..Rules.MAX_PLAYABLE_FRACTIONS) { "fractionsQuantity must be 2..${Rules.MAX_PLAYABLE_FRACTIONS}" }
        require(playersNumber in 0..fractionsQuantity)
    }
}

/** Events the view layer can use for sounds and effects. */
interface GameListener {
    fun onUnitMoved(unit: GameUnit, from: Hex, to: Hex, attack: Boolean) {}
    fun onBuilt(hex: Hex, what: Int) {}
    fun onUnitBuilt(hex: Hex, strength: Int, attack: Boolean) {}
    fun onTurnStarted(fraction: Int) {}
    fun onGameEnded(winner: Int) {}
}

/**
 * Rules and state of one match: the logical part of FieldManager, GameController and RulesetGeneric.
 * Pure Kotlin, no rendering — shared by every platform.
 */
class Game(val config: GameConfig) {
    val field = Field()
    val provinces = ArrayList<Province>()
    val units = ArrayList<GameUnit>()

    /** General purpose random (gameController.random). */
    val random = Random(config.seed)

    /** Random used by map generation and capital placement (gameController.predictableRandom). */
    internal var predictableRandom = Random(config.seed * 31 + 7)

    val fractionsQuantity get() = config.fractionsQuantity
    val playersNumber get() = config.playersNumber

    var turn = 0
        private set
    var turnsMade = 0
        private set
    var winner = -1
        private set
    val isGameOver get() = winner >= 0

    var listener: GameListener? = null

    /** Bumped on every state change; views redraw when it changes. */
    var version = 0
        private set

    val boundWidth: Float
    val boundHeight: Float

    private val ais: List<ArtificialIntelligence>

    init {
        val w = VirtualScreen.WIDTH
        val h = VirtualScreen.HEIGHT
        when (config.levelSize) {
            LevelSize.SMALL -> { boundWidth = w; boundHeight = h }
            LevelSize.MEDIUM -> { boundWidth = 2 * w; boundHeight = h }
            LevelSize.BIG -> { boundWidth = 2 * w; boundHeight = 2 * h }
            LevelSize.HUGE -> { boundWidth = 3 * w; boundHeight = 3 * h }
            else -> error("Unknown level size ${config.levelSize}")
        }
        ais = (0 until fractionsQuantity).map { createAi(this, it, config.difficulty) }
    }

    fun notifyChanged() {
        version++
    }

    // ---------------------------------------------------------------- creation

    fun generate() {
        MapGeneratorGeneric(this, predictableRandom).generateMap()
        detectProvinces()
        detectNeutralLands()
        takeAwaySomeMoneyToAchieveBalance()
        onEndCreation()
    }

    private fun onEndCreation() {
        for (row in field.cells) for (hex in row) {
            hex.canContainObjects = isPointInsideLevelBoundsHorizontally(hex.x)
        }
        turn = 0
        prepareCertainUnitsToMove()
        notifyChanged()
    }

    private fun isPointInsideLevelBoundsHorizontally(x: Float): Boolean {
        if (x < field.fieldPosX + field.hexSize / 2) return false
        if (x > field.fieldPosX + boundWidth) return false
        return true
    }

    private fun detectNeutralLands() {
        for (h in field.activeHexes) h.genFlag = false
        for (p in provinces) for (h in p.hexList) h.genFlag = true
        for (h in field.activeHexes) if (!h.genFlag) h.setFractionTracked(Rules.NEUTRAL_FRACTION)
    }

    private fun takeAwaySomeMoneyToAchieveBalance() {
        for (p in provinces) {
            if (p.fraction == 0) continue // first player is not getting income at first turn
            p.money -= p.income - p.taxes
        }
    }

    internal fun detectProvinces() {
        provinces.clear()
        unFlag(field.activeHexes)
        for (hex in field.activeHexes) {
            if (hex.isNeutral || hex.flag) continue
            val group = propagate(hex)
            if (group.size >= 2) addProvince(Province(this, group))
        }
        for (p in provinces) if (!p.hasCapital()) p.placeCapitalInRandomPlace(predictableRandom)
    }

    private fun propagate(start: Hex): ArrayList<Hex> {
        val result = ArrayList<Hex>()
        val queue = ArrayDeque<Hex>()
        queue.add(start)
        start.flag = true
        while (queue.isNotEmpty()) {
            val h = queue.removeFirst()
            result.add(h)
            for (dir in 0 until 6) {
                val adj = h.adjacent(dir)
                if (!adj.active || !adj.sameFraction(h) || adj.flag) continue
                queue.add(adj)
                adj.flag = true
            }
        }
        return result
    }

    internal fun unFlag(list: List<Hex>) {
        for (i in list.indices.reversed()) {
            list[i].flag = false
            list[i].inMoveZone = false
        }
    }

    // ---------------------------------------------------------------- ruleset (generic)

    fun getHexIncome(hex: Hex): Int {
        if (hex.containsTree()) return 0
        if (hex.objectInside == Obj.FARM) return Rules.FARM_INCOME + 1
        return 1
    }

    fun getHexTax(hex: Hex): Int {
        hex.unit?.let { return getUnitTax(it.strength) }
        if (hex.objectInside == Obj.TOWER) return Rules.TAX_TOWER
        if (hex.objectInside == Obj.STRONG_TOWER) return Rules.TAX_STRONG_TOWER
        return 0
    }

    fun getUnitTax(strength: Int) = Rules.TAX_UNIT[strength.coerceIn(1, 4)]

    fun canUnitAttackHex(unitStrength: Int, hex: Hex): Boolean {
        if (unitStrength == 4) return true
        return unitStrength > hex.getDefenseNumber()
    }

    fun canMergeUnits(strength1: Int, strength2: Int) = strength1 + strength2 <= 4

    private fun canSpawnPineOnHex(hex: Hex) =
        hex.isFree() && hex.howManyTreesNearby() >= 2 && hex.hasPineReadyToExpandNearby() && random.nextDouble() < 0.2

    private fun canSpawnPalmOnHex(hex: Hex) =
        hex.isFree() && hex.isNearWater() && hex.hasPalmReadyToExpandNearby() && random.nextDouble() < 0.3

    private fun rewardForTree(hex: Hex) {
        if (!hex.containsTree()) return
        getProvinceByHex(hex)?.let { it.money += Rules.TREE_CUT_REWARD }
    }

    // ---------------------------------------------------------------- queries

    fun isPlayerTurn(t: Int = turn) = t < playersNumber
    fun isCurrentTurn(fraction: Int) = turn == fraction

    fun getProvinceByHex(hex: Hex): Province? = provinces.firstOrNull { it.containsHex(hex) }

    fun numberOfProvincesWithFraction(fraction: Int) = provinces.count { it.fraction == fraction }

    fun detectMoveZone(startHex: Hex, strength: Int, moveLimit: Int = 9001): ArrayList<Hex> =
        MoveZoneDetection.detect(this, startHex, strength, moveLimit)

    fun hexCountByFraction(): IntArray {
        val counts = IntArray(fractionsQuantity)
        for (h in field.activeHexes) {
            if (h.isNeutral) continue
            if (h.isInProvince() && h.fraction in counts.indices) counts[h.fraction]++
        }
        return counts
    }

    fun isThereOnlyOneKingdomOnMap(): Boolean {
        var fraction = -1
        for (p in provinces) {
            if (p.hexList[0].isNeutral) continue
            if (fraction == -1) {
                fraction = p.fraction
                continue
            }
            if (p.fraction != fraction) return false
        }
        return true
    }

    // ---------------------------------------------------------------- field mutation

    internal fun addSolidObject(hex: Hex, type: Int) {
        if (!hex.active) return
        if (hex.objectInside == type) return
        if (!hex.canContainObjects) return
        if (hex.containsObject()) cleanOutHex(hex)
        hex.objectInside = type
    }

    internal fun cleanOutHex(hex: Hex) {
        hex.unit?.let { units.remove(it) }
        hex.unit = null
        hex.objectInside = Obj.NONE
    }

    internal fun spawnTree(hex: Hex) {
        if (!hex.active) return
        addSolidObject(hex, if (hex.isNearWater()) Obj.PALM else Obj.PINE)
    }

    private fun destroyBuildingsOnHex(hex: Hex) {
        val hadHouse = hex.objectInside == Obj.TOWN
        if (hex.containsBuilding()) cleanOutHex(hex)
        if (hadHouse) spawnTree(hex)
    }

    private fun killUnitByStarvation(hex: Hex) {
        cleanOutHex(hex)
        addSolidObject(hex, Obj.GRAVE)
    }

    private fun spawnUnitOnHex(hex: Hex, strength: Int): GameUnit {
        val unit = GameUnit(hex, strength)
        hex.unit = unit
        units.add(unit)
        return unit
    }

    internal fun addUnit(hex: Hex, strength: Int): GameUnit {
        if (hex.containsObject()) {
            rewardForTree(hex)
            cleanOutHex(hex)
            spawnUnitOnHex(hex, strength)
        } else {
            val unit = spawnUnitOnHex(hex, strength)
            if (isCurrentTurn(hex.fraction)) unit.isReadyToMove = true
        }
        return hex.unit!!
    }

    private fun addProvince(province: Province) {
        if (provinces.contains(province)) return
        if (provinces.any { it.isSameAs(province) }) return
        provinces.add(province)
    }

    private fun removeProvince(province: Province) {
        provinces.remove(province)
    }

    internal fun setHexFraction(hex: Hex, fraction: Int) {
        cleanOutHex(hex)
        val previous = hex.fraction
        hex.setFractionTracked(fraction)
        splitProvince(hex, previous)
        checkToUniteProvinces(hex)
        joinHexToAdjacentProvince(hex)
    }

    private fun splitProvince(hex: Hex, fraction: Int) {
        val oldProvince = getProvinceByHex(hex) ?: return
        unFlag(oldProvince.hexList)
        val added = ArrayList<Province>()
        hex.flag = true
        predictableRandom = Random(hex.index1 + hex.index2)
        for (k in 0 until 6) {
            val start = hex.adjacent(k)
            if (!start.active || start.fraction != fraction || start.flag) continue
            val group = propagate(start)
            if (group.size >= 2) {
                val province = Province(this, group)
                province.money = 0
                if (!province.hasCapital()) province.placeCapitalInRandomPlace(predictableRandom)
                addProvince(province)
                added.add(province)
            } else {
                destroyBuildingsOnHex(start)
            }
        }
        if (added.isNotEmpty() && hex.objectInside != Obj.TOWN) {
            added.maxByOrNull { it.hexList.size }!!.money = oldProvince.money
        }
        removeProvince(oldProvince)
    }

    private fun checkToUniteProvinces(hex: Hex) {
        val adjacentProvinces = ArrayList<Province>()
        for (i in 0 until 6) {
            val p = getProvinceByHex(hex.adjacent(i)) ?: continue
            if (hex.sameFraction(p) && !adjacentProvinces.contains(p)) adjacentProvinces.add(p)
        }
        if (adjacentProvinces.size < 2) return
        var sum = 0
        val capital = maxProvince(adjacentProvinces).getCapital()
        val hexes = ArrayList<Hex>()
        for (p in adjacentProvinces) {
            sum += p.money
            hexes.addAll(p.hexList)
            removeProvince(p)
        }
        val united = Province(this, hexes)
        united.money = sum
        united.setCapital(capital)
        addProvince(united)
    }

    /** Mirrors FieldManager.getMaxProvinceFromList (iterates backwards, strict >). */
    private fun maxProvince(list: List<Province>): Province {
        var max = list[0]
        for (k in list.indices.reversed()) if (list[k].hexList.size > max.hexList.size) max = list[k]
        return max
    }

    private fun joinHexToAdjacentProvince(hex: Hex) {
        for (i in 0 until 6) {
            val p = getProvinceByHex(hex.adjacent(i)) ?: continue
            if (!hex.sameFraction(p)) continue
            p.addHex(hex)
            for (j in 0 until 6) {
                val h = hex.adjacent(j)
                if (h.active && h.sameFraction(hex) && getProvinceByHex(h) == null) p.addHex(h)
            }
            return
        }
    }

    // ---------------------------------------------------------------- actions

    fun buildUnit(province: Province, hex: Hex, strength: Int): Boolean {
        if (!province.canBuildUnit(strength)) return false
        if (hex.sameFraction(province) && hex.unit != null && !canMergeUnits(strength, hex.unit!!.strength)) return false
        province.money -= Rules.PRICE_UNIT * strength
        val attack = !hex.sameFraction(province)
        if (!attack) {
            val existing = hex.unit
            if (existing == null) {
                addUnit(hex, strength)
            } else {
                val newUnit = GameUnit(hex, strength)
                newUnit.isReadyToMove = true
                mergeUnits(hex, newUnit, existing)
            }
        } else {
            setHexFraction(hex, province.fraction) // must be called before object in hex destroyed
            val unit = addUnit(hex, strength)
            unit.isReadyToMove = false
            province.addHex(hex)
        }
        listener?.onUnitBuilt(hex, strength, attack)
        notifyChanged()
        return true
    }

    private fun buildObject(province: Province, hex: Hex, type: Int, price: Int): Boolean {
        if (province.money < price) return false
        if (type == Obj.PINE) spawnTree(hex) else addSolidObject(hex, type)
        province.money -= price
        listener?.onBuilt(hex, type)
        notifyChanged()
        return true
    }

    fun buildTower(province: Province, hex: Hex) = buildObject(province, hex, Obj.TOWER, Rules.PRICE_TOWER)

    fun buildStrongTower(province: Province, hex: Hex) =
        buildObject(province, hex, Obj.STRONG_TOWER, Rules.PRICE_STRONG_TOWER)

    fun buildFarm(province: Province, hex: Hex): Boolean {
        if (!canBuildFarmOnHex(hex)) return false
        return buildObject(province, hex, Obj.FARM, province.currentFarmPrice)
    }

    fun buildTree(province: Province, hex: Hex) = buildObject(province, hex, Obj.PINE, Rules.PRICE_TREE)

    fun canBuildFarmOnHex(hex: Hex) = hex.hasThisObjectNearby(Obj.TOWN) || hex.hasThisObjectNearby(Obj.FARM)

    fun mergeUnits(hex: Hex, unit1: GameUnit, unit2: GameUnit): Boolean {
        if (!canMergeUnits(unit1.strength, unit2.strength)) return false
        cleanOutHex(unit1.currentHex)
        cleanOutHex(unit2.currentHex)
        val merged = addUnit(hex, unit1.strength + unit2.strength)
        merged.isReadyToMove = unit1.isReadyToMove && unit2.isReadyToMove
        merged.lastHex = unit1.lastHex
        notifyChanged()
        return true
    }

    fun canUnitMoveToFriendlyHex(unit: GameUnit, hex: Hex): Boolean {
        if (hex === unit.currentHex) return false
        if (hex.containsBuilding()) return false
        val other = hex.unit
        if (other != null && !canMergeUnits(unit.strength, other.strength)) return false
        return true
    }

    private fun unitMoveToHex(unit: GameUnit, target: Hex): Boolean {
        if (target.sameFraction(unit.currentHex) && target.containsBuilding()) return false
        rewardForTree(target)
        if (target.containsObject()) cleanOutHex(target) // unit crushes object
        unit.isReadyToMove = false
        unit.lastHex = unit.currentHex
        unit.currentHex = target
        unit.moveCounter++
        unit.lastHex.unit = null
        target.unit = unit
        return true
    }

    fun moveUnit(unit: GameUnit, target: Hex, unitProvince: Province?) {
        if (!unit.isReadyToMove) return
        val from = unit.currentHex
        if (unit.currentHex.sameFraction(target)) {
            val other = target.unit
            if (other == null) {
                unitMoveToHex(unit, target)
                listener?.onUnitMoved(unit, from, target, false)
            } else {
                mergeUnits(target, unit, other)
            }
        } else {
            if (!canUnitAttackHex(unit.strength, target) || unitProvince == null) return
            setHexFraction(target, turn) // must be called before object in hex destroyed
            cleanOutHex(target)
            unitMoveToHex(unit, target)
            unitProvince.addHex(target)
            listener?.onUnitMoved(unit, from, target, true)
        }
        notifyChanged()
    }

    // ---------------------------------------------------------------- turns

    private fun prepareCertainUnitsToMove() {
        for (u in units) if (isCurrentTurn(u.fraction)) u.isReadyToMove = true
    }

    private fun nextTurnIndex(current: Int): Int {
        var next = current + 1
        if (next == Rules.NEUTRAL_FRACTION) next++
        if (next >= fractionsQuantity) next = 0
        return next
    }

    /** Ends the current fraction's turn and starts the next one. */
    fun endTurn() {
        if (isGameOver) return
        checkToEndGame()
        if (isGameOver) return
        for (u in units) u.isReadyToMove = false
        turn = nextTurnIndex(turn)
        turnStartActions()
        notifyChanged()
    }

    private fun turnStartActions() {
        if (isCurrentTurn(0)) {
            turnsMade++
            expandTrees()
        }
        prepareCertainUnitsToMove()
        transformGraves() // must be called before 'check for bankrupts' and after 'expand trees'
        collectTributesAndPayTaxes()
        checkForBankrupts()
        checkForAloneUnits()
        listener?.onTurnStarted(turn)
    }

    private fun expandTrees() {
        val newPalms = field.activeHexes.filter { canSpawnPalmOnHex(it) }
        val newPines = field.activeHexes.filter { canSpawnPineOnHex(it) }
        for (i in newPalms.indices.reversed()) {
            val h = newPalms[i]
            if (h.canContainObjects) addSolidObject(h, Obj.PALM)
        }
        for (i in newPines.indices.reversed()) {
            val h = newPines[i]
            if (h.canContainObjects) addSolidObject(h, Obj.PINE)
        }
        for (h in field.activeHexes) {
            if (h.containsTree() && h.blockToTreeFromExpanding) h.blockToTreeFromExpanding = false
        }
    }

    private fun transformGraves() {
        for (h in field.activeHexes) {
            if (isCurrentTurn(h.fraction) && h.objectInside == Obj.GRAVE) {
                spawnTree(h)
                h.blockToTreeFromExpanding = true
            }
        }
    }

    private fun collectTributesAndPayTaxes() {
        for (p in provinces) if (isCurrentTurn(p.fraction)) p.money += p.profit
    }

    private fun checkForBankrupts() {
        for (p in provinces) {
            if (!isCurrentTurn(p.fraction) || p.money >= 0) continue
            p.money = 0
            for (h in p.hexList) if (h.containsUnit()) killUnitByStarvation(h)
        }
    }

    private fun checkForAloneUnits() {
        var i = 0
        while (i < units.size) {
            val u = units[i]
            if (isCurrentTurn(u.fraction) && u.currentHex.numberOfFriendlyHexesNearby() == 0) {
                killUnitByStarvation(u.currentHex)
                continue
            }
            i++
        }
    }

    private fun checkIfWeHaveWinner(): Int {
        if (field.activeHexes.isEmpty()) return -1
        if (!isThereOnlyOneKingdomOnMap()) return -1
        return provinces.firstOrNull { !it.hexList[0].isNeutral }?.fraction ?: -1
    }

    /** Checks the victory condition; returns the winner or -1. */
    fun checkToEndGame(): Int {
        if (isGameOver) return winner
        val w = checkIfWeHaveWinner()
        if (w >= 0) {
            winner = w
            listener?.onGameEnded(w)
            notifyChanged()
        }
        return winner
    }

    /** Runs the AI for the current (non-human) fraction. */
    fun performAiTurn() {
        if (isPlayerTurn() || isGameOver) return
        ais[turn].perform()
        notifyChanged()
    }

    /** True when it's a human's turn but that human has nothing left to play with. */
    fun currentHumanIsEliminated() = isPlayerTurn() && numberOfProvincesWithFraction(turn) == 0

    fun isFractionAlive(fraction: Int) = numberOfProvincesWithFraction(fraction) > 0

    // ---------------------------------------------------------------- snapshots (undo)

    fun takeSnapshot(): Snapshot = Snapshot.capture(this)

    fun restore(snapshot: Snapshot) {
        snapshot.restore(this)
        notifyChanged()
    }

    internal fun restoreInternals(turn: Int) {
        this.turn = turn
    }

    internal fun newProvince(hexes: List<Hex>, money: Int): Province =
        Province(this, hexes).also { it.money = money }

    internal fun putUnit(hex: Hex, strength: Int, ready: Boolean) {
        val u = spawnUnitOnHex(hex, strength)
        u.isReadyToMove = ready
    }

    companion object {
        fun create(config: GameConfig): Game = Game(config).also { it.generate() }
    }
}
