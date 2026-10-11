package io.github.geanyl17.opentracker.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import io.github.geanyl17.opentracker.ui.theme.AppTheme
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
        TabBar(current = stack.last(), onSelect = ::selectTab, onAdd = { open(Screen.Editor()) })
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
                onOpenEntry = { open(Screen.Editor(it)) },
                onSeeAll = { selectTab(Screen.Transactions) },
                onVoice = {
                    selectTab(Screen.Assistant)
                    listenOnOpen = true
                },
                onSettings = { open(Screen.Settings) },
                onOpenCategories = { open(Screen.Categories) },
            )
            Screen.Transactions -> TransactionsScreen(
                viewModel = viewModel { TransactionsViewModel(container.ledger) },
                tabBar = tabBar,
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

private class Tab(val screen: Screen, val label: String, @param:DrawableRes val icon: Int, @param:DrawableRes val selectedIcon: Int)

private val tabItems = listOf(
    Tab(Screen.Home, "Home", R.drawable.ic_home, R.drawable.ic_home_fill),
    Tab(Screen.Transactions, "Activity", R.drawable.ic_receipt_long, R.drawable.ic_receipt_long_fill),
    Tab(Screen.Assistant, "Assistant", R.drawable.ic_graphic_eq, R.drawable.ic_graphic_eq_fill),
)

/** A floating pill with the tabs, where the selected one shows its name, and a round button to add a transaction. */
@Composable
private fun TabBar(current: Screen, onSelect: (Screen) -> Unit, onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = AppTheme.colors.card,
            shadowElevation = 6.dp,
            border = if (AppTheme.colors.dark) BorderStroke(1.dp, colors.outlineVariant) else null,
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
        ) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabItems.forEach { tab ->
                    val selected = current == tab.screen
                    val weight by animateFloatAsState(if (selected) 1.9f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "tab")
                    Row(
                        Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(if (selected) colors.primary else Color.Transparent)
                            .clickable { onSelect(tab.screen) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(
                            if (selected) tab.selectedIcon else tab.icon,
                            tab.label,
                            Modifier.size(22.dp),
                            tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                        )
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Text(tab.label, style = MaterialTheme.typography.labelLarge, color = colors.onPrimary, maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = colors.primary,
            shadowElevation = 6.dp,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(R.drawable.ic_add, "Add a transaction", Modifier.size(28.dp), tint = colors.onPrimary)
            }
        }
    }
}

