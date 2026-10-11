package io.github.geanyl17.opentracker.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.ui.components.AppCard
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.EmptyHint
import io.github.geanyl17.opentracker.ui.components.EntryRow
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.ScreenTitle
import io.github.geanyl17.opentracker.ui.components.StatusBarScrim
import io.github.geanyl17.opentracker.ui.components.dayLabel
import io.github.geanyl17.opentracker.ui.theme.AppTheme
import io.github.geanyl17.opentracker.ui.theme.tabular

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    tabBar: @Composable () -> Unit,
    onOpenEntry: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val money = LocalMoney.current

    Scaffold(topBar = { StatusBarScrim() }, bottomBar = tabBar, snackbarHost = { SnackbarHost(LocalSnackbar.current) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { ScreenTitle("Activity", Modifier.padding(top = 12.dp)) }
            item {
                SearchField(viewModel.query, onChange = { viewModel.query = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filter.entries.forEach { option ->
                        val selected = viewModel.filter == option
                        Surface(
                            onClick = { viewModel.filter = option },
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else AppTheme.colors.card,
                        ) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
            if (state.loaded && state.days.isEmpty()) {
                item {
                    AppCard(Modifier.padding(horizontal = 16.dp)) {
                        EmptyHint(
                            if (state.isEmpty) {
                                "No transactions yet. Tap + to add one, or tell the assistant what you spent."
                            } else {
                                "Nothing matches."
                            },
                        )
                    }
                }
            }
            state.days.forEach { day ->
                item(key = "day-${day.date.toEpochDay()}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dayLabel(day.date),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (day.spent > 0) {
                            Text(
                                "−" + money.format(day.spent),
                                style = MaterialTheme.typography.labelLarge.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item(key = day.date.toEpochDay()) {
                    AppCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(6.dp)) {
                        day.items.forEach { item ->
                            EntryRow(item.entry, item.category, onClick = { onOpenEntry(item.entry.id) }, showDate = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().height(52.dp), shape = CircleShape, color = AppTheme.colors.card) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_search, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Search", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { AppIcon(R.drawable.ic_close, "Clear search") }
            }
        }
    }
}
