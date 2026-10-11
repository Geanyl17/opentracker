package io.github.geanyl17.opentracker.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.ai.AssistantAction
import io.github.geanyl17.opentracker.data.Money
import io.github.geanyl17.opentracker.ui.components.AppCard
import io.github.geanyl17.opentracker.ui.components.AppIcon
import io.github.geanyl17.opentracker.ui.components.CategoryIcon
import io.github.geanyl17.opentracker.ui.components.CircleIconButton
import io.github.geanyl17.opentracker.ui.components.EmphasizedDecelerate
import io.github.geanyl17.opentracker.ui.components.LocalMoney
import io.github.geanyl17.opentracker.ui.components.LocalSnackbar
import io.github.geanyl17.opentracker.ui.components.ScreenTitle
import io.github.geanyl17.opentracker.ui.components.TabBarInsets
import io.github.geanyl17.opentracker.ui.components.dayLabel
import io.github.geanyl17.opentracker.ui.components.pressScale
import io.github.geanyl17.opentracker.ui.components.rememberEntrance
import io.github.geanyl17.opentracker.ui.components.reveal
import io.github.geanyl17.opentracker.ui.theme.AppTheme
import io.github.geanyl17.opentracker.voice.VoiceState
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Mode { Connect, Intro, Chat }

private enum class ComposerButton { Mic, Send, Stop }

private class Suggestion(val text: String, @param:DrawableRes val icon: Int)

private val suggestions = listOf(
    Suggestion("How am I doing this month?", R.drawable.ic_speed),
    Suggestion("What did I spend the most on?", R.drawable.ic_query_stats),
    Suggestion("Where can I cut back?", R.drawable.ic_savings_fill),
    Suggestion("Compare with last month", R.drawable.ic_calendar_today),
)

@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    listenNow: Boolean,
    onListenHandled: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val voice by viewModel.voice.collectAsStateWithLifecycle()
    val pairing by viewModel.pairing.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    val connected = pairing != null
    val chatting = messages.isNotEmpty() || busy || voice is VoiceState.Listening

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
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            ScreenTitle(
                "Assistant",
                Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(top = 12.dp),
                subtitle = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (connected) AppTheme.colors.accent else MaterialTheme.colorScheme.outline),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (connected) "Antigravity on your PC" else "Not paired with your PC",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (messages.isNotEmpty() && !busy) CircleIconButton(R.drawable.ic_edit_square, "New chat", viewModel::newChat)
                    CircleIconButton(R.drawable.ic_query_stats, "Deep analysis", onOpenReport)
                },
            )
        },
        bottomBar = {
            // Rests on the tab bar, or on the keyboard while it's open.
            Column(Modifier.windowInsetsPadding(WindowInsets.ime.union(TabBarInsets))) {
                AnimatedVisibility(
                    visible = connected && chatting,
                    enter = fadeIn(tween(200, delayMillis = 100)) + expandVertically(tween(350, easing = EmphasizedDecelerate)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(250, easing = EmphasizedDecelerate)),
                ) {
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
            }
        },
        snackbarHost = { SnackbarHost(LocalSnackbar.current) },
    ) { padding ->
        AnimatedContent(
            targetState = when {
                !connected -> Mode.Connect
                !chatting -> Mode.Intro
                else -> Mode.Chat
            },
            transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90)) },
            label = "assistant",
        ) { mode ->
            when (mode) {
                Mode.Connect -> ConnectPrompt(padding, onOpenSettings)
                Mode.Intro -> Intro(
                    padding = padding,
                    pendingAnalysis = settings.pendingAnalysis != null,
                    input = input,
                    onInputChange = { input = it },
                    onSend = { send(input) },
                    onMic = ::listen,
                    onSuggestion = ::send,
                    onOpenReport = onOpenReport,
                )
                // Laid out from the bottom up, so the newest message stays in view when the keyboard or tab bar moves.
                Mode.Chat -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (busy) item(key = "thinking") { Thinking(Modifier.animateItem(fadeOutSpec = null)) }
                    items(messages.asReversed(), key = { it.id }) { message ->
                        Box(Modifier.animateItem()) {
                            when (message) {
                                is ChatMessage.User -> UserBubble(message.text)
                                is ChatMessage.Reply -> ReplyBlock(message, onUndo = { viewModel.undo(message, it) })
                                is ChatMessage.Failure -> FailureBlock(message, onRetry = { viewModel.retry(message) })
                            }
                        }
                    }
                }
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
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AppTheme.colors.card,
            shadowElevation = 2.dp,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
        ) {
            AnimatedContent(
                targetState = listening,
                contentKey = { it != null },
                transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(100)) using SizeTransform(clip = false) },
                contentAlignment = Alignment.CenterStart,
                label = "field",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            ) { shown ->
                if (shown != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Waveform(shown.level)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            shown.partial.ifEmpty { "Listening…" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (shown.partial.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                        )
                    }
                } else {
                    MessageField(text, onTextChange, busy, onSend, "Ask, or say what you spent", singleLine = false)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        AnimatedContent(
            targetState = when {
                listening != null -> ComposerButton.Stop
                text.isNotBlank() -> ComposerButton.Send
                else -> ComposerButton.Mic
            },
            transitionSpec = {
                (fadeIn(tween(150)) + scaleIn(tween(250, easing = EmphasizedDecelerate), initialScale = 0.6f)) togetherWith
                    (fadeOut(tween(100)) + scaleOut(tween(150), targetScale = 0.6f))
            },
            label = "button",
        ) { button ->
            when (button) {
                ComposerButton.Stop -> RoundButton(R.drawable.ic_stop, "Stop listening", onStop, AppTheme.colors.accent, AppTheme.colors.onAccent)
                ComposerButton.Send -> RoundButton(R.drawable.ic_arrow_upward, "Send", onSend, colors.primary, colors.onPrimary, enabled = !busy)
                ComposerButton.Mic -> RoundButton(R.drawable.ic_mic_fill, "Speak", onMic, AppTheme.colors.accent, AppTheme.colors.onAccent, enabled = !busy)
            }
        }
    }
}

