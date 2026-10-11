package io.github.geanyl17.opentracker.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
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
import io.github.geanyl17.opentracker.ui.components.EmphasizedAccelerate
import io.github.geanyl17.opentracker.ui.components.EmphasizedDecelerate
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.TabBarHeight
import io.github.geanyl17.opentracker.ui.components.pressScale
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Navigation(container: AppContainer, launchAction: String?, onLaunchHandled: () -> Unit) {
    var stack by rememberSaveable { mutableStateOf(listOf<Screen>(Screen.Home)) }
    var forward by remember { mutableStateOf(true) }
    var listenOnOpen by remember { mutableStateOf(false) }
    val saved = rememberSaveableStateHolder()
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current

    fun open(screen: Screen) {
        forward = true
        stack = stack + screen
    }
    fun back() {
        if (stack.size > 1) {
            forward = false
            if (stack.last() !in tabs) saved.removeState(stack.last().toString())
            stack = stack.dropLast(1)
        }
    }
    fun selectTab(tab: Screen) {
        forward = true
        stack = if (tab == Screen.Home) listOf(Screen.Home) else listOf(Screen.Home, tab)
    }

    LaunchedEffect(launchAction) {
        when (launchAction) {
            MainActivity.ACTION_VOICE -> {
                selectTab(Screen.Assistant)
                listenOnOpen = true
            }
            MainActivity.ACTION_ADD -> {
                forward = true
                stack = listOf(Screen.Home, Screen.Editor())
            }
            else -> return@LaunchedEffect
        }
        onLaunchHandled()
    }
    BackHandler(enabled = stack.size > 1, onBack = ::back)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = stack.last(),
            transitionSpec = { screenTransition(initialState, targetState, forward) },
            label = "screen",
        ) { screen ->
            saved.SaveableStateProvider(screen.toString()) {
                when (screen) {
                    Screen.Home -> HomeScreen(
                        viewModel = viewModel { HomeViewModel(container.ledger, container.settings) },
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
                        onOpenEntry = { open(Screen.Editor(it)) },
                    )
                    Screen.Assistant -> AssistantScreen(
                        viewModel = viewModel { AssistantViewModel(container) },
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

        AnimatedVisibility(
            visible = stack.last() in tabs && !WindowInsets.isImeVisible,
            enter = slideInVertically(tween(400, easing = EmphasizedDecelerate)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(250, easing = EmphasizedAccelerate)) { it } + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TabBar(current = stack.last { it in tabs }, onSelect = ::selectTab, onAdd = { open(Screen.Editor()) })
        }
    }
}

/**
 * Screens fade through each other: the old one fades out quickly, then the new one fades in. Tabs grow slightly as
 * they appear, screens opened on top move in from the side and back out the same way, and adding a transaction
 * comes up from below.
 */
private fun AnimatedContentTransitionScope<Screen>.screenTransition(from: Screen, to: Screen, forward: Boolean): ContentTransform {
    val appear = fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing))
    val vanish = fadeOut(tween(90, easing = FastOutLinearInEasing))
    val move = tween<IntOffset>(350, easing = FastOutSlowInEasing)
    val transform = when {
        from in tabs && to in tabs ->
            (appear + scaleIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing), initialScale = 0.96f)) togetherWith vanish
        to is Screen.Editor && forward ->
            (slideInVertically(move) { it / 10 } + appear) togetherWith (slideOutVertically(move) { -it / 30 } + vanish)
        from is Screen.Editor && !forward ->
            (slideInVertically(move) { -it / 30 } + appear) togetherWith (slideOutVertically(move) { it / 10 } + vanish)
        forward -> (slideInHorizontally(move) { it / 10 } + appear) togetherWith (slideOutHorizontally(move) { -it / 10 } + vanish)
        else -> (slideInHorizontally(move) { -it / 10 } + appear) togetherWith (slideOutHorizontally(move) { it / 10 } + vanish)
    }
    return transform.apply { targetContentZIndex = if (forward) 1f else -1f }
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
            .height(TabBarHeight)
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
                    val background by animateColorAsState(if (selected) colors.primary else colors.primary.copy(alpha = 0f), tween(250), label = "tab")
                    val content by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, tween(250), label = "tab")
                    val interaction = remember { MutableInteractionSource() }
                    Row(
                        Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .pressScale(interaction, pressed = 0.92f)
                            .clip(CircleShape)
                            .background(background)
                            .clickable(interactionSource = interaction, indication = ripple()) { onSelect(tab.screen) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Crossfade(selected, animationSpec = tween(200), label = "icon") { filled ->
                            AppIcon(if (filled) tab.selectedIcon else tab.icon, tab.label, Modifier.size(22.dp), tint = content)
                        }
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(tween(200, delayMillis = 80)) +
                                expandHorizontally(tween(300, easing = EmphasizedDecelerate), expandFrom = Alignment.Start),
                            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(250, easing = EmphasizedDecelerate), shrinkTowards = Alignment.Start),
                        ) {
                            Row {
                                Spacer(Modifier.width(8.dp))
                                Text(tab.label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onAdd,
            shape = CircleShape,
            color = colors.primary,
            shadowElevation = 6.dp,
            interactionSource = interaction,
            modifier = Modifier.size(64.dp).pressScale(interaction, pressed = 0.9f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(R.drawable.ic_add, "Add a transaction", Modifier.size(28.dp), tint = colors.onPrimary)
            }
        }
    }
}
