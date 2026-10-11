package io.github.geanyl17.opentracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.geanyl17.opentracker.data.Entry
import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.data.MonthSummary
import io.github.geanyl17.opentracker.data.SettingsStore
import io.github.geanyl17.opentracker.data.summarize
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class EntryItem(val entry: Entry, val category: String?)

data class HomeState(
    val month: YearMonth,
    val summary: MonthSummary? = null,
    val recent: List<EntryItem> = emptyList(),
    val hasMonthlyBudget: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(ledger: Ledger, private val settings: SettingsStore) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<HomeState> = combine(
        month.flatMapLatest { m -> ledger.entriesIn(m).map { m to it } },
        ledger.categories,
        settings.settings,
    ) { (month, entries), categories, settings ->
        val names = categories.associate { it.id to it.name }
        HomeState(
            month = month,
            summary = summarize(month, entries, categories, settings.monthlyBudget, LocalDate.now()),
            recent = entries.take(RECENT).map { EntryItem(it, it.categoryId?.let(names::get)) },
            hasMonthlyBudget = settings.monthlyBudget != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState(YearMonth.now()))

    fun selectMonth(value: YearMonth) {
        month.value = value
    }

    fun setMonthlyBudget(cents: Long?) {
        viewModelScope.launch { settings.update { it.copy(monthlyBudget = cents) } }
    }

    private companion object {
        const val RECENT = 5
    }
}
