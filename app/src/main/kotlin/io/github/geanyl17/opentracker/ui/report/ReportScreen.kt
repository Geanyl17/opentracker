package io.github.geanyl17.opentracker.ui.report

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.ui.components.AppCard
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.CircleIconButton
import io.github.geanyl17.opentracker.ui.components.EmphasizedDecelerate
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.Markdown
import io.github.geanyl17.opentracker.ui.components.SectionTitle
import io.github.geanyl17.opentracker.ui.components.TopBar
import io.github.geanyl17.opentracker.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ReportScreen(viewModel: ReportViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val paired by viewModel.paired.collectAsStateWithLifecycle()
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
            TopBar(
                "Deep analysis",
                onBack,
                actions = {
                    if (report != null) {
                        CircleIconButton(
                            R.drawable.ic_content_copy,
                            "Copy report",
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("OpenTracker report", report.text)))
                                    snackbar.showSnackbar("Report copied")
                                }
                            },
                        )
                        CircleIconButton(R.drawable.ic_delete, "Delete report", onClick = { viewModel.delete(report) })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "run") {
                RunPanel(
                    state = state,
                    paired = paired,
                    hasReport = report != null,
                    onRun = viewModel::run,
                    onCancel = viewModel::cancel,
                    onOpenSettings = onOpenSettings,
                )
            }
            if (report != null) {
                item(key = report.id) {
                    AppCard(Modifier.animateItem(), padding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {
                        Text(
                            "Report from ${formatDate(report.createdAt)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Markdown(report.text)
                    }
                }
            }
            val earlier = reports.filter { it.id != report?.id }
            if (earlier.isNotEmpty()) {
                item(key = "earlier") {
                    AppCard(
                        Modifier
                            .animateItem()
                            .animateContentSize(tween(300, easing = EmphasizedDecelerate)),
                        padding = PaddingValues(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    ) {
                        SectionTitle("Other reports", Modifier.padding(horizontal = 12.dp))
                        earlier.forEach { other ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectedId = other.id }
                                    .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(formatDate(other.createdAt), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        other.text.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart('#', ' ').orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { viewModel.delete(other) }) { AppIcon(R.drawable.ic_delete, "Delete report") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunPanel(
    state: AnalysisState,
    paired: Boolean,
    hasReport: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val app = AppTheme.colors
    Surface(shape = RoundedCornerShape(28.dp), color = app.hero, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(app.heroTrack), contentAlignment = Alignment.Center) {
                    AppIcon(R.drawable.ic_query_stats, null, Modifier.size(22.dp), tint = app.accent)
                }
                Spacer(Modifier.width(14.dp))
                Text("A year of your spending, studied", style = MaterialTheme.typography.titleMedium, color = app.onHero)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Antigravity on your PC writes a report: trends, recurring payments, unusual spending and ways to save. " +
                    "It uses your own Antigravity sign-in.",
                style = MaterialTheme.typography.bodyMedium,
                color = app.onHeroMuted,
            )
            Spacer(Modifier.height(14.dp))
            val buttonColors = ButtonDefaults.buttonColors(containerColor = app.onHero, contentColor = app.hero)
            AnimatedContent(
                targetState = if (paired) state else null,
                contentKey = { it?.let { shown -> shown::class } },
                transitionSpec = {
                    fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) using
                        SizeTransform(clip = false) { _, _ -> tween(300, easing = EmphasizedDecelerate) }
                },
                label = "run",
            ) { shown ->
                when (shown) {
                    null -> Button(onClick = onOpenSettings, colors = buttonColors, modifier = Modifier.height(52.dp)) { Text("Pair with your PC") }
                    is AnalysisState.Running -> Running(shown, onCancel)
                    is AnalysisState.Starting -> Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = app.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Starting…", style = MaterialTheme.typography.bodyLarge, color = app.onHero)
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (shown is AnalysisState.Failed) {
                            Text(shown.message, style = MaterialTheme.typography.bodyMedium, color = app.negative)
                        }
                        Button(onClick = onRun, colors = buttonColors, modifier = Modifier.height(52.dp)) {
                            Text(if (hasReport) "Run a new analysis" else "Run analysis")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Running(state: AnalysisState.Running, onCancel: () -> Unit) {
    val app = AppTheme.colors
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
            CircularProgressIndicator(Modifier.size(18.dp), color = app.accent, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                "Analyzing… %d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.bodyLarge,
                color = app.onHero,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onCancel, colors = ButtonDefaults.outlinedButtonColors(contentColor = app.onHero)) { Text("Cancel") }
        }
        Text(
            if (state.retrying) {
                "Connection problem. Still trying…"
            } else {
                "This usually takes a minute or two. You can leave this screen; the analysis keeps running on your PC."
            },
            style = MaterialTheme.typography.bodySmall,
            color = app.onHeroMuted,
        )
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

private fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormat)
