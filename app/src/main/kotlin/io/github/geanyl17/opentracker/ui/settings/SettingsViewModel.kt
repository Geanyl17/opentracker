package io.github.geanyl17.opentracker.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.geanyl17.opentracker.AppContainer
import io.github.geanyl17.opentracker.ai.Model
import io.github.geanyl17.opentracker.ai.RelayException
import io.github.geanyl17.opentracker.data.Category
import io.github.geanyl17.opentracker.data.Pairing
import io.github.geanyl17.opentracker.data.Settings
import io.github.geanyl17.opentracker.data.TransactionsCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PairState {
    data object Idle : PairState
    data object Working : PairState
    data object Done : PairState
    data class Failed(val message: String) : PairState
}

sealed interface ModelsState {
    data object Loading : ModelsState
    data class Loaded(val models: List<Model>) : ModelsState
    data class Failed(val message: String) : ModelsState
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val ledger = container.ledger
    private val messages = Channel<String>(Channel.BUFFERED)

    val settings: StateFlow<Settings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories: StateFlow<List<Category>> = ledger.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pairing: StateFlow<Pairing?> = container.secrets.pairing
    val events: Flow<String> = messages.receiveAsFlow()

    var pairState by mutableStateOf<PairState>(PairState.Idle)
        private set

    var models by mutableStateOf<ModelsState>(ModelsState.Loading)
        private set

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun pair(address: String, code: String) {
        pairState = PairState.Working
        viewModelScope.launch {
            pairState = try {
                container.secrets.pair(container.relay.pair(address, code))
                PairState.Done
            } catch (e: RelayException) {
                PairState.Failed(e.message ?: "Pairing failed.")
            }
        }
    }

    fun unpair() {
        container.secrets.pair(null)
        pairState = PairState.Idle
    }

    fun resetPairState() {
        pairState = PairState.Idle
    }

    fun loadModels() {
        models = ModelsState.Loading
        viewModelScope.launch {
            models = try {
                ModelsState.Loaded(container.relay.models())
            } catch (e: RelayException) {
                ModelsState.Failed(e.message ?: "Couldn't get the models from your PC.")
            }
        }
    }

    fun saveCategory(category: Category) {
        viewModelScope.launch {
            val name = category.name.trim()
            val taken = categories.value.any { it.id != category.id && it.kind == category.kind && it.name.equals(name, ignoreCase = true) }
            if (taken) {
                messages.send("There's already a category called $name.")
            } else {
                ledger.saveCategory(category.copy(name = name))
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { ledger.deleteCategory(category) }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val csv = TransactionsCsv.export(ledger.allEntries(), ledger.allCategories())
                withContext(Dispatchers.IO) {
                    container.context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(csv.encodeToByteArray()) }
                        ?: error("Couldn't open the file.")
                }
            }
            messages.send(if (result.isSuccess) "Exported your transactions." else "Export failed: ${result.exceptionOrNull()?.message}")
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    container.context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                        ?: error("Couldn't open the file.")
                }
                ledger.import(TransactionsCsv.read(text))
            }
            messages.send(
                result.fold(
                    onSuccess = { count ->
                        when (count) {
                            0 -> "Nothing new to import."
                            1 -> "Imported 1 transaction."
                            else -> "Imported $count transactions."
                        }
                    },
                    onFailure = { "Import failed: ${it.message}" },
                ),
            )
        }
    }
}
