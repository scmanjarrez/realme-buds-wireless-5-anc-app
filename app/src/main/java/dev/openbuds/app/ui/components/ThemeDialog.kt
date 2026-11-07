package dev.openbuds.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.openbuds.app.R
import dev.openbuds.app.ui.theme.ThemeMode

private val ROW_HORIZONTAL_PADDING = 16.dp
private val ROW_VERTICAL_PADDING = 12.dp
private val ICON_SIZE = 24.dp
private val ICON_SPACING = 16.dp

/**
 * Dialog that lets the user choose the app theme: System, Light or Dark.
 *
 * Tapping an option applies it and closes the dialog. The dialog has no confirm button.
 *
 * @param current the currently selected theme mode, shown as selected
 * @param onSelect called with the mode the user tapped
 * @param onDismiss called when the dialog is closed, after a selection or when dismissed
 */
@Composable
fun ThemeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Text(
                text = stringResource(R.string.theme_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ThemeOption(
                    mode = ThemeMode.SYSTEM,
                    label = stringResource(R.string.theme_system),
                    icon = Icons.Outlined.BrightnessAuto,
                    selected = current == ThemeMode.SYSTEM,
                    onSelect = onSelect,
                    onDismiss = onDismiss,
                )
                ThemeOption(
                    mode = ThemeMode.LIGHT,
                    label = stringResource(R.string.theme_light),
                    icon = Icons.Outlined.LightMode,
                    selected = current == ThemeMode.LIGHT,
                    onSelect = onSelect,
                    onDismiss = onDismiss,
                )
                ThemeOption(
                    mode = ThemeMode.DARK,
                    label = stringResource(R.string.theme_dark),
                    icon = Icons.Outlined.DarkMode,
                    selected = current == ThemeMode.DARK,
                    onSelect = onSelect,
                    onDismiss = onDismiss,
                )
                ThemeOption(
                    mode = ThemeMode.BLACK,
                    label = stringResource(R.string.theme_black),
                    icon = Icons.Outlined.Contrast,
                    selected = current == ThemeMode.BLACK,
                    onSelect = onSelect,
                    onDismiss = onDismiss,
                )
            }
        },
    )
}

/**
 * One selectable row of [ThemeDialog]: icon, label and radio button.
 *
 * @param mode the theme mode this row represents
 * @param label visible name of the theme mode
 * @param icon leading icon of the row
 * @param selected whether this mode is the current one
 * @param onSelect called with [mode] when the row is tapped
 * @param onDismiss called after [onSelect] when the row is tapped
 */
@Composable
private fun ThemeOption(
    mode: ThemeMode,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .selectable(
                    selected = selected,
                    onClick = {
                        onSelect(mode)
                        onDismiss()
                    },
                    role = Role.RadioButton,
                ).padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = ROW_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.width(ICON_SIZE),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(ICON_SPACING))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}
