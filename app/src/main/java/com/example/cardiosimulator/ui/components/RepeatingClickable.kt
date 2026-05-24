package com.example.cardiosimulator.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.remember
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A modifier that triggers [onClick] repeatedly while the component is pressed.
 * Starts with [initialDelayMillis] and then accelerates after [accelerationDelayMillis] 
 * until [minDelayMillis] is reached.
 */
fun Modifier.repeatingClickable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    initialDelayMillis: Long = 600L,
    minDelayMillis: Long = 50L,
    accelerationDelayMillis: Long = 1000L,
    accelerationFactor: Float = 0.85f,
    interactionSource: MutableInteractionSource? = null
): Modifier = composed {
    val currentOnClick by rememberUpdatedState(onClick)
    val localInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    pointerInput(enabled, initialDelayMillis, minDelayMillis, accelerationDelayMillis) {
        if (!enabled) return@pointerInput
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown()
                val pressInteraction = PressInteraction.Press(down.position)
                
                val job = launch {
                    localInteractionSource.emit(pressInteraction)
                    val startTime = System.currentTimeMillis()
                    currentOnClick()
                    delay(initialDelayMillis)
                    var currentDelay = 200L
                    while (true) {
                        currentOnClick()
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed >= accelerationDelayMillis) {
                            currentDelay = (currentDelay * accelerationFactor).toLong().coerceAtLeast(minDelayMillis)
                        }
                        delay(currentDelay)
                    }
                }
                
                val up = waitForUpOrCancellation()
                job.cancel()
                
                launch {
                    if (up != null) {
                        localInteractionSource.emit(PressInteraction.Release(pressInteraction))
                    } else {
                        localInteractionSource.emit(PressInteraction.Cancel(pressInteraction))
                    }
                }
            }
        }
    }
}
