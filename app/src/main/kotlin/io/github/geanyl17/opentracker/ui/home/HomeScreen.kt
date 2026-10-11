package io.github.geanyl17.opentracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.data.CategoryUsage
import io.github.geanyl17.opentracker.data.MonthSummary
import io.github.geanyl17.opentracker.ui.components.AmountDialog
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.BudgetBar
import io.github.geanyl17.opentracker.ui.components.EmptyHint
import io.github.geanyl17.opentracker.ui.components.EntryRow
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.MonthSwitcher
import io.github.geanyl17.opentracker.ui.components.SectionHeader
import io.github.geanyl17.opentracker.ui.components.monthLabel
import io.github.geanyl17.opentracker.ui.theme.tabular
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    tabBar: @Composable () -> Unit,
    onAdd: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onSeeAll: () -> Unit,
    onVoice: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editingBudget by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenTracker") },
                actions = {
                    IconButton(onClick = onVoice) { AppIcon(R.drawable.ic_mic, "Talk to the assistant") }
                    IconButton(onClick = onSettings) { AppIcon(R.drawable.ic_settings, "Settings") }
                },
            )
        },
        bottomBar = tabBar,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
            ) {
                AppIcon(R.drawable.ic_add, "Add a transaction")
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        val summary = state.summary
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            item { MonthSwitcher(state.month, viewModel::selectMonth) }
            if (summary != null) {
                item { Overview(summary, onSetBudget = { editingBudget = true }) }
                item { Stats(summary) }
                if (summary.categories.isNotEmpty()) {
                    item { SectionHeader("Spending by category") }
                    items(summary.categories, key = { it.categoryId ?: -1L }) { CategoryRow(it, summary.spent) }
                }
                item {
                    SectionHeader("Latest") {
                        if (state.recent.isNotEmpty()) TextButton(onClick = onSeeAll) { Text("See all") }
                    }
                }
                if (state.recent.isEmpty()) {
                    item {
                        EmptyHint("Nothing in ${monthLabel(state.month)} yet. Tap + to add a transaction, or tap the mic and say what you spent.")
                    }
                }
                items(state.recent, key = { it.entry.id }) { item ->
                    EntryRow(item.entry, item.category, onClick = { onOpenEntry(item.entry.id) })
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
private fun Overview(summary: MonthSummary, onSetBudget: () -> Unit) {
    val money = LocalMoney.current
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            if (summary.daysLeft != null) "Spent this month" else "Spent",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Text(money.format(summary.spent), style = MaterialTheme.typography.displaySmall.tabular())
        Spacer(Modifier.height(16.dp))
        val budget = summary.budget
        if (budget == null) {
            TextButton(onClick = onSetBudget, contentPadding = PaddingValues(0.dp)) { Text("Set a monthly budget") }
            return@Column
        }
        val over = summary.spent > budget
        BudgetBar(summary.spent.toFloat() / budget, color = if (over) colors.error else colors.primary)
        Spacer(Modifier.height(10.dp))
        Row {
            Text(
                if (over) {
                    "${money.format(summary.spent - budget)} over your ${money.format(budget)} budget"
                } else {
                    "${money.format(budget - summary.spent)} left of ${money.format(budget)}"
                },
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = if (over) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSetBudget, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(24.dp)) {
                Text("Edit", style = MaterialTheme.typography.labelMedium)
            }
        }
        summary.projected?.let { projected ->
            Text(
                "At this pace, about ${money.format(projected)} by the end of the month",
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = if (projected > budget) colors.error else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stats(summary: MonthSummary) {
    val money = LocalMoney.current
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 16.dp),
    ) {
        Stat("Income", money.format(summary.income), Modifier.weight(1f))
        Stat("Net", (if (summary.net < 0) "−" else "") + money.format(abs(summary.net)), Modifier.weight(1f))
        val perDay = summary.perDayLeft
        if (perDay != null) {
            Stat("Per day left", money.format(perDay), Modifier.weight(1f))
        } else {
            Stat("Daily average", money.format(summary.dailyAverage), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.tabular(), maxLines = 1)
    }
}

@Composable
private fun CategoryRow(usage: CategoryUsage, total: Long) {
    val money = LocalMoney.current
    val colors = MaterialTheme.colorScheme
    val budget = usage.budget
    val over = budget != null && usage.spent > budget
    Column(Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
        Row {
            Text(usage.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (budget != null) "${money.format(usage.spent)} / ${money.format(budget)}" else money.format(usage.spent),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = if (over) colors.error else colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            budget != null -> BudgetBar(usage.spent.toFloat() / budget, color = if (over) colors.error else colors.primary)
            total > 0 -> BudgetBar(usage.spent.toFloat() / total, color = colors.outline)
        }
    }
}
