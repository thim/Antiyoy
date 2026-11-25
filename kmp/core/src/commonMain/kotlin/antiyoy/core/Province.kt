package antiyoy.core

import kotlin.random.Random

/** A connected group of hexes of one fraction with a shared treasury. Port of gameplay.Province. */
class Province internal constructor(private val game: Game, hexes: List<Hex>) {
    var money = Rules.DEFAULT_PROVINCE_MONEY
    val hexList: ArrayList<Hex> = ArrayList(hexes)

    val fraction: Int get() = if (hexList.isEmpty()) -1 else hexList[0].fraction

    internal fun placeCapitalInRandomPlace(random: Random) {
        val place = getFreeHex(random) ?: getAnyHexExceptTowers() ?: getRandomHex()
        game.cleanOutHex(place)
        game.addSolidObject(place, Obj.TOWN)
        place.previousFraction = place.fraction
        game.notifyChanged()
    }

    fun hasCapital() = hexList.any { it.objectInside == Obj.TOWN }

    fun getCapital(): Hex = hexList.firstOrNull { it.objectInside == Obj.TOWN } ?: hexList[0]

    fun getRandomHex(): Hex = hexList[game.random.nextInt(hexList.size)]

    private fun getAnyHexExceptTowers(): Hex? {
        val candidates = hexList.filter { !it.containsTower() }
        if (candidates.isEmpty()) return null
        return candidates[game.random.nextInt(candidates.size)]
    }

    private fun getFreeHex(random: Random): Hex? {
        val candidates = hexList.filter { it.isFree() }
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }

    val profit: Int get() = income - taxes

    val income: Int get() = hexList.sumOf { game.getHexIncome(it) }

    val taxes: Int get() = hexList.sumOf { game.getHexTax(it) }

    fun countObjects(objType: Int) = hexList.count { it.objectInside == objType }

    internal fun setCapital(hex: Hex) {
        for (h in hexList) if (h.objectInside == Obj.TOWN) game.cleanOutHex(h)
        game.addSolidObject(hex, Obj.TOWN)
    }

    fun hasSomeoneReadyToMove() = hexList.any { it.unit?.isReadyToMove == true }

    fun canAiAffordUnit(strength: Int, turnsToSurvive: Int = strength + 1): Boolean {
        val newIncome = profit - game.getUnitTax(strength)
        return money + turnsToSurvive * newIncome >= 0
    }

    fun canBuildUnit(strength: Int) = money >= Rules.PRICE_UNIT * strength

    fun hasMoneyForTower() = money >= Rules.PRICE_TOWER
    fun hasMoneyForFarm() = money >= currentFarmPrice
    fun hasMoneyForStrongTower() = money >= Rules.PRICE_STRONG_TOWER
    fun hasMoneyForTree() = money >= Rules.PRICE_TREE

    val currentFarmPrice: Int get() = Rules.PRICE_FARM + extraFarmCost
    val extraFarmCost: Int get() = 2 * countObjects(Obj.FARM)

    fun containsHex(hex: Hex) = hexList.contains(hex)

    internal fun addHex(hex: Hex) {
        if (containsHex(hex)) return
        hexList.add(0, hex)
    }

    fun isSameAs(other: Province) = hexList.all { other.containsHex(it) } && other.hexList.all { containsHex(it) }

    override fun toString() = "[Province($fraction) ${hexList.size} hexes, $money$]"
}
