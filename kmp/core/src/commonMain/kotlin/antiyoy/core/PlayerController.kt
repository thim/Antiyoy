package antiyoy.core

enum class BuildAction(val unitStrength: Int) {
    UNIT_1(1), UNIT_2(2), UNIT_3(3), UNIT_4(4), TOWER(0), STRONG_TOWER(0), FARM(0), TREE(0);

    val isUnit get() = unitStrength > 0
}

/**
 * Turns taps on hexes into game actions for the human whose turn it is.
 * UI-independent port of gameplay.SelectionManager.
 */
class PlayerController(val game: Game) {
    var selectedProvince: Province? = null
        private set
    val selectedHexes = HashSet<Hex>()
    var selectedUnit: GameUnit? = null
        private set
    var moveZone: List<Hex> = emptyList()
        private set
    var buildAction: BuildAction? = null
        private set

    private val undoStack = ArrayList<Snapshot>()
    val canUndo get() = undoStack.isNotEmpty()

    /** Bumped when the selection changes, so views can redraw highlights. */
    var version = 0
        private set

    private fun changed() {
        version++
    }

    val isActive get() = game.isPlayerTurn() && !game.isGameOver

    fun price(action: BuildAction): Int = when (action) {
        BuildAction.TOWER -> Rules.PRICE_TOWER
        BuildAction.STRONG_TOWER -> Rules.PRICE_STRONG_TOWER
        BuildAction.FARM -> selectedProvince?.currentFarmPrice ?: Rules.PRICE_FARM
        BuildAction.TREE -> Rules.PRICE_TREE
        else -> Rules.PRICE_UNIT * action.unitStrength
    }

    fun canAfford(action: BuildAction): Boolean {
        val p = selectedProvince ?: return false
        return p.money >= price(action)
    }

    // ------------------------------------------------------------ input

    fun tap(hex: Hex?) {
        if (!isActive) return
        if (hex == null || !hex.active) {
            deselectAll()
            return
        }
        val somethingSelected = selectedHexes.isNotEmpty()
        if (buildAction != null) {
            reactionBuildStuff(hex)
            return
        }
        // inside selection
        if (somethingSelected) {
            if (hex !in selectedHexes && !hex.inMoveZone) deselectAll()
            if (moveZone.isNotEmpty() && !hex.inMoveZone) {
                selectedUnit = null
                hideMoveZone()
            }
        }
        // attack enemy
        val unit = selectedUnit
        if (!game.isCurrentTurn(hex.fraction) && hex.inMoveZone && unit != null) {
            applyUnitMove(unit, hex)
        }
        // select province
        if (game.isCurrentTurn(hex.fraction) && hasNeighbourWithFraction(hex, game.turn)) {
            applyProvinceSelection(hex)
        }
        // select unit or move peacefully
        if (somethingSelected) {
            val sel = selectedUnit
            if (sel == null) {
                val u = hex.unit
                if (u != null && u.isReadyToMove && game.isCurrentTurn(u.fraction)) selectUnit(u)
            } else if (hex.inMoveZone && game.isCurrentTurn(hex.fraction) && game.canUnitMoveToFriendlyHex(sel, hex)) {
                applyUnitMove(sel, hex)
            }
        }
        changed()
    }

    fun chooseBuild(action: BuildAction) {
        if (!isActive) return
        val province = selectedProvince ?: return
        hideMoveZone()
        selectedUnit = null
        buildAction = action
        when {
            action.isUnit -> showMoveZone(game.detectMoveZone(province.hexList[0], action.unitStrength))
            action == BuildAction.FARM -> showMoveZone(MoveZoneDetection.detectForFarm(game, province))
        }
        changed()
    }

    fun cancelBuild() {
        buildAction = null
        hideMoveZone()
        changed()
    }

    fun undo() {
        if (!isActive || undoStack.isEmpty()) return
        game.restore(undoStack.removeAt(undoStack.size - 1))
        deselectAll()
    }

    fun endTurn() {
        if (!isActive) return
        deselectAll()
        undoStack.clear()
        game.endTurn()
    }

    /** Called by the view when a new human turn starts. */
    fun onTurnStarted() {
        deselectAll()
        undoStack.clear()
    }

