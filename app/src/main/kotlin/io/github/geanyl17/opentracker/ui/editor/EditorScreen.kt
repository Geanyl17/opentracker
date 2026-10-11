package io.github.geanyl17.opentracker.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.data.Amounts
import io.github.geanyl17.opentracker.data.Entry
import io.github.geanyl17.opentracker.data.Kind
import io.github.geanyl17.opentracker.data.Ledger
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.CategoryIcon
import io.github.geanyl17.opentracker.ui.components.CategoryLooks
import io.github.geanyl17.opentracker.ui.components.CircleIconButton
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.TopBar
import io.github.geanyl17.opentracker.ui.components.dayLabel
import io.github.geanyl17.opentracker.ui.theme.AppTheme
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
            TopBar(
                title = when {
                    entryId != null -> "Edit transaction"
                    kind == Kind.INCOME -> "New income"
                    else -> "New expense"
                },
                onBack = onClose,
                backIcon = R.drawable.ic_close,
                backDescription = "Close",
                actions = {
                    original?.let { entry ->
                        CircleIconButton(
                            R.drawable.ic_delete,
                            "Delete",
                            onClick = {
                                scope.launch {
                                    ledger.delete(entry)
                                    onDeleted(entry)
                                }
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = ::save,
                    enabled = cents != null && !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text("Save", style = MaterialTheme.typography.titleSmall)
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            KindToggle(kind) { option ->
                if (kind != option) {
                    kind = option
                    if (categories.none { it.id == categoryId && it.kind == option }) categoryId = null
                }
            }

            AmountField(amount, onChange = { value -> amount = value.filter { it.isDigit() || it == '.' || it == ',' } }, focus)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Category", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.filter { it.kind == kind }.forEach { category ->
                        val selected = category.id == categoryId
                        val color = CategoryLooks.of(category.name).color
                        Surface(
                            onClick = { categoryId = if (selected) null else category.id },
                            shape = CircleShape,
                            color = if (selected) color.copy(alpha = if (AppTheme.colors.dark) 0.28f else 0.16f) else AppTheme.colors.card,
                            border = if (selected) BorderStroke(1.5.dp, color) else null,
                        ) {
                            Row(Modifier.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                CategoryIcon(category.name, size = 30.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(category.name, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(20.dp), color = AppTheme.colors.card) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                    if (note.isEmpty()) {
                        Text("What was it for?", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = note,
                        onValueChange = { note = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = LocalDate.now()
                DateChip("Today", selected = date == today) { date = today }
                DateChip("Yesterday", selected = date == today.minusDays(1)) { date = today.minusDays(1) }
                val custom = date != today && date != today.minusDays(1)
                DateChip(if (custom) dayLabel(date) else "Pick a date", selected = custom, icon = true) { pickingDate = true }
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

@Composable
private fun KindToggle(kind: Kind, onChange: (Kind) -> Unit) {
    Surface(shape = CircleShape, color = AppTheme.colors.card, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp)) {
            Kind.entries.forEach { option ->
                val selected = kind == option
                Surface(
                    onClick = { onChange(option) },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else AppTheme.colors.card,
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (option == Kind.EXPENSE) "Expense" else "Income",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountField(amount: String, onChange: (String) -> Unit, focus: FocusRequester) {
    val style = MaterialTheme.typography.displayLarge.tabular().copy(textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(LocalMoney.current.symbol, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Box(contentAlignment = Alignment.Center) {
                if (amount.isEmpty()) Text("0", style = style.copy(color = MaterialTheme.colorScheme.outline))
                BasicTextField(
                    value = amount,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = style,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 4.dp).focusRequester(focus),
                )
            }
        }
        Text(
            "Amount",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DateChip(label: String, selected: Boolean, icon: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else AppTheme.colors.card,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon) {
                AppIcon(
                    R.drawable.ic_calendar_today,
                    null,
                    Modifier.size(18.dp),
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
