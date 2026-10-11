package io.github.geanyl17.opentracker.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.ai.AssistantAction
import io.github.geanyl17.opentracker.data.Money
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.dayLabel
import io.github.geanyl17.opentracker.voice.VoiceState
import kotlinx.coroutines.launch
import java.time.LocalDate

private val suggestions = listOf(
    "How am I doing this month?",
    "What did I spend the most on?",
    "Where can I cut back?",
    "Compare with last month",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    tabBar: @Composable () -> Unit,
    listenNow: Boolean,
    onListenHandled: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    val connected = apiKey != null

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.listen()
        } else {
            scope.launch { snackbar.showSnackbar("Voice input needs microphone access.") }
        }
    }
    fun listen() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.listen() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun send(text: String) {
        viewModel.send(text)
        input = ""
    }

    LaunchedEffect(listenNow) {
        if (listenNow) {
            onListenHandled()
            if (connected) listen()
        }
    }
    LaunchedEffect(voice) {
        (voice as? VoiceState.Error)?.let {
            viewModel.clearVoiceError()
            snackbar.showSnackbar(it.message)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.cancelListening() }
    DisposableEffect(Unit) { onDispose { viewModel.cancelListening() } }
    LaunchedEffect(messages.size, busy) {
        val count = messages.size + if (busy) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assistant") },
                actions = {
                    if (messages.isNotEmpty() && !busy) {
                        IconButton(onClick = viewModel::newChat) { AppIcon(R.drawable.ic_edit, "New chat") }
                    }
                    IconButton(onClick = onOpenReport) { AppIcon(R.drawable.ic_article, "Deep analysis") }
                },
            )
        },
        bottomBar = {
            Column(Modifier.imePadding()) {
                if (connected) {
                    Composer(
                        text = input,
                        onTextChange = { input = it },
                        voice = voice,
                        busy = busy,
                        onSend = { send(input) },
                        onMic = ::listen,
                        onStop = viewModel::stopListening,
                    )
                }
                if (!WindowInsets.isImeVisible) tabBar()
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        when {
            !connected -> ConnectPrompt(padding, onOpenSettings)
            messages.isEmpty() -> Intro(
                padding = padding,
                pendingAnalysis = settings.pendingAnalysis != null,
                onSuggestion = ::send,
                onOpenReport = onOpenReport,
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(messages, key = { it.id }) { message ->
                    when (message) {
                        is ChatMessage.User -> UserBubble(message.text)
                        is ChatMessage.Reply -> ReplyBlock(message, onUndo = { viewModel.undo(message, it) })
                        is ChatMessage.Failure -> FailureBlock(message, onRetry = { viewModel.retry(message) })
                    }
                }
                if (busy) item(key = "thinking") { Thinking() }
            }
        }
    }
}

@Composable
private fun Composer(
    text: String,
    onTextChange: (String) -> Unit,
    voice: VoiceState,
    busy: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onStop: () -> Unit,
) {
    val listening = voice as? VoiceState.Listening
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (listening != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelMeter(listening.level)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        listening.partial.ifEmpty { "Listening…" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (listening.partial.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                    )
                }
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!busy) onSend() }),
                    decorationBox = { field ->
                        if (text.isEmpty()) {
                            Text("Ask, or say what you spent", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                        }
                        field()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            listening != null -> FilledIconButton(onClick = onStop, modifier = Modifier.size(48.dp)) {
                AppIcon(R.drawable.ic_stop, "Stop listening")
            }
            text.isNotBlank() -> FilledIconButton(onClick = onSend, enabled = !busy, modifier = Modifier.size(48.dp)) {
                AppIcon(R.drawable.ic_arrow_upward, "Send")
            }
            else -> FilledIconButton(onClick = onMic, enabled = !busy, modifier = Modifier.size(48.dp)) {
                AppIcon(R.drawable.ic_mic, "Speak")
            }
        }
    }
}

@Composable
private fun LevelMeter(level: Float) {
    val animated by animateFloatAsState(level, label = "level")
    Row(Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0.55f, 1f, 0.75f, 0.4f).forEach { weight ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(0.2f + 0.8f * animated * weight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ReplyBlock(reply: ChatMessage.Reply, onUndo: (Int) -> Unit) {
    val money = LocalMoney.current
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 40.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (reply.text.isNotBlank()) SelectionContainer { Text(reply.text, style = MaterialTheme.typography.bodyLarge) }
        reply.actions.forEachIndexed { index, item ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        describe(item.action, money),
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (item.undone) TextDecoration.LineThrough else null,
                        color = if (item.undone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                    if (item.undone) {
                        Text(
                            "Undone",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    } else {
                        TextButton(onClick = { onUndo(index) }) {
                            AppIcon(R.drawable.ic_undo, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Undo")
                        }
                    }
                }
            }
        }
        reply.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun FailureBlock(failure: ChatMessage.Failure, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 40.dp, top = 8.dp, bottom = 8.dp)) {
        Text(failure.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Try again") }
    }
}

@Composable
private fun Thinking() {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Intro(padding: PaddingValues, pendingAnalysis: Boolean, onSuggestion: (String) -> Unit, onOpenReport: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ask about your money", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Talk or type. Say something like “Spent 250 on lunch” and it's recorded, or ask how your month is going.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { suggestion ->
                SuggestionChip(onClick = { onSuggestion(suggestion) }, label = { Text(suggestion) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = onOpenReport,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_article, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Deep analysis with Antigravity", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (pendingAnalysis) {
                            "Analysis in progress…"
                        } else {
                            "Google's Antigravity agent studies all your transactions and writes a full report. Takes a few minutes."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AppIcon(R.drawable.ic_chevron_right, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ConnectPrompt(padding: PaddingValues, onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connect Gemini", style = MaterialTheme.typography.headlineSmall)
        Text(
            "The assistant and the Antigravity analysis run on Google's Gemini API with your own API key. " +
                "You can create one for free in Google AI Studio, then paste it in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpenSettings) { Text("Add API key") }
    }
}

private fun describe(action: AssistantAction, money: Money): String {
    fun line(amount: Long, category: String?, note: String) =
        listOfNotNull(money.format(amount), category ?: "Uncategorized", note.ifBlank { null }).joinToString(" · ")
    return when (action) {
        is AssistantAction.Added -> {
            val day = if (action.entry.date == LocalDate.now()) "" else " (${dayLabel(action.entry.date)})"
            "Added " + line(action.entry.amount, action.category, action.entry.note) + day
        }
        is AssistantAction.Updated -> "Updated: " + line(action.after.amount, action.category, action.after.note)
        is AssistantAction.Deleted -> "Deleted " + line(action.entry.amount, action.category, action.entry.note)
        is AssistantAction.BudgetChanged -> {
            val target = action.category?.name?.let { "$it budget" } ?: "Monthly budget"
            action.budget?.let { "$target set to ${money.format(it)}" } ?: "$target removed"
        }
    }
}
