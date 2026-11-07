package dev.openbuds.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One selectable option of a [NoiseModeSelector].
 *
 * @param T Type of the key identifying the option.
 * @property key Value reported through `onSelect` when the option is tapped.
 * @property label String resource used as the tile label.
 * @property icon Outlined icon shown above the label.
 */
data class NoiseModeOption<T>(
    val key: T,
    @param:StringRes val label: Int,
    val icon: ImageVector,
)

/**
 * Row of equally weighted radio-style tiles used to pick a noise control mode.
 *
 * The active tile is filled with `primary`; inactive tiles are transparent with an
 * `outlineVariant` border. Colors animate when the selection changes.
 *
 * @param T Type of the key identifying each option.
 * @param options Options to render, in display order.
 * @param selected Key of the currently selected option, or null when none is selected.
 * @param onSelect Called with the key of the tile the user tapped.
 * @param modifier Modifier applied to the row.
 */
@Composable
fun <T> NoiseModeSelector(
    options: List<NoiseModeOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            NoiseModeTile(
                option = option,
                selected = option.key == selected,
                onClick = { onSelect(option.key) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Single tile of a [NoiseModeSelector].
 *
 * @param T Type of the option key.
 * @param option Option rendered by this tile.
 * @param selected Whether this tile is the current selection.
 * @param onClick Called when the tile is tapped.
 * @param modifier Modifier applied to the tile surface.
 */
@Composable
private fun <T> NoiseModeTile(
    option: NoiseModeOption<T>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "noiseTileContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "noiseTileContent",
    )

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(option.label),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
