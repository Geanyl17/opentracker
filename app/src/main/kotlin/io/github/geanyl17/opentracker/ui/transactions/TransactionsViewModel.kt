package io.github.geanyl17.opentracker.ui.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.geanyl17.opentracker.data.Amounts
import io.github.geanyl17.opentracker.data.Kind
import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.ui.home.EntryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class Filter(val label: String) { ALL("All"), EXPENSES("Expenses"), INCOME("Income") }

data class DayGroup(val date: LocalDate, val items: List<EntryItem>, val spent: Long)

data class TransactionsState(
    val days: List<DayGroup> = emptyList(),
    val isEmpty: Boolean = false,
    val loaded: Boolean = false,
)

class TransactionsViewModel(ledger: Ledger) : ViewModel() {
    var query by mutableStateOf("")
    var filter by mutableStateOf(Filter.ALL)

    val state: StateFlow<TransactionsState> = combine(
        ledger.entries,
        ledger.categories,
        snapshotFlow { query },
        snapshotFlow { filter },
    ) { entries, categories, query, filter ->
        val names = categories.associate { it.id to it.name }
        val needle = query.trim()
        val items = entries
            .filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.EXPENSES -> it.kind == Kind.EXPENSE
                    Filter.INCOME -> it.kind == Kind.INCOME
                }
            }
            .map { EntryItem(it, it.categoryId?.let(names::get)) }
            .filter { item ->
                needle.isEmpty() ||
                    item.entry.note.contains(needle, ignoreCase = true) ||
                    (item.category ?: "Uncategorized").contains(needle, ignoreCase = true) ||
                    Amounts.plain(item.entry.amount).startsWith(needle)
            }
        TransactionsState(
            days = items.groupBy { it.entry.date }.map { (date, list) ->
                DayGroup(date, list, list.filter { it.entry.kind == Kind.EXPENSE }.sumOf { it.entry.amount })
            },
            isEmpty = entries.isEmpty(),
            loaded = true,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsState())
}
