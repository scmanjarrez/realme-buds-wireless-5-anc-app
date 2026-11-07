package dev.openbuds.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.openbuds.app.ui.theme.LocalCardStyle

/**
 * Show a settings card with an optional header and description.
 *
 * The card fills the available width and uses the container color from the theme's card style
 * with no elevation.
 * When [title] is not null, a header is shown with an optional leading [icon] tinted with the
 * primary color. An optional [description] is shown below the header. The [content] is laid out
 * in a [Column] below them.
 *
 * @param title section header shown above the content, or null to omit it
 * @param modifier modifier applied to the card
 * @param icon optional leading icon of the header, or null to show only the title
 * @param description optional explanatory text shown below the header, or null to omit it
 * @param content column content rendered below the header and description
 */
@Composable
fun SettingsCard(
    title: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardStyle = LocalCardStyle.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = cardStyle.containerColor,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardStyle.border,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (title != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = if (title == null) 16.dp else 0.dp,
                            bottom = 8.dp,
                        ),
                )
            }
            content()
        }
    }
}

/**
 * Show a 1 dp divider between rows of a [SettingsCard], inset from the card edges.
 *
 * The divider uses the outline variant color. It is placed explicitly between rows; use
 * [startIndent] of 56 dp for rows with a leading icon and 16 dp for rows without one.
 *
 * @param modifier modifier applied to the divider
 * @param startIndent distance from the card's start edge to where the divider begins
 */
@Composable
fun InsetDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = 56.dp,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
    )
}
