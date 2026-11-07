package dev.openbuds.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val DISABLED_ALPHA = 0.38f

/**
 * A list-style row with a title, optional subtitle and an optional trailing content slot.
 * The row is clickable only when [onClick] is not null.
 *
 * @param title primary text of the row
 * @param subtitle secondary text shown below the title, hidden when null
 * @param onClick click callback, or null to make the row non-clickable
 * @param icon optional leading icon, ignored when [leading] is provided
 * @param leading optional leading content (for example a radio button); takes precedence over [icon]
 * @param content optional trailing content, rendered at the end of the row
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    icon: ImageVector? = null,
    leading: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null,
) {
    BaseSettingRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onClick = onClick,
        enabled = true,
        icon = icon,
        leading = leading,
        content = content,
    )
}

/**
 * A [SettingRow] with a trailing [Switch]. The whole row toggles the value.
 *
 * @param title primary text of the row
 * @param checked current switch state
 * @param onCheckedChange callback with the new state when the row is toggled
 * @param subtitle secondary text shown below the title, hidden when null
 * @param enabled whether the row and its switch respond to input; disabled rows are dimmed
 * @param icon optional leading icon
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    BaseSettingRow(
        title = title,
        modifier =
            modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        subtitle = subtitle,
        onClick = null,
        enabled = enabled,
        icon = icon,
        content = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent =
                    if (checked) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
            )
        },
    )
}

/**
 * A [SettingRow] that navigates on click, with a decorative arrow.
 *
 * @param title primary text of the row
 * @param onClick callback invoked when the row is clicked
 * @param subtitle secondary text shown below the title, hidden when null
 * @param icon optional leading icon
 */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    BaseSettingRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        icon = icon,
        content = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
        enabled = true,
    )
}

/**
 * A [SettingRow]-style row with a trailing [TextButton] that runs [onAction].
 * The row itself is not clickable; only the button triggers the action.
 *
 * @param title primary text of the row
 * @param actionLabel text of the trailing button
 * @param onAction callback invoked when the trailing button is pressed
 * @param icon optional leading icon
 * @param subtitle secondary text shown below the title, hidden when null
 * @param enabled whether the row and its button respond to input; disabled rows are dimmed
 */
@Composable
fun ActionRow(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    BaseSettingRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onClick = null,
        enabled = enabled,
        icon = icon,
        content = {
            TextButton(
                onClick = onAction,
                enabled = enabled,
            ) {
                Text(text = actionLabel)
            }
        },
    )
}

@Composable
private fun BaseSettingRow(
    title: String,
    modifier: Modifier,
    subtitle: String?,
    onClick: (() -> Unit)?,
    enabled: Boolean,
    icon: ImageVector?,
    leading: @Composable (() -> Unit)? = null,
    content: @Composable (() -> Unit)?,
) {
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .then(clickModifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (leading != null) {
            leading()
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
        }
        content?.invoke()
    }
}
