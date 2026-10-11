package io.github.geanyl17.opentracker.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.geanyl17.opentracker.AppContainer
import io.github.geanyl17.opentracker.ai.Analyst
import io.github.geanyl17.opentracker.ai.GeminiException
import io.github.geanyl17.opentracker.data.Report
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Starting : AnalysisState
    data class Running(val since: Long, val retrying: Boolean = false) : AnalysisState
    data class Failed(val message: String) : AnalysisState
}

class ReportViewModel(private val container: AppContainer) : ViewModel() {
    private val analyst = container.analyst
    private val _state = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val state: StateFlow<AnalysisState> = _state.asStateFlow()
    private var polling: Job? = null
    private var pendingId: String? = null

    val reports: StateFlow<List<Report>> = container.ledger.reports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hasKey: StateFlow<Boolean> = container.secrets.apiKey
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.secrets.apiKey.value != null)

    init {
        viewModelScope.launch {
            container.settings.settings
                .map { it.pendingAnalysis to it.pendingAnalysisSince }
                .distinctUntilChanged()
                .collect { (id, since) ->
                    if (id != null && polling?.isActive != true) poll(id, since ?: System.currentTimeMillis())
                }
        }
    }

    fun run() {
        if (_state.value is AnalysisState.Starting || _state.value is AnalysisState.Running) return
        _state.value = AnalysisState.Starting
        viewModelScope.launch {
            try {
                analyst.start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = AnalysisState.Failed(e.message ?: "Couldn't start the analysis.")
            }
        }
    }

    fun cancel() {
        val id = pendingId ?: return
        polling?.cancel()
        pendingId = null
        _state.value = AnalysisState.Idle
        viewModelScope.launch { analyst.cancel(id) }
    }

    fun delete(report: Report) {
        viewModelScope.launch { container.ledger.deleteReport(report) }
    }

    private fun poll(id: String, since: Long) {
        pendingId = id
        _state.value = AnalysisState.Running(since)
        polling = viewModelScope.launch {
            while (true) {
                val status = try {
                    analyst.check(id)
                } catch (e: GeminiException) {
                    if (e.status in 400..499 && e.status != 429) {
                        analyst.cancel(id)
                        Analyst.Status.Failed(e.message ?: "The analysis failed.")
                    } else {
                        _state.value = AnalysisState.Running(since, retrying = true)
                        null
                    }
                }
                when (status) {
                    Analyst.Status.Done -> _state.value = AnalysisState.Idle
                    is Analyst.Status.Failed -> _state.value = AnalysisState.Failed(status.message)
                    Analyst.Status.Running -> _state.value = AnalysisState.Running(since)
                    null -> Unit
                }
                if (status == Analyst.Status.Done || status is Analyst.Status.Failed) break
                delay(POLL_INTERVAL)
            }
            pendingId = null
        }
    }

    private companion object {
        const val POLL_INTERVAL = 5_000L
    }
}
