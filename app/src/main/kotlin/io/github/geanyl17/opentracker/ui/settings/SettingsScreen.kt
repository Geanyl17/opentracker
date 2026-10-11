package io.github.geanyl17.opentracker.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.data.Settings
import io.github.geanyl17.opentracker.data.ThemeMode
import io.github.geanyl17.opentracker.ui.components.AmountDialog
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.ChoiceDialog
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.SectionHeader
import io.github.geanyl17.opentracker.ui.components.TextFieldDialog
import java.time.LocalDate
import java.util.Currency
import java.util.Locale

private enum class Editing { BUDGET, CURRENCY, API_KEY, MODEL, AGENT, LANGUAGE, THEME }

private const val SOURCE_URL = "https://github.com/Geanyl17/opentracker"
private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpenCategories: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var editing by rememberSaveable { mutableStateOf<Editing?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::export)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    LaunchedEffect(Unit) { viewModel.events.collect { snackbar.showSnackbar(it) } }

    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        val current = settings ?: return@Scaffold
        val money = LocalMoney.current
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item { SectionHeader("Budget") }
            item {
                SettingRow("Monthly budget", current.monthlyBudget?.let(money::format) ?: "Not set") { editing = Editing.BUDGET }
            }
            item {
                SettingRow("Currency", "${money.currency.currencyCode} · ${money.currency.getDisplayName(locale)}") {
                    editing = Editing.CURRENCY
                }
            }
            item { SettingRow("Categories", "Names and budgets for each category", onClick = onOpenCategories) }

            item { SectionHeader("Gemini and Antigravity") }
            item {
                SettingRow("Gemini API key", apiKey?.let { "Set · ends in ${it.takeLast(4)}" } ?: "Not set") { editing = Editing.API_KEY }
            }
            item { SettingRow("Assistant model", current.model) { editing = Editing.MODEL } }
            item { SettingRow("Antigravity agent", current.agent) { editing = Editing.AGENT } }
            item {
                SwitchRow("Read replies aloud", "Speak the assistant's answers", current.speakReplies) { on ->
                    viewModel.update { it.copy(speakReplies = on) }
                }
            }
            item {
                SettingRow("Speech language", current.speechLanguage.ifBlank { "Phone default" }) { editing = Editing.LANGUAGE }
            }

            item { SectionHeader("Appearance") }
            item { SettingRow("Theme", current.theme.label) { editing = Editing.THEME } }
            if (Build.VERSION.SDK_INT >= 31) {
                item {
                    SwitchRow("Wallpaper colors", "Use colors from your wallpaper", current.dynamicColor) { on ->
                        viewModel.update { it.copy(dynamicColor = on) }
                    }
                }
            }

            item { SectionHeader("Data") }
            item {
                SettingRow("Export to CSV", "Save all transactions as a spreadsheet file") {
                    exporter.launch("opentracker-${LocalDate.now()}.csv")
                }
            }
            item {
                SettingRow("Import from CSV", "Add transactions from a file with date, type, amount, category and note columns") {
                    importer.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel"))
                }
            }

            item { SectionHeader("About") }
            item { SettingRow("OpenTracker $version", "Free and open source, under the GPL-3.0 license") { uriHandler.openUri(SOURCE_URL) } }
        }

        when (editing) {
            Editing.BUDGET -> AmountDialog(
                title = "Monthly budget",
                initial = current.monthlyBudget,
                description = "How much you want to spend at most each month. Leave it empty to use the sum of your category budgets.",
                onDismiss = { editing = null },
                onConfirm = { value ->
                    viewModel.update { it.copy(monthlyBudget = value) }
                    editing = null
                },
            )
            Editing.CURRENCY -> CurrencyDialog(
                selected = current.currency,
                locale = locale,
                onDismiss = { editing = null },
                onSelect = { code ->
                    viewModel.update { it.copy(currency = code) }
                    editing = null
                },
            )
            Editing.API_KEY -> TextFieldDialog(
                title = "Gemini API key",
                initial = apiKey.orEmpty(),
                label = "API key",
                secret = true,
                description = {
                    Text(
                        buildAnnotatedString {
                            append("Create a key in ")
                            withLink(LinkAnnotation.Url(AI_STUDIO_URL, TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary)))) {
                                append("Google AI Studio")
                            }
                            append(" and paste it here. It's stored only on this phone and is left out of backups.")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onDismiss = { editing = null },
                onConfirm = { value ->
                    viewModel.setApiKey(value)
                    editing = null
                },
                extraAction = if (apiKey != null) {
                    {
                        TextButton(
                            onClick = {
                                viewModel.setApiKey(null)
                                editing = null
                            },
                        ) { Text("Remove") }
                    }
                } else {
                    null
                },
            )
            Editing.MODEL -> TextFieldDialog(
                title = "Assistant model",
                initial = current.model,
                label = "Model",
                description = { Text("The Gemini model for the assistant. Default: ${Settings.DEFAULT_MODEL}", style = MaterialTheme.typography.bodyMedium) },
                isValid = { it.isNotBlank() },
                onDismiss = { editing = null },
                onConfirm = { value ->
                    viewModel.update { it.copy(model = value.trim()) }
                    editing = null
                },
            )
            Editing.AGENT -> TextFieldDialog(
                title = "Antigravity agent",
                initial = current.agent,
                label = "Agent",
                description = { Text("The agent for deep analysis. Default: ${Settings.DEFAULT_AGENT}", style = MaterialTheme.typography.bodyMedium) },
                isValid = { it.isNotBlank() },
                onDismiss = { editing = null },
                onConfirm = { value ->
                    viewModel.update { it.copy(agent = value.trim()) }
                    editing = null
                },
            )
            Editing.LANGUAGE -> TextFieldDialog(
                title = "Speech language",
                initial = current.speechLanguage,
                label = "Language tag",
                keyboardType = KeyboardType.Ascii,
                description = {
                    Text(
                        "The language you speak to the assistant in, like en-US, en-GB or es-ES. Leave it empty to use the phone's language.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onDismiss = { editing = null },
                onConfirm = { value ->
                    viewModel.update { it.copy(speechLanguage = value.trim()) }
                    editing = null
                },
            )
            Editing.THEME -> ChoiceDialog(
                title = "Theme",
                options = ThemeMode.entries.map { it to it.label },
                selected = current.theme,
                onDismiss = { editing = null },
                onSelect = { mode ->
                    viewModel.update { it.copy(theme = mode) }
                    editing = null
                },
            )
            null -> Unit
        }
    }
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Same as the phone"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

@Composable
fun SettingRow(title: String, value: String? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .heightIn(min = 64.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CurrencyDialog(selected: String, locale: Locale, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember(locale) {
        Currency.getAvailableCurrencies().sortedBy { it.currencyCode }.map { it.currencyCode to it.getDisplayName(locale) }
    }
    val shown = all.filter { (code, name) -> query.isBlank() || code.contains(query, true) || name.contains(query, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.first }) { (code, name) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(code) }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                code,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (code == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(56.dp),
                            )
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
