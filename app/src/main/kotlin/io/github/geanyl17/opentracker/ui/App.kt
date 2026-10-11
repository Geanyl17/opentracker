package io.github.geanyl17.opentracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.geanyl17.opentracker.AppContainer
import io.github.geanyl17.opentracker.MainActivity
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.data.Money
import io.github.geanyl17.opentracker.ui.assistant.AssistantScreen
import io.github.geanyl17.opentracker.ui.assistant.AssistantViewModel
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.editor.EditorScreen
import io.github.geanyl17.opentracker.ui.home.HomeScreen
import io.github.geanyl17.opentracker.ui.home.HomeViewModel
import io.github.geanyl17.opentracker.ui.report.ReportScreen
import io.github.geanyl17.opentracker.ui.report.ReportViewModel
import io.github.geanyl17.opentracker.ui.settings.CategoriesScreen
import io.github.geanyl17.opentracker.ui.settings.SettingsScreen
import io.github.geanyl17.opentracker.ui.settings.SettingsViewModel
import io.github.geanyl17.opentracker.ui.theme.OpenTrackerTheme
import io.github.geanyl17.opentracker.ui.transactions.TransactionsScreen
import io.github.geanyl17.opentracker.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.launch
import java.io.Serializable

sealed interface Screen : Serializable {
    data object Home : Screen
    data object Transactions : Screen
    data object Assistant : Screen
    data object Settings : Screen
    data object Categories : Screen
    data object Report : Screen
    data class Editor(val entryId: Long? = null) : Screen
}

private val tabs = listOf(Screen.Home, Screen.Transactions, Screen.Assistant)

@Composable
fun App(container: AppContainer, launchAction: String?, onLaunchHandled: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val current = settings ?: return
    val money = remember(current.currency) { Money.of(current.currency) }
    val snackbar = remember { SnackbarHostState() }
    OpenTrackerTheme(current.theme, current.dynamicColor) {
        CompositionLocalProvider(LocalMoney provides money, LocalSnackbar provides snackbar) {
            Navigation(container, launchAction, onLaunchHandled)
        }
    }
}

@Composable
private fun Navigation(container: AppContainer, launchAction: String?, onLaunchHandled: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf<Screen>(Screen.Home)) }
    var listenOnOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    fun open(screen: Screen) {
        stack = stack + screen
    }
    fun back() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }
    fun selectTab(tab: Screen) {
        stack = if (tab == Screen.Home) listOf(Screen.Home) else listOf(Screen.Home, tab)
    }

    LaunchedEffect(launchAction) {
        when (launchAction) {
            MainActivity.ACTION_VOICE -> {
                selectTab(Screen.Assistant)
                listenOnOpen = true
            }
            MainActivity.ACTION_ADD -> stack = listOf(Screen.Home, Screen.Editor())
            else -> return@LaunchedEffect
        }
        onLaunchHandled()
    }
    BackHandler(enabled = stack.size > 1, onBack = ::back)

    val tabBar: @Composable () -> Unit = {
        NavigationBar {
            listOf(
                Triple(Screen.Home, "Home", R.drawable.ic_home),
                Triple(Screen.Transactions, "Transactions", R.drawable.ic_receipt_long),
                Triple(Screen.Assistant, "Assistant", R.drawable.ic_forum),
            ).forEach { (tab, label, icon) ->
                NavigationBarItem(
                    selected = stack.last() == tab,
                    onClick = { selectTab(tab) },
                    icon = { AppIcon(icon, null) },
                    label = { Text(label) },
                )
            }
        }
    }

    AnimatedContent(
        targetState = stack.last(),
        transitionSpec = {
            if (initialState in tabs && targetState in tabs) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            }
        },
        label = "screen",
    ) { screen ->
        when (screen) {
            Screen.Home -> HomeScreen(
                viewModel = viewModel { HomeViewModel(container.ledger, container.settings) },
                tabBar = tabBar,
                onAdd = { open(Screen.Editor()) },
                onOpenEntry = { open(Screen.Editor(it)) },
                onSeeAll = { selectTab(Screen.Transactions) },
                onVoice = {
                    selectTab(Screen.Assistant)
                    listenOnOpen = true
                },
                onSettings = { open(Screen.Settings) },
            )
            Screen.Transactions -> TransactionsScreen(
                viewModel = viewModel { TransactionsViewModel(container.ledger) },
                tabBar = tabBar,
                onAdd = { open(Screen.Editor()) },
                onOpenEntry = { open(Screen.Editor(it)) },
            )
            Screen.Assistant -> AssistantScreen(
                viewModel = viewModel { AssistantViewModel(container) },
                tabBar = tabBar,
                listenNow = listenOnOpen,
                onListenHandled = { listenOnOpen = false },
                onOpenReport = { open(Screen.Report) },
                onOpenSettings = { open(Screen.Settings) },
            )
            Screen.Report -> ReportScreen(
                viewModel = viewModel { ReportViewModel(container) },
                onBack = ::back,
                onOpenSettings = { open(Screen.Settings) },
            )
            Screen.Settings -> SettingsScreen(
                viewModel = viewModel { SettingsViewModel(container) },
                onBack = ::back,
                onOpenCategories = { open(Screen.Categories) },
            )
            Screen.Categories -> CategoriesScreen(
                viewModel = viewModel { SettingsViewModel(container) },
                onBack = ::back,
            )
            is Screen.Editor -> EditorScreen(
                ledger = container.ledger,
                entryId = screen.entryId,
                onClose = ::back,
                onDeleted = { entry ->
                    back()
                    scope.launch {
                        val result = snackbar.showSnackbar("Transaction deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                        if (result == SnackbarResult.ActionPerformed) container.ledger.restore(entry)
                    }
                },
            )
        }
    }
}
