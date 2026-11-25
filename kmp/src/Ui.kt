import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.text.*
import korlibs.korge.input.*
import korlibs.korge.view.*
import korlibs.math.geom.*

/** Portuguese UI strings (the original game shipped many languages; we start with pt-BR). */
object Str {
    const val TITLE = "Antiyoy"
    const val PLAY = "Jogar"
    const val SIZE = "Mapa"
    const val COLORS = "Cores"
    const val HUMANS = "Humanos"
    const val DIFFICULTY = "IA"
    val SIZE_NAMES = listOf("Pequeno", "Médio", "Grande", "Enorme")
    val DIFFICULTY_NAMES = listOf("Fácil", "Normal", "Difícil", "Especialista", "Balanceador")
    const val SPECTATE = "só IA"
    const val TAP_PROVINCE = "Toque numa província sua"
    const val TURN = "Turno"
    const val VICTORY = "Vitória!"
    const val DEFEAT = "Derrota"
    const val NEW_GAME = "Novo jogo"
    const val MENU = "Menu"
    const val CONTINUE = "Continuar"
    const val PLAYER_TURN = "Vez do jogador"
    const val TAP_TO_START = "Toque para começar"
    const val WINNER = "Vencedor:"
    const val UNDO = "Desfazer"
    const val END_TURN = "Fim"
    const val SOUND_ON = "Som: sim"
    const val SOUND_OFF = "Som: não"
    const val HINT = "Arraste para mover o mapa, role ou use +/- para zoom"
}

/** Simple rounded button with optional icon and label. */
class UiButton(
    w: Double,
    h: Double,
    private val font: korlibs.image.font.Font,
    label: String = "",
    icon: BmpSlice? = null,
    textSize: Double = 18.0,
    val bg: RGBA = RGBA(0x30, 0x30, 0x38, 0xE0),
) : Container() {
    private val back = roundRect(Size(w, h), RectCorners(h * 0.18), fill = bg)
    private val border = roundRect(Size(w, h), RectCorners(h * 0.18), fill = Colors.TRANSPARENT, stroke = Colors.WHITE, strokeThickness = 3.0).also { it.visible = false }
    private val iconView: Image? = icon?.let {
        val ih = if (label.isEmpty()) h * 0.78 else h * 0.6
        val iw = ih * it.width / it.height
        image(it) {
            size(Size(iw, ih))
            xy((w - iw) / 2, if (label.isEmpty()) (h - ih) / 2 else h * 0.02)
        }
    }
    private val labelView = text(label, textSize, Colors.WHITE, font, TextAlignment.MIDDLE_CENTER) {
        xy(w / 2, if (icon == null) h / 2 else h * 0.83)
    }

    var label: String
        get() = labelView.text
        set(value) {
            labelView.text = value
        }

    var enabled = true
        set(value) {
            field = value
            alpha = if (value) 1.0 else 0.4
        }

    var highlighted = false
        set(value) {
            field = value
            border.visible = value
        }

    fun onTap(block: () -> Unit): UiButton {
        onClick { if (enabled) block() }
        return this
    }
}

fun Container.uiButton(
    w: Double, h: Double, font: korlibs.image.font.Font, label: String = "", icon: BmpSlice? = null,
    textSize: Double = 18.0, bg: RGBA = RGBA(0x30, 0x30, 0x38, 0xE0),
): UiButton = UiButton(w, h, font, label, icon, textSize, bg).addTo(this)
