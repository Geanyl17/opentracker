package io.github.geanyl17.opentracker.ui.transactions

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.ui.components.AppCard
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.EmphasizedDecelerate
import io.github.geanyl17.opentracker.ui.components.EmptyHint
import io.github.geanyl17.opentracker.ui.components.EntryRow
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.Pill
import io.github.geanyl17.opentracker.ui.components.ScreenTitle
import io.github.geanyl17.opentracker.ui.components.StatusBarScrim
import io.github.geanyl17.opentracker.ui.components.TabBarSpace
import io.github.geanyl17.opentracker.ui.components.dayLabel
import io.github.geanyl17.opentracker.ui.theme.AppTheme
import io.github.geanyl17.opentracker.ui.theme.tabular

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    onOpenEntry: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val money = LocalMoney.current

    Scaffold(topBar = { StatusBarScrim() }, bottomBar = { TabBarSpace() }, snackbarHost = { SnackbarHost(LocalSnackbar.current) }) { padding ->
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
                        Pill(option.label, selected = viewModel.filter == option, onClick = { viewModel.filter = option })
                    }
                }
            }
            if (state.loaded && state.days.isEmpty()) {
                item(key = "empty") {
                    AppCard(Modifier.animateItem().padding(horizontal = 16.dp)) {
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
                            .animateItem()
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
                    AppCard(
                        Modifier
                            .animateItem()
                            .padding(horizontal = 16.dp)
                            .animateContentSize(tween(300, easing = EmphasizedDecelerate)),
                        padding = PaddingValues(6.dp),
                    ) {
                        day.items.forEach { item ->
                            key(item.entry.id) {
                                EntryRow(item.entry, item.category, onClick = { onOpenEntry(item.entry.id) }, showDate = false)
                            }
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
