package io.github.geanyl17.opentracker.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.geanyl17.opentracker.AppContainer
import io.github.geanyl17.opentracker.data.Category
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

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val ledger = container.ledger
    private val messages = Channel<String>(Channel.BUFFERED)

    val settings: StateFlow<Settings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val categories: StateFlow<List<Category>> = ledger.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val apiKey: StateFlow<String?> = container.secrets.apiKey
    val events: Flow<String> = messages.receiveAsFlow()

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    fun setApiKey(value: String?) = container.secrets.setApiKey(value)

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
