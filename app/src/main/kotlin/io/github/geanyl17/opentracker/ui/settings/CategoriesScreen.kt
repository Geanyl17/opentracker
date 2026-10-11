package io.github.geanyl17.opentracker.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.data.Amounts
import io.github.geanyl17.opentracker.data.Category
import io.github.geanyl17.opentracker.data.Kind
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val money = LocalMoney.current
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addingKind by rememberSaveable { mutableStateOf<Kind?>(null) }
    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            Kind.entries.forEach { kind ->
                item(key = kind.name) {
                    SectionHeader(if (kind == Kind.EXPENSE) "Expenses" else "Income") {
                        TextButton(onClick = { addingKind = kind }) { Text("Add") }
                    }
                }
                items(categories.filter { it.kind == kind }, key = { it.id }) { category ->
                    SettingRow(
                        category.name,
                        when {
                            kind == Kind.INCOME -> null
                            category.budget != null -> "${money.format(category.budget)} a month"
                            else -> "No budget"
                        },
                    ) { editingId = category.id }
                }
            }
        }
    }

    val editing = categories.firstOrNull { it.id == editingId }
    val kind = editing?.kind ?: addingKind
    if (kind != null) {
        CategoryDialog(
            category = editing,
            kind = kind,
            onDismiss = {
                editingId = null
                addingKind = null
            },
            onSave = { name, budget ->
                viewModel.saveCategory((editing ?: Category(name = name, kind = kind)).copy(name = name, budget = budget))
                editingId = null
                addingKind = null
            },
            onDelete = editing?.let {
                {
                    viewModel.deleteCategory(it)
                    editingId = null
                }
            },
        )
    }
}

@Composable
private fun CategoryDialog(
    category: Category?,
    kind: Kind,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var budget by remember { mutableStateOf(category?.budget?.let(Amounts::editable).orEmpty()) }
    val budgetValid = budget.isBlank() || Amounts.parse(budget) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "New category" else "Edit category") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == Kind.EXPENSE) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = budget,
                        onValueChange = { budget = it },
                        label = { Text("Monthly budget") },
                        placeholder = { Text("No budget") },
                        prefix = { Text(LocalMoney.current.symbol) },
                        isError = !budgetValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Deleting a category keeps its transactions; they become uncategorized.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), Amounts.parse(budget)?.takeIf { it > 0 }) },
                enabled = name.isNotBlank() && budgetValid,
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
