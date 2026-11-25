package antiyoy.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class GameTest {

    private fun checkInvariants(game: Game) {
        val seen = HashSet<Hex>()
        for (p in game.provinces) {
            assertTrue(p.hexList.size >= 2, "province too small: $p")
            val f = p.fraction
            for (h in p.hexList) {
                assertTrue(h.active, "inactive hex in province")
                assertEquals(f, h.fraction, "mixed fractions in province")
                assertTrue(seen.add(h), "hex $h in two provinces")
            }
            assertTrue(p.countObjects(Obj.TOWN) <= 1, "province with ${p.countObjects(Obj.TOWN)} towns")
        }
        for (u in game.units) {
            assertTrue(u.currentHex.unit === u, "unit not on its hex: $u")
            assertTrue(u.strength in 1..4)
        }
        val onField = game.field.activeHexes.count { it.unit != null }
        assertEquals(game.units.size, onField, "unit list out of sync")
    }

    @Test
    fun generatesMapsForAllSizesAndFractions() {
        for (size in LevelSize.ALL) for (fractions in 2..7) {
            val game = Game.create(GameConfig(fractionsQuantity = fractions, levelSize = size, seed = size * 100L + fractions))
            assertTrue(game.field.activeHexes.size > 20, "too few hexes")
            for (f in 0 until fractions) {
                assertTrue(game.isFractionAlive(f), "fraction $f has no province (size=$size, n=$fractions)")
            }
            assertTrue(game.provinces.all { it.hasCapital() }, "province without capital")
            checkInvariants(game)
        }
    }

    @Test
    fun aiOnlyMatchesKeepInvariants() {
        for (difficulty in 0..4) for (seed in 1L..3L) {
            val game = Game.create(
                GameConfig(fractionsQuantity = 5, playersNumber = 0, difficulty = difficulty, levelSize = LevelSize.SMALL, seed = seed)
            )
            var turns = 0
            while (!game.isGameOver && turns < 5 * 150) {
                game.performAiTurn()
                checkInvariants(game)
                game.endTurn()
                turns++
            }
            checkInvariants(game)
        }
    }

    @Test
    fun strongAiUsuallyFinishesSmallMaps() {
        var finished = 0
        for (seed in 1L..6L) {
            val game = Game.create(
                GameConfig(fractionsQuantity = 3, playersNumber = 0, difficulty = Difficulty.EXPERT, levelSize = LevelSize.SMALL, seed = seed)
            )
            var turns = 0
            while (!game.isGameOver && turns < 3 * 300) {
                game.performAiTurn()
                game.endTurn()
                turns++
            }
            if (game.isGameOver) finished++
        }
        assertTrue(finished >= 3, "only $finished of 6 matches finished")
    }

    @Test
    fun playerCanBuildAndUndo() {
        val game = Game.create(GameConfig(fractionsQuantity = 4, levelSize = LevelSize.SMALL, seed = 42))
        val pc = PlayerController(game)
        val province = game.provinces.first { it.fraction == 0 }
        province.money = 100
        pc.tap(province.hexList.first { it.isFree() })
        assertEquals(province, pc.selectedProvince)
        val moneyBefore = province.money
        val target = province.hexList.first { it.isFree() }
        pc.chooseBuild(BuildAction.TOWER)
        pc.tap(target)
        assertEquals(Obj.TOWER, target.objectInside)
        assertEquals(moneyBefore - Rules.PRICE_TOWER, game.getProvinceByHex(target)!!.money)
        pc.undo()
        assertEquals(Obj.NONE, target.objectInside)
        assertEquals(moneyBefore, game.getProvinceByHex(target)!!.money)
        checkInvariants(game)
    }

    @Test
    fun playerUnitCanAttackNeutralLand() {
        for (seed in 1L..20L) {
            val game = Game.create(GameConfig(fractionsQuantity = 4, levelSize = LevelSize.SMALL, seed = seed))
            val pc = PlayerController(game)
            val province = game.provinces.first { it.fraction == 0 }
            province.money = 100
            pc.tap(province.getCapital())
            pc.chooseBuild(BuildAction.UNIT_1)
            val target = pc.moveZone.firstOrNull { it.isNeutral && it.isFree() } ?: continue
            val sizeBefore = province.hexList.size
            pc.tap(target)
            assertEquals(0, target.fraction)
            assertTrue(target.unit != null)
            assertTrue(game.getProvinceByHex(target)!!.hexList.size > sizeBefore)
            checkInvariants(game)
            return
        }
        fail("no seed produced neutral land next to the player")
    }
}
