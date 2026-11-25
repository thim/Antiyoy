package antiyoy.core

/** Port of gameplay.MoveZoneDetection: flood fill limited by move points and attack ability. */
internal object MoveZoneDetection {

    fun detect(game: Game, startHex: Hex, strength: Int, moveLimit: Int): ArrayList<Hex> {
        game.unFlag(game.field.activeHexes)
        val result = ArrayList<Hex>()
        val queue = ArrayDeque<Hex>()
        queue.add(startHex)
        startHex.moveZoneNumber = moveLimit
        while (queue.isNotEmpty()) {
            val temp = queue.removeFirst()
            temp.inMoveZone = true
            result.add(temp)
            if (!temp.sameFraction(startHex)) continue
            if (temp.moveZoneNumber == 0) continue
            for (dir in 0 until 6) {
                val adj = temp.adjacent(dir)
                if (!adj.active || adj.flag) continue
                if (adj.sameFraction(startHex)) {
                    queue.add(adj)
                    adj.moveZoneNumber = temp.moveZoneNumber - 1
                    adj.flag = true
                } else if (game.canUnitAttackHex(strength, adj)) {
                    queue.add(adj)
                    adj.flag = true
                }
            }
        }
        return result
    }

    fun detectForFarm(game: Game, province: Province): ArrayList<Hex> {
        game.unFlag(game.field.activeHexes)
        val result = ArrayList<Hex>()
        for (hex in province.hexList) {
            if (game.canBuildFarmOnHex(hex)) {
                hex.inMoveZone = true
                result.add(hex)
            }
        }
        return result
    }
}
