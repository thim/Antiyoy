import antiyoy.core.*
import korlibs.image.color.*
import korlibs.image.text.*
import korlibs.korge.input.*
import korlibs.korge.view.*
import korlibs.math.geom.*
import korlibs.time.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** Match screen: renders the field with the original sprites and turns input into PlayerController calls. */
class GameScreen(private val app: App, private val game: Game) : Container() {
    private val assets = app.assets
    private val font = assets.font
    private val controller = PlayerController(game)
    private val hvs = game.field.hexViewSize

    /** Drawn tile radius: a hair over the field's hex size so neighbouring tiles leave no seams. */
    private val tileR = 1.02 * game.field.hexSize
    private val spriteBox = 1.55 * game.field.hexSize

    // --- layers
    private val background = image(assets.background)
    private val world = container()
    private val hexLayer = world.container()
    private val overlayLayer = world.container()
    private val borderLayer = world.container()
    private val objectLayer = world.container()
    private val unitLayer = world.container()
    private val inputCatcher = solidRect(10.0, 10.0, RGBA(0, 0, 0, 1))
    private val hud = container()

    private val hexImages = HashMap<Hex, Image>()
    private val unitViews = HashMap<GameUnit, UnitView>()

    private var drawnGameVersion = -1
    private var drawnSelectionVersion = -1
    private var drawnTurn = -1

    // --- camera
    private var zoom = 1.0
    private var camX = 0.0 // screen offset of world origin
    private var camY = 0.0
    private var screenW = 720.0
    private var screenH = 1280.0
    private var ui = 1.0
    private var topBarH = 64.0
    private var bottomBarH = 96.0

    // --- AI pacing
    private var aiTimer = 0.0
    private var waitingHandover = false
    private var timeSec = 0.0

    // --- HUD views
    private lateinit var turnChip: Image
    private lateinit var turnText: Text
    private lateinit var moneyText: Text
    private lateinit var infoText: Text
    private val buildButtons = HashMap<BuildAction, UiButton>()
    private lateinit var undoButton: UiButton
    private lateinit var endTurnButton: UiButton
    private var overlay: Container? = null

    init {
        game.listener = object : GameListener {
            override fun onUnitMoved(unit: GameUnit, from: Hex, to: Hex, attack: Boolean) {
                if (game.isPlayerTurn()) assets.play(if (attack) "attack" else "walk")
            }

            override fun onBuilt(hex: Hex, what: Int) {
                if (game.isPlayerTurn()) assets.play("build")
            }

            override fun onUnitBuilt(hex: Hex, strength: Int, attack: Boolean) {
                if (game.isPlayerTurn()) assets.play(if (attack) "attack" else "build")
            }
        }
        buildStaticField()
        onStageResized { w, h -> layout(w.toDouble(), h.toDouble()) }
        installInput()
        addUpdater { dt -> update(dt.seconds) }
        startTurnIfNeeded()
    }

    // ================================================================ field drawing

    private fun toWorldY(y: Float) = -y.toDouble()

    private fun buildStaticField() {
        // hexes are created once, back to front so each side band is covered by the tile below; only textures change afterwards
        val sorted = game.field.activeHexes.sortedByDescending { it.y }
        for (hex in sorted) {
            hexImages[hex] = hexLayer.image(assets.hexFor(hex.fraction)) {
                size(Size(2 * tileR, 2 * tileR * assets.hexAspect))
                xy(hex.x - tileR, toWorldY(hex.y) - HALF_SQRT3 * tileR)
            }
        }
    }

    private fun refreshField() {
        for ((hex, img) in hexImages) img.bitmap = assets.hexFor(hex.fraction)
        refreshBorders()
        refreshObjects()
        syncUnits()
    }

    private fun refreshBorders() {
        borderLayer.removeChildren()
        val thickness = 0.09 * hvs
        val half = 0.5 * game.field.hexSize
        for (hex in game.field.activeHexes) {
            for (dir in 0 until 3) { // each pair once
                val adj = hex.adjacent(dir)
                if (!adj.active || adj.fraction == hex.fraction) continue
                drawEdge(hex, adj, half, thickness, Colors.BLACK.withAd(0.3))
            }
        }
    }