    fun unitsReadyToMove(): List<GameUnit> = game.units.filter { it.isReadyToMove && game.isCurrentTurn(it.fraction) }

    /** Selects the province of [unit] and the unit itself (used by "next unit" button). */
    fun focusUnit(unit: GameUnit) {
        if (!isActive) return
        deselectAll()
        applyProvinceSelection(unit.currentHex)
        if (selectedProvince != null) selectUnit(unit)
        changed()
    }

    fun deselectAll() {
        selectedHexes.clear()
        selectedProvince = null
        selectedUnit = null
        buildAction = null
        hideMoveZone()
        changed()
    }

    // ------------------------------------------------------------ internals

    private fun hasNeighbourWithFraction(hex: Hex, fraction: Int): Boolean {
        for (i in 0 until 6) {
            val n = hex.adjacent(i)
            if (n.active && n.sameFraction(fraction)) return true
        }
        return false
    }

    private fun applyProvinceSelection(hex: Hex) {
        val province = game.getProvinceByHex(hex) ?: return
        selectedProvince = province
        selectedHexes.clear()
        selectedHexes.addAll(province.hexList)
    }

    private fun selectUnit(unit: GameUnit) {
        selectedUnit = unit
        buildAction = null
        showMoveZone(game.detectMoveZone(unit.currentHex, unit.strength, Rules.UNIT_MOVE_LIMIT))
    }

    private fun showMoveZone(zone: List<Hex>) {
        moveZone = zone
    }

    private fun hideMoveZone() {
        for (h in moveZone) h.inMoveZone = false
        moveZone = emptyList()
    }

    private inline fun recordAction(action: () -> Boolean): Boolean {
        val snapshot = game.takeSnapshot()
        val ok = action()
        if (ok) {
            undoStack.add(snapshot)
            game.checkToEndGame()
        }
        return ok
    }

    private fun applyUnitMove(unit: GameUnit, target: Hex) {
        val province = selectedProvince
        recordAction {
            val before = unit.currentHex
            val hadUnit = target.unit
            game.moveUnit(unit, target, province)
            unit.currentHex !== before || (hadUnit != null && target.unit !== hadUnit)
        }
        selectedUnit = null
        hideMoveZone()
    }

    private fun canBuildOnHex(hex: Hex, action: BuildAction): Boolean {
        if (hex !in selectedHexes) return false
        if (action == BuildAction.STRONG_TOWER) return !hex.containsBuilding() || hex.objectInside == Obj.TOWER
        return !hex.containsBuilding()
    }

    private fun reactionBuildStuff(hex: Hex) {
        val action = buildAction ?: return
        val province = selectedProvince
        if (province != null) {
            if (canBuildOnHex(hex, action)) {
                recordAction { buildOnOwnHex(province, hex, action) }
                reselect(hex)
            } else if (action.isUnit && hex.inMoveZone && !game.isCurrentTurn(hex.fraction) &&
                province.canBuildUnit(action.unitStrength)
            ) {
                recordAction { game.buildUnit(province, hex, action.unitStrength) }
                reselect(hex) // when uniting provinces, the selected province object may change
            }
        }
        buildAction = null
        hideMoveZone()
        changed()
    }

    private fun buildOnOwnHex(province: Province, hex: Hex, action: BuildAction): Boolean = when (action) {
        BuildAction.TOWER -> !hex.containsTree() && !hex.containsUnit() && game.buildTower(province, hex)
        BuildAction.STRONG_TOWER -> !hex.containsTree() && !hex.containsUnit() && game.buildStrongTower(province, hex)
        BuildAction.FARM -> !hex.containsTree() && !hex.containsUnit() && game.buildFarm(province, hex)
        BuildAction.TREE -> hex.isFree() && game.buildTree(province, hex)
        else -> game.buildUnit(province, hex, action.unitStrength)
    }

    private fun reselect(anchor: Hex) {
        val p = game.getProvinceByHex(anchor) ?: selectedProvince?.takeIf { game.provinces.contains(it) }
        if (p == null) {
            selectedHexes.clear()
            selectedProvince = null
            return
        }
        applyProvinceSelection(p.hexList[0])
    }
}
