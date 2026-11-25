import korlibs.audio.sound.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.format.*
import korlibs.io.async.*
import korlibs.io.file.std.*
import kotlin.coroutines.*

/** Textures, font and sounds taken from the original Antiyoy assets. */
class Assets private constructor(
    val hexByColor: List<BmpSlice>,
    val whiteHex: BmpSlice,
    val colorRgba: List<RGBA>,
    val shadowHex: BmpSlice,
    val background: BmpSlice,
    val units: List<BmpSlice>,
    val castle: BmpSlice,
    val tower: BmpSlice,
    val strongTower: BmpSlice,
    val farms: List<BmpSlice>,
    val pine: BmpSlice,
    val palm: BmpSlice,
    val grave: BmpSlice,
    val coin: BmpSlice,
    val endTurn: BmpSlice,
    val font: Font,
    private val sounds: Map<String, Sound>,
) {
    /** Hex texture for a fraction. Color index == fraction (neutral 7 is gray), as in the original with no color offset. */
    fun hexFor(fraction: Int): BmpSlice = hexByColor[fraction.coerceIn(0, hexByColor.size - 1)]

    fun colorFor(fraction: Int): RGBA = colorRgba[fraction.coerceIn(0, colorRgba.size - 1)]

    var soundEnabled = true
    var context: CoroutineContext = EmptyCoroutineContext

    fun play(name: String) {
        if (!soundEnabled) return
        try {
            val snd = sounds[name] ?: return
            launchImmediately(context) { snd.play() }
        } catch (_: Throwable) {
        }
    }

    companion object {
        private val HEX_NAMES = listOf(
            "green", "red", "blue", "cyan", "yellow", "color1", "color2", "color3", "color4", "color5", "color6",
        )

        suspend fun load(): Assets {
            val res = resourcesVfs
            suspend fun bmp(name: String): Bitmap = res[name].readBitmap()
            val hexBitmaps = HEX_NAMES.map { bmp("hex_$it.png") }
            val colors = hexBitmaps.map { it.getRgbaRaw(it.width / 2, it.height / 2) }
            val font: Font = try {
                res["font.otf"].readTtfFont()
            } catch (_: Throwable) {
                DefaultTtfFont
            }
            val sounds = HashMap<String, Sound>()
            for (s in listOf("attack", "build", "coin", "end_turn", "select_unit", "walk")) {
                try {
                    sounds[s] = res["sound/$s.ogg"].readSound()
                } catch (_: Throwable) {
                }
            }
            return Assets(
                hexByColor = hexBitmaps.map { it.slice() },
                whiteHex = hexBitmaps[0].toBMP32().clone().apply {
                    for (y in 0 until height) for (x in 0 until width) {
                        val a = getRgbaRaw(x, y).a
                        setRgbaRaw(x, y, RGBA(255, 255, 255, a))
                    }
                }.slice(),
                colorRgba = colors,
                shadowHex = bmp("shadow_hex.png").slice(),
                background = bmp("game_background_water.png").slice(),
                units = (0..3).map { bmp("fe/man$it.png").slice() },
                castle = bmp("fe/castle.png").slice(),
                tower = bmp("fe/tower.png").slice(),
                strongTower = bmp("fe/strong_tower.png").slice(),
                farms = (1..3).map { bmp("fe/farm$it.png").slice() },
                pine = bmp("fe/pine.png").slice(),
                palm = bmp("fe/palm.png").slice(),
                grave = bmp("fe/grave.png").slice(),
                coin = bmp("coin.png").slice(),
                endTurn = bmp("end_turn.png").slice(),
                font = font,
                sounds = sounds,
            )
        }
    }
}
