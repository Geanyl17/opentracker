package io.github.geanyl17.opentracker.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.EmptyHint
import io.github.geanyl17.opentracker.ui.components.EntryRow
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.dayLabel
import io.github.geanyl17.opentracker.ui.theme.tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel,
    tabBar: @Composable () -> Unit,
    onAdd: () -> Unit,
    onOpenEntry: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query = viewModel.query
    val money = LocalMoney.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Transactions") }) },
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .consumeWindowInsets(padding),
        ) {
            TextField(
                value = query,
                onValueChange = { viewModel.query = it },
                placeholder = { Text("Search", maxLines = 1) },
                leadingIcon = { AppIcon(R.drawable.ic_search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.query = "" }) { AppIcon(R.drawable.ic_close, "Clear search") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { option ->
                    FilterChip(
                        selected = viewModel.filter == option,
                        onClick = { viewModel.filter = option },
                        label = { Text(option.label) },
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.loaded && state.days.isEmpty()) {
                    item {
                        EmptyHint(
                            if (state.isEmpty) {
                                "No transactions yet. Tap + to add one, or tell the assistant what you spent."
                            } else {
                                "Nothing matches."
                            },
                        )
                    }
                }
                state.days.forEach { day ->
                    item(key = day.date.toEpochDay()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
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
                                    money.format(day.spent),
                                    style = MaterialTheme.typography.labelLarge.tabular(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    items(day.items, key = { it.entry.id }) { item ->
                        EntryRow(item.entry, item.category, onClick = { onOpenEntry(item.entry.id) }, showDate = false)
                    }
                }
            }
        }
    }
}
