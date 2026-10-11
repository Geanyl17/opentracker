package io.github.geanyl17.opentracker.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.data.Amounts
import io.github.geanyl17.opentracker.data.Entry
import io.github.geanyl17.opentracker.data.Kind
import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.dayLabel
import io.github.geanyl17.opentracker.ui.theme.tabular
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    ledger: Ledger,
    entryId: Long?,
    onClose: () -> Unit,
    onDeleted: (Entry) -> Unit,
) {
    val categories by ledger.categories.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }

    var original by remember { mutableStateOf<Entry?>(null) }
    var kind by rememberSaveable { mutableStateOf(Kind.EXPENSE) }
    var amount by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var filled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(entryId) {
        if (entryId == null) {
            focus.requestFocus()
            return@LaunchedEffect
        }
        val entry = ledger.entry(entryId) ?: return@LaunchedEffect
        original = entry
        if (!filled) {
            kind = entry.kind
            amount = Amounts.editable(entry.amount)
            categoryId = entry.categoryId
            note = entry.note
            date = entry.date
            filled = true
        }
    }

    val cents = Amounts.parse(amount)?.takeIf { it > 0 }
    fun save() {
        if (cents == null || saving) return
        saving = true
        scope.launch {
            val entry = Entry(kind = kind, amount = cents, categoryId = categoryId, note = note.trim(), date = date)
            val existing = original
            if (existing == null) ledger.add(entry) else ledger.update(entry.copy(id = existing.id, createdAt = existing.createdAt))
            onClose()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            entryId != null -> "Edit transaction"
                            kind == Kind.INCOME -> "New income"
                            else -> "New expense"
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = onClose) { AppIcon(R.drawable.ic_close, "Close") } },
                actions = {
                    original?.let { entry ->
                        IconButton(
                            onClick = {
                                scope.launch {
                                    ledger.delete(entry)
                                    onDeleted(entry)
                                }
                            },
                        ) {
                            AppIcon(R.drawable.ic_delete, "Delete")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
            ) {
                Button(
                    onClick = ::save,
                    enabled = cents != null && !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Save")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Kind.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = kind == option,
                        onClick = {
                            if (kind != option) {
                                kind = option
                                if (categories.none { it.id == categoryId && it.kind == option }) categoryId = null
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, Kind.entries.size),
                        label = { Text(if (option == Kind.EXPENSE) "Expense" else "Income") },
                    )
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { value -> amount = value.filter { it.isDigit() || it == '.' || it == ',' } },
                textStyle = MaterialTheme.typography.headlineMedium.tabular(),
                prefix = {
                    Text(
                        LocalMoney.current.symbol,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                placeholder = { Text("0", style = MaterialTheme.typography.headlineMedium.tabular()) },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.filter { it.kind == kind }.forEach { category ->
                        FilterChip(
                            selected = category.id == categoryId,
                            onClick = { categoryId = if (category.id == categoryId) null else category.id },
                            label = { Text(category.name) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note") },
                placeholder = { Text("What was it for?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { pickingDate = true }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(R.drawable.ic_calendar_today, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(dayLabel(date), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        pickingDate = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
