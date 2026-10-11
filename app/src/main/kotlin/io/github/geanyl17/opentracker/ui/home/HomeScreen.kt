package io.github.geanyl17.opentracker.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.data.CategoryUsage
import io.github.geanyl17.opentracker.data.MonthSummary
import io.github.geanyl17.opentracker.ui.components.AmountDialog
import io.github.geanyl17.opentracker.ui.components.AppCard
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.CategoryIcon
import io.github.geanyl17.opentracker.ui.components.CategoryLooks
import io.github.geanyl17.opentracker.ui.components.CircleIconButton
import io.github.geanyl17.opentracker.ui.components.DonutChart
import io.github.geanyl17.opentracker.ui.components.EmphasizedAccelerate
import io.github.geanyl17.opentracker.ui.components.EmphasizedDecelerate
import io.github.geanyl17.opentracker.ui.components.EmptyHint
import io.github.geanyl17.opentracker.ui.components.EntryRow
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.ProgressBar
import io.github.geanyl17.opentracker.ui.components.SectionTitle
import io.github.geanyl17.opentracker.ui.components.SegmentedBar
import io.github.geanyl17.opentracker.ui.components.StatusBarScrim
import io.github.geanyl17.opentracker.ui.components.TabBarSpace
import io.github.geanyl17.opentracker.ui.components.countUp
import io.github.geanyl17.opentracker.ui.components.monthLabel
import io.github.geanyl17.opentracker.ui.components.pressScale
import io.github.geanyl17.opentracker.ui.components.rememberEntrance
import io.github.geanyl17.opentracker.ui.components.reveal
import io.github.geanyl17.opentracker.ui.theme.AppTheme
import io.github.geanyl17.opentracker.ui.theme.tabular
import kotlin.math.abs
import kotlin.math.roundToInt
import java.time.LocalTime
import java.time.YearMonth

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenEntry: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onVoice: () -> Unit,
    onSettings: () -> Unit,
    onOpenCategories: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingBudget by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = { StatusBarScrim() }, bottomBar = { TabBarSpace() }, snackbarHost = { SnackbarHost(LocalSnackbar.current) }) { padding ->
        val summary = state.summary
        val entrance = rememberEntrance(ready = summary != null)
        fun LazyItemScope.block(index: Int) = Modifier.animateItem().reveal({ entrance.value }, index).padding(horizontal = 16.dp)
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Header(state.month, viewModel::selectMonth, onVoice, onSettings, Modifier.animateItem().reveal({ entrance.value }, 0))
            }
            if (summary != null) {
                item(key = "summary") { SummaryCard(summary, onBudget = { editingBudget = true }, modifier = block(1)) }
                item(key = "stats") { Stats(summary, block(2)) }
                val spending = summary.categories.filter { it.spent > 0 }
                if (spending.isNotEmpty()) {
                    item(key = "spending") { WhereItWent(spending, summary.spent, summary.month, block(3)) }
                }
                val budgeted = summary.categories.filter { it.budget != null }
                if (budgeted.isNotEmpty()) {
                    item(key = "budgets") { Budgets(budgeted, onOpenCategories, block(4)) }
                }
                item(key = "recent") {
                    AppCard(
                        block(5).animateContentSize(tween(300, easing = EmphasizedDecelerate)),
                        padding = PaddingValues(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    ) {
                        SectionTitle("Recent", Modifier.padding(horizontal = 12.dp)) {
                            if (state.recent.isNotEmpty()) TextButton(onClick = onSeeAll) { Text("See all") }
                        }
                        if (state.recent.isEmpty()) {
                            EmptyHint(
                                "Nothing in ${monthLabel(state.month)} yet. Tap + to add a transaction, or tap the mic and say what you spent.",
                                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                            )
                        }
                        state.recent.forEach { item ->
                            EntryRow(item.entry, item.category, onClick = { onOpenEntry(item.entry.id) })
                        }
                    }
                }
            }
        }
    }

    if (editingBudget) {
        AmountDialog(
            title = "Monthly budget",
            initial = state.summary?.budget.takeIf { state.hasMonthlyBudget },
            description = "How much you want to spend at most each month. Leave it empty to use the sum of your category budgets.",
            onDismiss = { editingBudget = false },
            onConfirm = {
                viewModel.setMonthlyBudget(it)
                editingBudget = false
            },
        )
    }
}

@Composable
private fun Header(month: YearMonth, onMonth: (YearMonth) -> Unit, onVoice: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = month,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(tween(350, easing = EmphasizedDecelerate)) { direction * it / 2 } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(200, easing = EmphasizedAccelerate)) { -direction * it / 2 } + fadeOut(tween(120))) using
                            SizeTransform(clip = false) { _, _ -> tween(300, easing = EmphasizedDecelerate) }
                    },
                    label = "month",
                ) { shown ->
                    Text(monthLabel(shown), style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { onMonth(month.minusMonths(1)) }, modifier = Modifier.size(36.dp)) {
                    AppIcon(R.drawable.ic_chevron_left, "Previous month", Modifier.size(22.dp))
                }
                IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month < YearMonth.now(), modifier = Modifier.size(36.dp)) {
                    AppIcon(R.drawable.ic_chevron_right, "Next month", Modifier.size(22.dp))
                }
            }
        }
        CircleIconButton(R.drawable.ic_mic, "Talk to the assistant", onVoice)
        Spacer(Modifier.width(8.dp))
        CircleIconButton(R.drawable.ic_settings, "Settings", onSettings)
    }
}

