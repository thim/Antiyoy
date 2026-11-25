import antiyoy.core.*
import korlibs.image.color.*
import korlibs.korge.*
import korlibs.korge.view.*
import korlibs.math.geom.*
import korlibs.time.*
import kotlin.coroutines.coroutineContext

suspend fun main() = Korge(
    windowSize = Size(720, 1280),
    title = "Antiyoy",
    backgroundColor = Colors["#1e4a7a"],
    displayMode = KorgeDisplayMode.NO_SCALE,
) {
    val assets = Assets.load()
    assets.context = coroutineContext
    App(this, assets).showMenu()
}

/** Switches between the menu and a running match. */
class App(val stage: Stage, val assets: Assets) {
    var settings = MenuSettings()
    private var current: View? = null

    fun showMenu() {
        current?.removeFromParent()
        current = MenuScreen(this).addTo(stage)
    }

    fun startGame(s: MenuSettings) {
        settings = s
        val config = GameConfig(
            fractionsQuantity = s.colors,
            playersNumber = s.humans,
            difficulty = s.difficulty,
            levelSize = LevelSize.ALL[s.sizeIndex],
            seed = DateTime.nowUnixMillisLong(),
        )
        current?.removeFromParent()
        current = GameScreen(this, Game.create(config)).addTo(stage)
    }
}

data class MenuSettings(
    val sizeIndex: Int = 1,
    val colors: Int = 5,
    val humans: Int = 1,
    val difficulty: Int = Difficulty.NORMAL,
)
