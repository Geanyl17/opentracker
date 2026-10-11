package io.github.geanyl17.opentracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.data.Amounts
import io.github.geanyl17.opentracker.data.Category
import io.github.geanyl17.opentracker.data.Kind
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.CategoryIcon
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.TopBar

@Composable
fun CategoriesScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val money = LocalMoney.current
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addingKind by rememberSaveable { mutableStateOf<Kind?>(null) }
    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { TopBar("Categories", onBack) },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Kind.entries.forEach { kind ->
                item(key = kind.name) {
                    SettingsGroup(if (kind == Kind.EXPENSE) "Expenses" else "Income") {
                        categories.filter { it.kind == kind }.forEach { category ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editingId = category.id }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CategoryIcon(category.name, size = 40.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(category.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                    if (kind == Kind.EXPENSE) {
                                        Text(
                                            category.budget?.let { "${money.format(it)} a month" } ?: "No budget",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                AppIcon(R.drawable.ic_chevron_right, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { addingKind = kind }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIcon(R.drawable.ic_add, null, Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                if (kind == Kind.EXPENSE) "Add an expense category" else "Add an income category",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    }
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