    private fun drawEdge(a: Hex, b: Hex, half: Double, thickness: Double, color: RGBA) {
        val ax = a.x.toDouble()
        val ay = toWorldY(a.y)
        val bx = b.x.toDouble()
        val by = toWorldY(b.y)
        val ang = atan2(by - ay, bx - ax) + PI / 2
        val cx = (ax + bx) / 2
        val cy = (ay + by) / 2
        borderLayer.solidRect(2 * half, thickness, color) {
            anchor(Anchor.CENTER)
            xy(cx, cy)
            rotation = ang.radians
        }
    }

    private fun objectBitmap(hex: Hex) = when (hex.objectInside) {
        Obj.TOWN -> assets.castleFor(hex.fraction)
        Obj.TOWER -> assets.tower
        Obj.STRONG_TOWER -> assets.strongTowerFor(hex.fraction)
        Obj.FARM -> assets.farms[hex.visualDiversityIndex]
        Obj.PINE -> assets.pine
        Obj.PALM -> assets.palm
        Obj.GRAVE -> assets.grave
        else -> null
    }

    private fun refreshObjects() {
        objectLayer.removeChildren()
        val withObjects = game.field.activeHexes.filter { it.containsObject() }.sortedByDescending { it.y }
        for (hex in withObjects) {
            val bmp = objectBitmap(hex) ?: continue
            objectLayer.image(bmp) {
                size(Size(spriteBox, spriteBox))
                xy(hex.x - spriteBox / 2, toWorldY(hex.y) - SPRITE_FOOT * spriteBox)
            }
        }
    }

    private inner class UnitView(val unit: GameUnit) {
        val image = unitLayer.image(assets.units[unit.strength - 1]) { size(Size(spriteBox, spriteBox)) }
        var seenMoves = unit.moveCounter
        var animStart = -10.0
        var strength = unit.strength

        fun update() {
            if (strength != unit.strength) {
                strength = unit.strength
                image.bitmap = assets.units[strength - 1]
            }
            if (seenMoves != unit.moveCounter) {
                seenMoves = unit.moveCounter
                animStart = timeSec
            }
            val t = ((timeSec - animStart) / MOVE_TIME).coerceIn(0.0, 1.0)
            val e = 1 - (1 - t) * (1 - t)
            val from = unit.lastHex
            val to = unit.currentHex
            val x = from.x + (to.x - from.x) * e
            val y = from.y + (to.y - from.y) * e
            var jump = 0.0
            if (unit.isReadyToMove && game.isPlayerTurn() && game.isCurrentTurn(unit.fraction)) {
                val p = (timeSec % JUMP_PERIOD) / JUMP_PERIOD
                jump = 4 * 0.11 * p * (1 - p) * hvs
            }
            image.xy(x - spriteBox / 2, -y - SPRITE_FOOT * spriteBox - jump)
        }
    }

    private fun syncUnits() {
        val alive = game.units.toHashSet()
        val dead = unitViews.keys.filter { it !in alive }
        for (u in dead) unitViews.remove(u)?.image?.removeFromParent()
        for (u in game.units) if (u !in unitViews) unitViews[u] = UnitView(u)
        // draw order: units further up first
        val ordered = unitViews.values.sortedByDescending { it.unit.currentHex.y }
        for ((i, v) in ordered.withIndex()) unitLayer.addChildAt(v.image, i)
    }

    private fun refreshOverlay() {
        overlayLayer.removeChildren()
        val zone = controller.moveZone
        if (zone.isNotEmpty()) {
            val inZone = zone.toHashSet()
            for (hex in game.field.activeHexes) {
                if (hex in inZone) continue
                hexOverlay(hex, Colors.BLACK, 0.45)
            }
            for (hex in zone) if (!hex.sameFraction(game.turn)) hexOverlay(hex, Colors.WHITE, 0.18)
        }
        for (hex in controller.selectedHexes) hexOverlay(hex, Colors.WHITE, 0.28)
        controller.selectedUnit?.let { hexOverlay(it.currentHex, Colors.YELLOW, 0.35) }
    }

    private fun hexOverlay(hex: Hex, color: RGBA, alpha: Double) {
        overlayLayer.image(assets.whiteHex) {
            size(Size(2 * tileR, 2 * HALF_SQRT3 * tileR))
            xy(hex.x - tileR, toWorldY(hex.y) - HALF_SQRT3 * tileR)
            colorMul = color
            this.alpha = alpha
        }
    }

    // ================================================================ layout / HUD