@Composable
private fun SummaryCard(summary: MonthSummary, onBudget: () -> Unit, modifier: Modifier = Modifier) {
    val money = LocalMoney.current
    val app = AppTheme.colors
    val budget = summary.budget
    val spent = countUp(summary.spent)
    val left = countUp(summary.left ?: 0)
    val budgetPress = remember { MutableInteractionSource() }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = app.hero) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (summary.daysLeft != null) "Spent this month" else "Spent in ${monthLabel(summary.month)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = app.onHeroMuted,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = onBudget,
                    shape = CircleShape,
                    color = app.heroTrack,
                    interactionSource = budgetPress,
                    modifier = Modifier.pressScale(budgetPress),
                ) {
                    Text(
                        budget?.let { "Budget ${money.format(it)}" } ?: "Set a budget",
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = app.onHero,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(money.format(spent), style = MaterialTheme.typography.displayMedium.tabular(), color = app.onHero, maxLines = 1)
            Spacer(Modifier.height(20.dp))
            val scale = maxOf(budget ?: summary.spent, summary.spent).toFloat().coerceAtLeast(1f)
            SegmentedBar(
                parts = summary.categories.filter { it.spent > 0 }.map { it.spent / scale to CategoryLooks.of(it.categoryName()).color },
                track = app.heroTrack,
                height = 12.dp,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        summary.left == null -> plural(summary.categories.count { it.spent > 0 }, "category", "categories")
                        left >= 0 -> "${money.format(left)} left"
                        else -> "${money.format(-left)} over budget"
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold).tabular(),
                    color = if (summary.left != null && left < 0) app.negative else app.onHero,
                    modifier = Modifier.weight(1f),
                )
                summary.perDayLeft?.let { perDay ->
                    Text(
                        "${money.format(perDay)} a day · ${plural(summary.daysLeft ?: 0, "day", "days")}",
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = app.onHeroMuted,
                    )
                }
            }
            summary.projected?.let { projected ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "On pace for ${money.format(projected)} by the end of the month",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = if (budget != null && projected > budget) app.negative else app.onHeroMuted,
                )
            }
        }
    }
}

@Composable
private fun Stats(summary: MonthSummary, modifier: Modifier = Modifier) {
    val money = LocalMoney.current
    val app = AppTheme.colors
    val income = countUp(summary.income)
    val net = countUp(summary.net)
    val perDay = summary.perDayLeft
    val daily = countUp(perDay ?: summary.dailyAverage)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Stat(R.drawable.ic_south_west, "Income", money.format(income), app.positive, Modifier.weight(1f))
        Stat(
            R.drawable.ic_north_east,
            "Net",
            (if (net < 0) "−" else "+") + money.format(abs(net)),
            if (summary.net < 0) app.negative else app.positive,
            Modifier.weight(1f),
        )
        Stat(
            R.drawable.ic_speed,
            if (perDay != null) "Per day" else "Daily avg",
            money.format(daily),
            MaterialTheme.colorScheme.onSurface,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun Stat(@DrawableRes icon: Int, label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    AppCard(modifier, padding = PaddingValues(14.dp)) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = if (AppTheme.colors.dark) 0.2f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, null, Modifier.size(18.dp), tint = tint)
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall.tabular(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WhereItWent(spending: List<CategoryUsage>, total: Long, month: YearMonth, modifier: Modifier = Modifier) {
    val shown = spending.take(5)
    val rest = spending.drop(5).sumOf { it.spent }
    val parts = shown.map { it.spent.toFloat() to CategoryLooks.of(it.categoryName()).color } +
        listOfNotNull((rest.toFloat() to MaterialTheme.colorScheme.outline).takeIf { rest > 0 })
    AppCard(modifier) {
        SectionTitle("Where it went")
        Row(verticalAlignment = Alignment.CenterVertically) {
            val top = spending.first()
            DonutChart(parts, Modifier.size(128.dp), thickness = 16.dp, key = month) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${percent(top.spent, total)}%", style = MaterialTheme.typography.titleLarge.tabular())
                    Text(
                        top.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(80.dp),
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shown.forEach { usage ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(CategoryLooks.of(usage.categoryName()).color))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            usage.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${percent(usage.spent, total)}%",
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (rest > 0) {
                    Text(
                        "+${spending.size - shown.size} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Budgets(budgeted: List<CategoryUsage>, onManage: () -> Unit, modifier: Modifier = Modifier) {
    val money = LocalMoney.current
    AppCard(modifier) {
        SectionTitle("Budgets") { TextButton(onClick = onManage) { Text("Edit") } }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            budgeted.forEach { usage ->
                val budget = usage.budget ?: return@forEach
                val over = usage.spent > budget
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryIcon(usage.categoryName(), size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                usage.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${money.format(usage.spent)} of ${money.format(budget)}",
                                style = MaterialTheme.typography.bodyMedium.tabular(),
                                color = if (over) AppTheme.colors.negative else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(
                            usage.spent.toFloat() / budget,
                            if (over) AppTheme.colors.negative else CategoryLooks.of(usage.categoryName()).color,
                        )
                    }
                }
            }
        }
    }
}

private fun CategoryUsage.categoryName(): String? = name.takeIf { categoryId != null }

private fun plural(count: Int, one: String, many: String): String = "$count ${if (count == 1) one else many}"

private fun percent(part: Long, total: Long): Int = if (total <= 0) 0 else (part * 100f / total).roundToInt()
