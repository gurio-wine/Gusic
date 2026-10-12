package com.gurio.gusic.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.gurio.gusic.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drives the player-page <-> lyrics-page reveal transition.
 *
 * The fraction itself lives on [PlayerViewModel] (single source of truth shared by the
 * full-player sections and the underlying LyricsSheet), but the animation must be launched
 * from a composition-attached scope: `Animatable.animateTo` needs a MonotonicFrameClock,
 * which a ViewModel scope does not provide.
 *
 * 0f = covered (player page on top, only the in-player lyrics window visible),
 * 1f = revealed (lyrics page fully expanded on top of the player page).
 */
internal class LyricsRevealController(
    private val animatable: Animatable<Float, AnimationVector1D>,
    private val scope: CoroutineScope
) {
    val isRunning: Boolean get() = animatable.isRunning

    fun expand() {
        scope.launch {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }
    }

    fun collapse() {
        scope.launch {
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
            )
        }
    }

    fun snapTo(value: Float) {
        scope.launch {
            animatable.snapTo(value)
        }
    }
}

@Composable
internal fun rememberLyricsRevealController(
    playerViewModel: PlayerViewModel
): LyricsRevealController {
    val scope = rememberCoroutineScope()
    return remember(playerViewModel, scope) {
        LyricsRevealController(
            animatable = playerViewModel.lyricsRevealFraction,
            scope = scope
        )
    }
}
