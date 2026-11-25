import korlibs.image.color.*
import korlibs.image.text.*
import korlibs.korge.view.*
import korlibs.math.geom.*

/** New-game menu: map size, number of colors, human players and AI difficulty. */
class MenuScreen(private val app: App) : Container() {
    private var s = app.settings

    init {
        onStageResized { w, h -> build(w.toDouble(), h.toDouble()) }
    }

    private fun build(w: Double, h: Double) {
        removeChildren()
        val assets = app.assets
        val font = assets.font
        val ui = (minOf(w, h) / 720.0).coerceIn(0.55, 1.6)

        image(assets.background) { size(Size(w, h)) }
        solidRect(w, h, RGBA(0, 0, 0, 0x50))

        // decorative hex strip
        val hexSize = minOf(70 * ui, h * 0.065)
        val colorsShown = s.colors
        val stripW = colorsShown * hexSize * 0.9
        for (i in 0 until colorsShown) {
            image(assets.hexFor(i)) {
                size(Size(hexSize, hexSize * assets.hexAspect))
                xy((w - stripW) / 2 + i * hexSize * 0.9, h * 0.05)
            }
        }
        text(Str.TITLE, minOf(96 * ui, h * 0.09), Colors.WHITE, font, TextAlignment.MIDDLE_CENTER) { xy(w / 2, h * 0.05 + hexSize * 2.1) }

        val bw = minOf(w * 0.8, 520 * ui)
        val bh = minOf(78 * ui, h * 0.075)
        val ts = bh / 78 * 32
        var y = h * 0.05 + hexSize * 3.4
        fun option(text: String, action: () -> Unit) {
            uiButton(bw, bh, font, text, textSize = ts).apply {
                xy((w - bw) / 2, y)
                onTap {
                    action()
                    app.settings = s
                    build(w, h)
                }
            }
            y += bh * 1.2
        }
        option("${Str.SIZE}: ${Str.SIZE_NAMES[s.sizeIndex]}") { s = s.copy(sizeIndex = (s.sizeIndex + 1) % 4) }
        option("${Str.COLORS}: ${s.colors}") {
            val c = if (s.colors >= 7) 2 else s.colors + 1
            s = s.copy(colors = c, humans = minOf(s.humans, c))
        }
        option("${Str.HUMANS}: ${if (s.humans == 0) Str.SPECTATE else s.humans.toString()}") {
            s = s.copy(humans = (s.humans + 1) % (s.colors + 1))
        }
        option("${Str.DIFFICULTY}: ${Str.DIFFICULTY_NAMES[s.difficulty]}") { s = s.copy(difficulty = (s.difficulty + 1) % 5) }
        option(if (app.assets.soundEnabled) Str.SOUND_ON else Str.SOUND_OFF) { app.assets.soundEnabled = !app.assets.soundEnabled }

        y += bh * 0.3
        uiButton(bw, bh * 1.25, font, Str.PLAY, textSize = ts * 1.35, bg = RGBA(0x3c, 0x9a, 0x4a, 0xF0)).apply {
            xy((w - bw) / 2, y)
            onTap { app.startGame(s) }
        }
        text(Str.HINT, minOf(20 * ui, h * 0.022), RGBA(255, 255, 255, 0xB0), font, TextAlignment.MIDDLE_CENTER) { xy(w / 2, h - h * 0.025) }
    }
}
