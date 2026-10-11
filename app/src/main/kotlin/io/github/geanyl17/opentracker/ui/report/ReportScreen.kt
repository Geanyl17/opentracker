package io.github.geanyl17.opentracker.ui.report

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.data.Report
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.Markdown
import io.github.geanyl17.opentracker.ui.components.SectionHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: ReportViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val report = reports.firstOrNull { it.id == selectedId } ?: reports.firstOrNull()
    val listState = rememberLazyListState()
    LaunchedEffect(reports.firstOrNull()?.id) { selectedId = null }
    LaunchedEffect(selectedId) { if (selectedId != null) listState.animateScrollToItem(1) }
    val clipboard = LocalClipboard.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deep analysis") },
                navigationIcon = { IconButton(onClick = onBack) { AppIcon(R.drawable.ic_arrow_back, "Back") } },
                actions = {
                    if (report != null) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("OpenTracker report", report.text)))
                                    snackbar.showSnackbar("Report copied")
                                }
                            },
                        ) {
                            AppIcon(R.drawable.ic_content_copy, "Copy report")
                        }
                        IconButton(onClick = { viewModel.delete(report) }) { AppIcon(R.drawable.ic_delete, "Delete report") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                RunPanel(
                    state = state,
                    hasKey = hasKey,
                    hasReport = report != null,
                    onRun = viewModel::run,
                    onCancel = viewModel::cancel,
                    onOpenSettings = onOpenSettings,
                )
            }
            if (report != null) {
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                item {
                    Text(
                        "Report from ${formatDate(report.createdAt)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                item { Markdown(report.text, Modifier.padding(horizontal = 24.dp)) }
            }
            val earlier = reports.filter { it.id != report?.id }
            if (earlier.isNotEmpty()) {
                item { SectionHeader("Other reports") }
                items(earlier, key = Report::id) { other ->
                    ListItem(
                        headlineContent = { Text(formatDate(other.createdAt)) },
                        supportingContent = {
                            Text(
                                other.text.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart('#', ' ').orEmpty(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { viewModel.delete(other) }) { AppIcon(R.drawable.ic_delete, "Delete report") }
                        },
                        modifier = Modifier
                            .clickable { selectedId = other.id }
                            .padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RunPanel(
    state: AnalysisState,
    hasKey: Boolean,
    hasReport: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Google's Antigravity agent reads all your transactions in a private sandbox with no internet access, " +
                "analyzes them with code and writes a report: trends, recurring payments, unusual spending and ways to save.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            !hasKey -> Button(onClick = onOpenSettings) { Text("Add API key") }
            state is AnalysisState.Running -> Running(state, onCancel)
            state is AnalysisState.Starting -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Starting…", style = MaterialTheme.typography.bodyLarge)
            }
            else -> {
                if (state is AnalysisState.Failed) {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onRun) { Text(if (hasReport) "Run a new analysis" else "Run analysis") }
            }
        }
    }
}

@Composable
private fun Running(state: AnalysisState.Running, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val seconds = ((now - state.since) / 1000).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                "Analyzing… %d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
        Text(
            if (state.retrying) {
                "Connection problem. Still trying…"
            } else {
                "This usually takes a few minutes. You can leave this screen; the analysis keeps running on Google's servers."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormat)
