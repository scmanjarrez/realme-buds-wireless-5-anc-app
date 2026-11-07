package dev.openbuds.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val SETTLE_MS = 1500L

@Composable
fun LabeledSlider(
    title: String,
    value: Int,
    range: IntRange,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: (Int) -> String = { it.toString() },
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value.toFloat()) }
    var lastInteraction by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }

    LaunchedEffect(value, dragging) {
        if (!dragging) {
            val wait = SETTLE_MS.milliseconds - lastInteraction.elapsedNow()
            if (wait.isPositive()) delay(wait)
            if (!dragging) local = value.toFloat()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueLabel(local.roundToInt()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local,
            onValueChange = {
                dragging = true
                lastInteraction = TimeSource.Monotonic.markNow()
                local = it
            },
            onValueChangeFinished = {
                dragging = false
                lastInteraction = TimeSource.Monotonic.markNow()
                onCommit(local.roundToInt())
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
