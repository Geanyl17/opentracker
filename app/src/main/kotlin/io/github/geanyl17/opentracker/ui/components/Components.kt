package io.github.geanyl17.opentracker.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.geanyl17.opentracker.R
import io.github.geanyl17.opentracker.data.Entry
import io.github.geanyl17.opentracker.data.Kind
import io.github.geanyl17.opentracker.data.Money
import io.github.geanyl17.opentracker.ui.theme.AppTheme
import io.github.geanyl17.opentracker.ui.theme.tabular
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

val LocalMoney = staticCompositionLocalOf { Money.of("USD") }
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

@Composable
fun AppIcon(@DrawableRes id: Int, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(painterResource(id), contentDescription, modifier, tint)
}

@Composable
fun AppCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(20.dp), content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = AppTheme.colors.card) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** A round button with a soft background, for the actions in a screen's header. */
@Composable
fun CircleIconButton(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = CircleShape, color = AppTheme.colors.card, modifier = modifier.size(44.dp)) {
        Box(contentAlignment = Alignment.Center) {
            AppIcon(icon, contentDescription, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Covers the status bar, so content scrolling under it stays out of the way of the clock and icons. */
@Composable
fun StatusBarScrim() {
    Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(MaterialTheme.colorScheme.background))
}

@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: (@Composable () -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            subtitle?.invoke()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** The header of a screen opened on top of the tabs: a round back or close button, the title and actions. */
@Composable
fun TopBar(
    title: String,
    onBack: () -> Unit,
    @DrawableRes backIcon: Int = R.drawable.ic_arrow_back,
    backDescription: String = "Back",
    actions: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(backIcon, backDescription, onBack)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, bottom = 12.dp).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun AmountText(
    cents: Long,
    kind: Kind,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
) {
    val money = LocalMoney.current
    Text(
        text = if (kind == Kind.INCOME) "+" + money.format(cents) else "−" + money.format(cents),
        modifier = modifier,
        style = style.tabular(),
        color = if (kind == Kind.INCOME) AppTheme.colors.positive else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
    )
}

@Composable
fun CategoryIcon(name: String?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val look = CategoryLooks.of(name)
    val alpha = if (AppTheme.colors.dark) 0.22f else 0.14f
    Box(
        modifier.size(size).clip(CircleShape).background(look.color.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        if (look.icon != null) {
            AppIcon(look.icon, null, Modifier.size(size * 0.5f), tint = look.color)
        } else {
            Text(
                name.orEmpty().trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = look.color,
            )
        }
    }
}

/** A bar split into colored parts, each a fraction of the whole, on a track. */
@Composable
fun SegmentedBar(parts: List<Pair<Float, Color>>, track: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Canvas(modifier.fillMaxWidth().height(height).clip(CircleShape)) {
        drawRect(track)
        val gap = 2.dp.toPx()
        var x = 0f
        parts.filter { it.first > 0f }.forEach { (fraction, color) ->
            if (x >= size.width) return@forEach
            val width = (fraction * size.width * progress.value).coerceAtMost(size.width - x)
            drawRect(color, topLeft = Offset(x, 0f), size = Size((width - gap).coerceAtLeast(1.dp.toPx()), size.height))
            x += width
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600, easing = FastOutSlowInEasing), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (animated > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(animated.coerceAtLeast(0.03f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** A ring split into colored arcs, drawn with small gaps between them. */
@Composable
fun DonutChart(parts: List<Pair<Float, Color>>, modifier: Modifier = Modifier, thickness: Dp = 16.dp, center: @Composable () -> Unit = {}) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = thickness.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val total = parts.sumOf { it.first.toDouble() }.toFloat()
            if (total <= 0f) return@Canvas
            val gapDegrees = if (parts.size > 1) 2.5f else 0f
            var start = -90f
            parts.forEach { (value, color) ->
                val full = value / total * 360f * sweep.value
                drawArc(color, start + gapDegrees / 2, (full - gapDegrees).coerceAtLeast(0.8f), false, topLeft, arcSize, style = Stroke(stroke))
                start += full
            }
        }
        center()
    }
}

@Composable
fun EntryRow(entry: Entry, category: String?, onClick: () -> Unit, showDate: Boolean = true) {
    val name = category ?: "Uncategorized"
    val subtitle = listOfNotNull(
        name.takeIf { entry.note.isNotBlank() },
        dayLabel(entry.date).takeIf { showDate },
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.note.ifBlank { name },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AmountText(entry.amount, entry.kind)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")
private val dayWithYearFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val monthFormat = DateTimeFormatter.ofPattern("LLLL")
private val monthWithYearFormat = DateTimeFormatter.ofPattern("LLLL yyyy")

fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when {
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    date.year == today.year -> date.format(dayFormat)
    else -> date.format(dayWithYearFormat)
}

fun monthLabel(month: YearMonth, current: YearMonth = YearMonth.now()): String =
    month.format(if (month.year == current.year) monthFormat else monthWithYearFormat)