    private fun layout(w: Double, h: Double) {
        screenW = w
        screenH = h
        ui = (minOf(w, h) / 720.0).coerceIn(0.5, 1.5)
        topBarH = 64 * ui
        bottomBarH = 112 * ui
        background.size(Size(w, h))
        inputCatcher.size(Size(w, h))
        buildHud()
        fitCamera()
        drawnSelectionVersion = -1
        drawnGameVersion = -1
    }

    private fun buildHud() {
        hud.removeChildren()
        buildButtons.clear()
        val w = screenW
        val h = screenH
        hud.solidRect(w, topBarH, RGBA(0, 0, 0, 0x90))
        turnChip = hud.image(assets.hexFor(0)) {
            size(Size(topBarH * 0.7, topBarH * 0.7 * assets.hexAspect))
            xy(topBarH * 0.2, topBarH * 0.15)
        }
        turnText = hud.text("", 24 * ui, Colors.WHITE, font, TextAlignment.MIDDLE_LEFT) { xy(topBarH * 1.05, topBarH / 2) }
        hud.image(assets.coin) {
            size(Size(topBarH * 0.55, topBarH * 0.55))
            xy(w * 0.36, topBarH * 0.22)
        }
        moneyText = hud.text("", 26 * ui, Colors.WHITE, font, TextAlignment.MIDDLE_LEFT) { xy(w * 0.36 + topBarH * 0.65, topBarH / 2) }
        val menuW = 110 * ui
        hud.uiButton(menuW, topBarH * 0.8, font, Str.MENU, textSize = 22 * ui).apply {
            xy(w - menuW - 8 * ui, topBarH * 0.1)
            onTap { app.showMenu() }
        }
        infoText = hud.text("", 20 * ui, RGBA(255, 255, 255, 0xD0), font, TextAlignment.MIDDLE_CENTER) {
            xy(w / 2, topBarH + 20 * ui)
        }

        // zoom buttons
        val zb = 56 * ui
        hud.uiButton(zb, zb, font, "+", textSize = 34 * ui).apply {
            xy(w - zb - 10 * ui, topBarH + 40 * ui)
            onTap { zoomAt(1.25, screenW / 2, screenH / 2) }
        }
        hud.uiButton(zb, zb, font, "−", textSize = 34 * ui).apply {
            xy(w - zb - 10 * ui, topBarH + 40 * ui + zb * 1.15)
            onTap { zoomAt(0.8, screenW / 2, screenH / 2) }
        }

        // bottom bar
        val barY = h - bottomBarH
        hud.solidRect(w, bottomBarH, RGBA(0, 0, 0, 0x90)) { y = barY }
        val actions = listOf(
            BuildAction.UNIT_1, BuildAction.UNIT_2, BuildAction.UNIT_3, BuildAction.UNIT_4,
            BuildAction.TOWER, BuildAction.STRONG_TOWER, BuildAction.FARM,
        )
        val slots = actions.size + 2
        val gap = 6 * ui
        val bw = minOf((w - gap * (slots + 1)) / slots, 110 * ui)
        val bh = bottomBarH - 2 * gap
        val totalW = slots * bw + (slots - 1) * gap
        var x = (w - totalW) / 2
        undoButton = hud.uiButton(bw, bh, font, Str.UNDO, textSize = 15 * ui).apply {
            xy(x, barY + gap)
            onTap { controller.undo() }
        }
        x += bw + gap
        for (a in actions) {
            val icon = when (a) {
                BuildAction.TOWER -> assets.tower
                BuildAction.STRONG_TOWER -> assets.strongTowerFor(game.turn)
                BuildAction.FARM -> assets.farms[0]
                else -> assets.units[a.unitStrength - 1]
            }
            buildButtons[a] = hud.uiButton(bw, bh, font, "", icon, textSize = 17 * ui).apply {
                xy(x, barY + gap)
                onTap {
                    if (controller.buildAction == a) controller.cancelBuild() else controller.chooseBuild(a)
                }
            }
            x += bw + gap
        }
        endTurnButton = hud.uiButton(bw, bh, font, Str.END_TURN, assets.endTurn, textSize = 17 * ui, bg = RGBA(0x3c, 0x7a, 0x4a, 0xF0)).apply {
            xy(x, barY + gap)
            onTap {
                assets.play("end_turn")
                controller.endTurn()
            }
        }
        refreshHud()
        overlay?.let { hud.addChild(it) }
    }

