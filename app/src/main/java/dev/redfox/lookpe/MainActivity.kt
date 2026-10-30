package dev.redfox.lookpe

import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.chrisbanes.haze.rememberHazeState
import dev.redfox.lookpe.backup.Backups
import dev.redfox.lookpe.data.Repository
import dev.redfox.lookpe.data.refreshWidgets
import dev.redfox.lookpe.notify.DailySummary
import dev.redfox.lookpe.notify.Notifications
import dev.redfox.lookpe.service.ListenerHealth
import kotlinx.coroutines.Dispatchers
import dev.redfox.lookpe.reveal.RevealState
import dev.redfox.lookpe.reveal.askForBiometric
import dev.redfox.lookpe.ui.HomeScreen
import dev.redfox.lookpe.ui.HistoryScreen
import dev.redfox.lookpe.ui.SearchScreen
import dev.redfox.lookpe.ui.SettingsScreen
import dev.redfox.lookpe.ui.SkippedScreen
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.listSaver
import java.time.LocalDate
import dev.redfox.lookpe.ui.LookPeAccent
import dev.redfox.lookpe.ui.LookPeNeutrals
import dev.redfox.lookpe.widget.MidnightRefresh
import dev.redfox.obsidian.effects.LocalHazeState
import dev.redfox.obsidian.motion.ObTransitions
import dev.redfox.obsidian.motion.RevealScope
import dev.redfox.obsidian.theme.ObsidianTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        MidnightRefresh.schedule(this)
        Backups.ensureScheduled(this)
        Notifications.createChannels(this)
        ListenerHealth.schedule(this)
        DailySummary.schedule(this)
        // A newer parser may read saved notifications better: recover skipped ones, correct amounts.
        lifecycleScope.launch(Dispatchers.IO) {
            val repo = Repository.get(applicationContext)
            val recovered = repo.reparseSkipped()
            val corrected = repo.repairAmounts()
            if (recovered + corrected > 0) refreshWidgets(applicationContext)
        }
        setContent {
            ObsidianTheme(accent = LookPeAccent, neutrals = LookPeNeutrals) {
                CompositionLocalProvider(LocalHazeState provides rememberHazeState()) {
                    Box(Modifier.fillMaxSize().background(ObsidianTheme.colors.background)) {
                        AppNavigation()
                    }
                }
            }
        }
    }

    /** A small back stack: Home is the root; the others are pushed on top. */
    @Composable
    private fun AppNavigation() {
        var stack by rememberSaveable(stateSaver = ScreenStackSaver) { mutableStateOf(listOf(Screen.Home)) }
        var popped by rememberSaveable { mutableStateOf(false) }
        // The day shown on Home; History can jump to one. Kept here so it survives visiting other screens.
        var dayEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }

        val push = { s: Screen -> popped = false; stack = stack + s }
        val back = { if (stack.size > 1) { popped = true; stack = stack.dropLast(1) } }
        BackHandler(enabled = stack.size > 1, onBack = back)

        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = { if (popped) ObTransitions.pop() else ObTransitions.push() },
            label = "screen",
        ) { screen ->
            // Entrances replay when a screen arrives, not when it's uncovered by going back.
            RevealScope(enabled = !popped || screen != stack.last()) {
                when (screen) {
                    Screen.Home -> HomeScreen(
                        day = LocalDate.ofEpochDay(dayEpoch),
                        onDayChange = { dayEpoch = it.toEpochDay() },
                        onReveal = ::reveal,
                        onOpenSettings = { push(Screen.Settings) },
                        onOpenSearch = { push(Screen.Search) },
                        onOpenHistory = { push(Screen.History) },
                    )
                    Screen.Settings -> SettingsScreen(
                        onBack = back,
                        onTurnOffLock = ::turnOffLock,
                        onOpenSkipped = { push(Screen.Skipped) },
                    )
                    Screen.Search -> SearchScreen(onBack = back)
                    Screen.History -> HistoryScreen(
                        onBack = back,
                        onOpenDay = { dayEpoch = it.toEpochDay(); back() },
                    )
                    Screen.Skipped -> SkippedScreen(onBack = back)
                }
            }
        }
    }

    private fun reveal() {
        askForBiometric(
            onSuccess = { lifecycleScope.launch { RevealState.reveal(applicationContext) } },
            onFail = {},
        )
    }

    /** Only someone who can unlock the phone may stop hiding the amounts. */
    private fun turnOffLock() {
        askForBiometric(
            onSuccess = { lifecycleScope.launch { RevealState.setLockEnabled(applicationContext, false) } },
            onFail = {},
            title = "Stop hiding amounts",
            subtitle = "Amounts will always be visible in the app and widget",
        )
    }
}

private enum class Screen { Home, Settings, Search, History, Skipped }

private val ScreenStackSaver = listSaver<List<Screen>, String>(
    save = { stack -> stack.map { it.name } },
    restore = { names -> names.map(Screen::valueOf) },
)
