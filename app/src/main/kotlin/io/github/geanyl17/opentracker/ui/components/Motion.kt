package io.github.geanyl17.opentracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

/** Material's emphasized curves: things arrive fast and settle gently, and leave by speeding up. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Shrinks what's being pressed a little, and springs it back on release. */
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.94f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Goes from 0 to 1 the first time it's shown and again whenever [key] changes, once [ready]. Coming back to it, from
 * another tab or by scrolling, finds it finished rather than playing it again.
 */
@Composable
fun rememberPlayback(
    key: Any? = Unit,
    ready: Boolean = true,
    animation: AnimationSpec<Float> = tween(900, delayMillis = 100, easing = EmphasizedDecelerate),
): Animatable<Float, AnimationVector1D> {
    val id = key.toString()
    var played by rememberSaveable { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(if (played == id) 1f else 0f) }
    LaunchedEffect(id, ready) {
        if (ready && played != id) {
            progress.snapTo(0f)
            progress.animateTo(1f, animation)
            played = id
        }
    }
    return progress
}

/** Animates from the value it last showed to [target], or up from zero the first time. */
@Composable
fun rememberFill(
    target: Float,
    animation: AnimationSpec<Float> = tween(900, delayMillis = 100, easing = EmphasizedDecelerate),
): Animatable<Float, AnimationVector1D> {
    var settled by rememberSaveable { mutableFloatStateOf(0f) }
    val fill = remember { Animatable(settled) }
    LaunchedEffect(target) {
        if (fill.value == target) return@LaunchedEffect
        fill.animateTo(target, animation)
        settled = target
    }
    return fill
}

/** Drives [reveal] for the blocks of a screen, once its content is [ready]. */
@Composable
fun rememberEntrance(ready: Boolean = true): Animatable<Float, AnimationVector1D> =
    rememberPlayback(ready = ready, animation = tween(900, easing = LinearEasing))

/** Fades and lifts the [index]-th block of a screen into place as [progress] goes from 0 to 1, one after another. */
fun Modifier.reveal(progress: () -> Float, index: Int): Modifier = graphicsLayer {
    val local = ((progress() - index * 0.07f) / 0.45f).coerceIn(0f, 1f)
    val eased = EmphasizedDecelerate.transform(local)
    alpha = eased
    translationY = (1f - eased) * 24.dp.toPx()
}

/** An amount that counts from where it was to [target], or up from zero the first time. */
@Composable
fun countUp(target: Long, durationMillis: Int = 900): Long {
    var to by rememberSaveable { mutableLongStateOf(0L) }
    var from by remember { mutableLongStateOf(to) }
    val progress = remember { Animatable(1f) }
    fun current() = from + ((to - from) * progress.value.toDouble()).roundToLong()
    LaunchedEffect(target) {
        if (target == to && progress.value == 1f) return@LaunchedEffect
        from = current()
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis, delayMillis = 100, easing = FastOutSlowInEasing))
    }
    return current()
}
