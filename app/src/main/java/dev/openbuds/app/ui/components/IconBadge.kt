package dev.openbuds.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Circular container that displays [icon] on a `primaryContainer` background.
 *
 * The icon is drawn at half of [size]. It is decorative, so it has no content description.
 *
 * @param icon The icon to display.
 * @param modifier Modifier applied to the container.
 * @param size Diameter of the circular container.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(size / 2),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Small filled circle (8 dp) that indicates a status using [color].
 *
 * @param color Fill color of the dot, usually a `colorScheme` role.
 * @param modifier Modifier applied to the dot.
 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(8.dp)
                .background(color, CircleShape),
    )
}
