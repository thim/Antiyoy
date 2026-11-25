package antiyoy.core

/** Objects that can sit on a hex (port of yio.tro.antiyoy.gameplay.Obj). */
object Obj {
    const val NONE = 0
    const val PINE = 1
    const val PALM = 2
    const val TOWN = 3
    const val TOWER = 4
    const val GRAVE = 5
    const val FARM = 6
    const val STRONG_TOWER = 7
}

/** Constants of the "generic" (Antiyoy) ruleset, port of GameRules. */
object Rules {
    const val MAX_FRACTIONS_QUANTITY = 11
    const val NEUTRAL_FRACTION = 7
    const val UNIT_MOVE_LIMIT = 4

    const val PRICE_UNIT = 10
    const val PRICE_TOWER = 15
    const val PRICE_FARM = 12
    const val PRICE_STRONG_TOWER = 35
    const val PRICE_TREE = 10

    const val FARM_INCOME = 4
    const val TREE_CUT_REWARD = 3

    const val TAX_TOWER = 1
    const val TAX_STRONG_TOWER = 6
    val TAX_UNIT = intArrayOf(0, 2, 6, 18, 36)

    const val DEFAULT_PROVINCE_MONEY = 10

    /** Maximum number of colors supported by the generic map generator balance table. */
    const val MAX_PLAYABLE_FRACTIONS = 7
}

object LevelSize {
    const val SMALL = 1
    const val MEDIUM = 2
    const val BIG = 4
    const val HUGE = 9
    val ALL = intArrayOf(SMALL, MEDIUM, BIG, HUGE)
}

object Difficulty {
    const val EASY = 0
    const val NORMAL = 1
    const val HARD = 2
    const val EXPERT = 3
    const val BALANCER = 4
}

/**
 * The original game derived all field geometry from the phone screen size.
 * We keep a fixed virtual portrait screen so that map generation behaves the same way.
 */
object VirtualScreen {
    const val WIDTH = 720f
    const val HEIGHT = 1280f
}