@Composable
private fun MessageField(
    text: String,
    onTextChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    placeholder: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.onSurface),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 5,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (!busy && text.isNotBlank()) onSend() }),
        decorationBox = { field ->
            if (text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            field()
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun RoundButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 56.dp,
) {
    val color by animateColorAsState(if (enabled) container else container.copy(alpha = 0.4f), tween(200), label = "button")
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = color,
        shadowElevation = 2.dp,
        interactionSource = interaction,
        modifier = modifier.size(size).pressScale(interaction, pressed = 0.9f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppIcon(icon, description, Modifier.size(size * 0.42f), tint = content)
        }
    }
}

/** Bars that move with the loudness of the voice. */
@Composable
private fun Waveform(level: Float) {
    val animated by animateFloatAsState(level, label = "level")
    val wobble by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "wobble",
    )
    Row(Modifier.height(22.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(0.45f, 0.8f, 1f, 0.7f, 0.5f).forEachIndexed { index, weight ->
            val motion = if (index % 2 == 0) wobble else 2f - wobble
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight((0.2f + 0.8f * animated * weight * motion).coerceIn(0.15f, 1f))
                    .clip(CircleShape)
                    .background(AppTheme.colors.accent),
            )
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(Modifier.fillMaxWidth().padding(start = 64.dp, end = 16.dp, top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AssistantMark() {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(AppTheme.colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(R.drawable.ic_graphic_eq, null, Modifier.size(18.dp), tint = AppTheme.colors.onAccent)
    }
}

@Composable
private fun ReplyBlock(reply: ChatMessage.Reply, onUndo: (Int) -> Unit) {
    val money = LocalMoney.current
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 10.dp, bottom = 6.dp)) {
        AssistantMark()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (reply.text.isNotBlank()) {
                SelectionContainer { Text(reply.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp)) }
            }
            reply.actions.forEachIndexed { index, item ->
                Surface(shape = RoundedCornerShape(18.dp), color = AppTheme.colors.card) {
                    Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryIcon(categoryOf(item.action), size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            describe(item.action, money),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            textDecoration = if (item.undone) TextDecoration.LineThrough else null,
                            color = if (item.undone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        AnimatedContent(
                            targetState = item.undone,
                            transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(100)) using SizeTransform(clip = false) },
                            contentAlignment = Alignment.CenterEnd,
                            label = "undo",
                        ) { undone ->
                            if (undone) {
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
            }
            reply.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun FailureBlock(failure: ChatMessage.Failure, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 24.dp, top = 10.dp, bottom = 6.dp)) {
        AssistantMark()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(failure.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
            TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Try again") }
        }
    }
}

/** Three dots that rise and fall in turn while the reply is on its way. */
@Composable
private fun Thinking(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Thinking" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistantMark()
        Spacer(Modifier.width(14.dp))
        repeat(3) { index ->
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 1200
                        0f at 0
                        1f at 300 using FastOutSlowInEasing
                        0f at 600 using FastOutSlowInEasing
                    },
                    initialStartOffset = StartOffset(index * 160),
                ),
                label = "dot",
            )
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .size(8.dp)
                    .graphicsLayer {
                        translationY = -phase * 5.dp.toPx()
                        alpha = 0.35f + 0.65f * phase
                    }
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun Intro(
    padding: PaddingValues,
    pendingAnalysis: Boolean,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onSuggestion: (String) -> Unit,
    onOpenReport: () -> Unit,
) {
    val entrance = rememberEntrance()
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        RoundButton(
            R.drawable.ic_mic_fill,
            "Speak",
            onMic,
            AppTheme.colors.accent,
            AppTheme.colors.onAccent,
            size = 104.dp,
            modifier = Modifier
                .reveal({ entrance.value }, 0)
                .graphicsLayer {
                    scaleX = breath
                    scaleY = breath
                },
        )
        Spacer(Modifier.height(18.dp))
        Text("Tap and talk", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.reveal({ entrance.value }, 1))
        Spacer(Modifier.height(6.dp))
        Text(
            "Say “Spent 250 on lunch” and it's recorded, or ask how your month is going.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.reveal({ entrance.value }, 2).padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(24.dp))
        suggestions.chunked(2).forEachIndexed { rowIndex, row ->
            Row(
                Modifier
                    .reveal({ entrance.value }, 3 + rowIndex)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { suggestion ->
                    val interaction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = { onSuggestion(suggestion.text) },
                        shape = RoundedCornerShape(20.dp),
                        color = AppTheme.colors.card,
                        interactionSource = interaction,
                        modifier = Modifier.weight(1f).fillMaxHeight().pressScale(interaction, pressed = 0.96f),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppIcon(suggestion.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(suggestion.text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
        AppCard(Modifier.reveal({ entrance.value }, 5), padding = PaddingValues(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
            Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                MessageField(input, onInputChange, busy = false, onSend, "Or type a question", singleLine = true, modifier = Modifier.weight(1f))
                AnimatedVisibility(
                    visible = input.isNotBlank(),
                    enter = fadeIn(tween(150)) + expandHorizontally(tween(250, easing = EmphasizedDecelerate)) +
                        scaleIn(tween(250, easing = EmphasizedDecelerate), initialScale = 0.6f),
                    exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200, easing = EmphasizedDecelerate)) + scaleOut(tween(150), targetScale = 0.6f),
                ) {
                    RoundButton(R.drawable.ic_arrow_upward, "Send", onSend, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, size = 44.dp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val reportPress = remember { MutableInteractionSource() }
        Surface(
            onClick = onOpenReport,
            shape = RoundedCornerShape(24.dp),
            color = AppTheme.colors.hero,
            interactionSource = reportPress,
            modifier = Modifier.reveal({ entrance.value }, 6).fillMaxWidth().pressScale(reportPress, pressed = 0.97f),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(AppTheme.colors.heroTrack),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(R.drawable.ic_query_stats, null, Modifier.size(22.dp), tint = AppTheme.colors.accent)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Deep analysis", style = MaterialTheme.typography.titleSmall, color = AppTheme.colors.onHero)
                    Text(
                        if (pendingAnalysis) "Analysis in progress…" else "A full report on a year of your spending",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.colors.onHeroMuted,
                    )
                }
                AppIcon(R.drawable.ic_chevron_right, null, tint = AppTheme.colors.onHeroMuted)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ConnectPrompt(padding: PaddingValues, onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = AppTheme.colors.hero, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(AppTheme.colors.heroTrack),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(R.drawable.ic_computer, null, Modifier.size(26.dp), tint = AppTheme.colors.accent)
                }
                Text("Connect Antigravity", style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.onHero)
                Text(
                    "The assistant runs on the Antigravity CLI on your computer, signed in with your own Google account. " +
                        "Start the OpenTracker relay on your PC, then pair this phone with it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.colors.onHeroMuted,
                )
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = AppTheme.colors.onHero, contentColor = AppTheme.colors.hero),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text("Pair with your PC", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun categoryOf(action: AssistantAction): String? = when (action) {
    is AssistantAction.Added -> action.category
    is AssistantAction.Updated -> action.category
    is AssistantAction.Deleted -> action.category
    is AssistantAction.BudgetChanged -> action.category?.name
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
