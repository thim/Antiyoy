import korlibs.audio.sound.*
import korlibs.image.bitmap.*
import korlibs.image.color.*
import korlibs.image.font.*
import korlibs.image.format.*
import korlibs.io.async.*
import korlibs.io.file.std.*
import kotlin.coroutines.*

/** Textures ("Ilhas de brinquedo" art, see art/export.py), plus font and sounds from the original Antiyoy. */
class Assets private constructor(
    val hexByColor: List<BmpSlice>,
    val whiteHex: BmpSlice,
    val colorRgba: List<RGBA>,
    val background: BmpSlice,
    val units: List<BmpSlice>,
    private val castles: List<BmpSlice>,
    val tower: BmpSlice,
    private val strongTowers: List<BmpSlice>,
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

    fun castleFor(fraction: Int): BmpSlice = castles[fraction.coerceIn(0, castles.size - 1)]

    fun strongTowerFor(fraction: Int): BmpSlice = strongTowers[fraction.coerceIn(0, strongTowers.size - 1)]

    /** Height / width of a hex tile image (top face plus side band). */
    val hexAspect: Double get() = hexByColor[0].height.toDouble() / hexByColor[0].width

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
        /** Fraction indices 0..10 (7 is neutral), as exported by art/export.py. */
        private const val COLORS = 11

        suspend fun load(): Assets {
            val res = resourcesVfs
            suspend fun bmp(name: String): Bitmap = res[name].readBitmap()
            val hexBitmaps = (0 until COLORS).map { bmp("tiles/hex_$it.png") }
            val colors = hexBitmaps.map { it.getRgbaRaw(it.width / 2, it.height / 3) }
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
                whiteHex = bmp("tiles/hex_top_white.png").slice(),
                colorRgba = colors,
                background = bmp("game_background_water.png").slice(),
                units = (0..3).map { bmp("fe/man$it.png").slice() },
                castles = (0 until COLORS).map { bmp("fe/castle_$it.png").slice() },
                tower = bmp("fe/tower.png").slice(),
                strongTowers = (0 until COLORS).map { bmp("fe/strong_tower_$it.png").slice() },
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