    private fun refreshHud() {
        if (!::endTurnButton.isInitialized) return
        turnChip.bitmap = assets.hexFor(game.turn)
        turnText.text = "${Str.TURN} ${game.turnsMade + 1}"
        val p = controller.selectedProvince
        if (p != null && game.provinces.contains(p)) {
            val profit = p.profit
            moneyText.text = "${p.money}  (${if (profit >= 0) "+" else ""}$profit)"
            infoText.text = ""
        } else {
            moneyText.text = "—"
            infoText.text = if (controller.isActive) Str.TAP_PROVINCE else ""
        }
        val active = controller.isActive
        for ((a, b) in buildButtons) {
            b.enabled = active && p != null && controller.canAfford(a)
            b.highlighted = controller.buildAction == a
            b.label = "$${controller.price(a)}"
        }
        undoButton.enabled = active && controller.canUndo
        endTurnButton.enabled = active
    }

    // ================================================================ camera

    private fun fitCamera() {
        val hexes = game.field.activeHexes
        if (hexes.isEmpty()) return
        val minX = hexes.minOf { it.x } - hvs
        val maxX = hexes.maxOf { it.x } + hvs
        val minY = hexes.minOf { toWorldY(it.y) } - 1.2 * hvs
        val maxY = hexes.maxOf { toWorldY(it.y) } + hvs
        val availW = screenW
        val availH = screenH - topBarH - bottomBarH
        zoom = minOf(availW / (maxX - minX), availH / (maxY - minY)) * 0.95
        camX = (availW - (maxX - minX) * zoom) / 2 - minX * zoom
        camY = topBarH + (availH - (maxY - minY) * zoom) / 2 - minY * zoom
        applyCamera()
    }

    private fun applyCamera() {
        world.scale = zoom
        world.xy(camX, camY)
    }

    private fun zoomAt(factor: Double, sx: Double, sy: Double) {
        val newZoom = (zoom * factor).coerceIn(0.15, 4.0)
        val f = newZoom / zoom
        camX = sx - (sx - camX) * f
        camY = sy - (sy - camY) * f
        zoom = newZoom
        applyCamera()
    }

    private fun screenToField(sx: Double, sy: Double): Pair<Float, Float> {
        val wx = (sx - camX) / zoom
        val wy = (sy - camY) / zoom
        return wx.toFloat() to (-wy).toFloat()
    }

    // ================================================================ input

    private fun installInput() {
        var downX = 0.0
        var downY = 0.0
        var lastX = 0.0
        var lastY = 0.0
        var pressed = false
        var dragging = false
        var pinching = false
        var pinchStartZoom = 1.0

        inputCatcher.mouse {
            onDown {
                val p = it.currentPosStage
                downX = p.x; downY = p.y; lastX = p.x; lastY = p.y
                pressed = true
                dragging = false
            }
            onMoveAnywhere {
                if (!pressed || pinching) return@onMoveAnywhere
                val p = it.currentPosStage
                if (!dragging && sqrt((p.x - downX) * (p.x - downX) + (p.y - downY) * (p.y - downY)) > 12 * ui) dragging = true
                if (dragging) {
                    camX += p.x - lastX
                    camY += p.y - lastY
                    applyCamera()
                }
                lastX = p.x; lastY = p.y
            }
            onUpAnywhere {
                if (pressed && !dragging && !pinching) {
                    val p = it.currentPosStage
                    onTapAt(p.x, p.y)
                }
                pressed = false
                dragging = false
            }
            onScroll {
                val p = it.currentPosStage
                val dy = it.scrollDeltaYPixels
                if (abs(dy) > 0.01f) zoomAt(if (dy > 0) 1 / 1.12 else 1.12, p.x, p.y)
            }
        }
        inputCatcher.touch.scaleRecognizer(
            start = { pinching = true; pinchStartZoom = zoom },
            end = { pinching = false; dragging = true },
        ) { ratio ->
            if (!pinching) return@scaleRecognizer
            zoomAt(pinchStartZoom * ratio / zoom, screenW / 2, screenH / 2)
        }
    }

    private fun onTapAt(sx: Double, sy: Double) {
        if (overlay != null) return
        if (sy < topBarH || sy > screenH - bottomBarH) return
        val (fx, fy) = screenToField(sx, sy)
        val hex = game.field.hexAtPoint(fx, fy)
        val hadUnit = controller.selectedUnit
        controller.tap(hex)
        if (controller.selectedUnit != null && controller.selectedUnit !== hadUnit) assets.play("select_unit")
    }

    // ================================================================ turn flow

    private fun startTurnIfNeeded() {
        if (game.isPlayerTurn() && game.playersNumber > 1 && !game.isGameOver) showHandover()
    }

    private fun update(dt: Double) {
        timeSec += dt
        if (!game.isGameOver && overlay == null) {
            if (!game.isPlayerTurn()) {
                aiTimer += dt
                if (aiTimer >= AI_DELAY) {
                    aiTimer = 0.0
                    game.performAiTurn()
                    game.endTurn()
                    onTurnChanged()
                }
            } else if (game.currentHumanIsEliminated()) {
                if (allHumansEliminated()) showEnd() else {
                    game.endTurn()
                    onTurnChanged()
                }
            }
        }
        if (game.isGameOver && overlay == null) showEnd()

        if (drawnTurn != game.turn) {
            drawnTurn = game.turn
            if (game.isPlayerTurn()) controller.onTurnStarted()
        }
        if (drawnGameVersion != game.version) {
            drawnGameVersion = game.version
            refreshField()
            refreshOverlay()
            refreshHud()
        }
        if (drawnSelectionVersion != controller.version) {
            drawnSelectionVersion = controller.version
            refreshOverlay()
            refreshHud()
        }
        for (v in unitViews.values) v.update()
    }

    private fun allHumansEliminated() = game.playersNumber > 0 && (0 until game.playersNumber).none { game.isFractionAlive(it) }

    private var lastHumanTurn = -1

    private fun onTurnChanged() {
        if (game.isPlayerTurn() && !game.isGameOver && !game.currentHumanIsEliminated()) {
            if (game.playersNumber > 1 && lastHumanTurn != game.turn) showHandover()
            lastHumanTurn = game.turn
        }
    }

    private fun showHandover() {
        val c = Container()
        c.solidRect(screenW, screenH, RGBA(0, 0, 0, 0xA0))
        c.image(assets.hexFor(game.turn)) {
            size(Size(140 * ui, 140 * ui * assets.hexAspect))
            xy(screenW / 2 - 70 * ui, screenH * 0.35)
        }
        c.text("${Str.PLAYER_TURN} ${game.turn + 1}", 40 * ui, Colors.WHITE, font, TextAlignment.MIDDLE_CENTER) {
            xy(screenW / 2, screenH * 0.35 + 190 * ui)
        }
        c.text(Str.TAP_TO_START, 26 * ui, Colors.WHITE, font, TextAlignment.MIDDLE_CENTER) {
            xy(screenW / 2, screenH * 0.35 + 250 * ui)
        }
        c.onClick {
            c.removeFromParent()
            overlay = null
        }
        overlay = c
        hud.addChild(c)
    }

    private fun showEnd() {
        val c = Container()
        c.solidRect(screenW, screenH, RGBA(0, 0, 0, 0xA0))
        val winner = game.winner
        val title = when {
            winner < 0 -> Str.DEFEAT
            game.playersNumber == 1 -> if (winner == 0) Str.VICTORY else Str.DEFEAT
            else -> Str.WINNER
        }
        c.text(title, 64 * ui, Colors.WHITE, font, TextAlignment.MIDDLE_CENTER) { xy(screenW / 2, screenH * 0.33) }
        if (winner >= 0) {
            c.image(assets.hexFor(winner)) {
                size(Size(120 * ui, 120 * ui * assets.hexAspect))
                xy(screenW / 2 - 60 * ui, screenH * 0.33 + 60 * ui)
            }
        }
        val bw = 320 * ui
        val bh = 80 * ui
        c.uiButton(bw, bh, font, Str.NEW_GAME, textSize = 32 * ui, bg = RGBA(0x3c, 0x9a, 0x4a, 0xF0)).apply {
            xy((screenW - bw) / 2, screenH * 0.6)
            onTap { app.startGame(app.settings) }
        }
        c.uiButton(bw, bh, font, Str.MENU, textSize = 32 * ui).apply {
            xy((screenW - bw) / 2, screenH * 0.6 + bh * 1.25)
            onTap { app.showMenu() }
        }
        overlay = c
        hud.addChild(c)
        if (winner >= 0 && winner < game.playersNumber) assets.play("coin")
    }

    companion object {
        const val MOVE_TIME = 0.22
        const val JUMP_PERIOD = 0.5
        const val AI_DELAY = 0.12
        val HALF_SQRT3 = kotlin.math.sqrt(3.0) / 2

        /** Fraction of the sprite box above a hex centre (sprites stand at 80% of their 100x100 viewBox). */
        const val SPRITE_FOOT = 0.8
    }
}
