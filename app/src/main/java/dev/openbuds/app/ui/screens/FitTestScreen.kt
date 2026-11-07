package dev.openbuds.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Earbuds
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openbuds.app.R
import dev.openbuds.app.protocol.FitStatus
import dev.openbuds.app.ui.BudsViewModel
import dev.openbuds.app.ui.FitTestUiState
import dev.openbuds.app.ui.components.BudsScaffold
import dev.openbuds.app.ui.components.IconBadge
import dev.openbuds.app.ui.components.InsetDivider
import dev.openbuds.app.ui.components.SettingRow
import dev.openbuds.app.ui.components.SettingsCard

/**
 * Earbud fit test screen: starts the fit check and shows the result for each earbud.
 *
 * The test state is reset when the screen leaves composition.
 *
 * @param viewModel source of the fit test state, its actions and the refresh state
 * @param onBack navigation callback for the back arrow
 */
@Composable
fun FitTestScreen(
    viewModel: BudsViewModel,
    onBack: () -> Unit,
) {
    val fitTest by viewModel.fitTest.collectAsStateWithLifecycle()
    val refreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { viewModel.resetFitTest() }
    }

    BudsScaffold(
        title = stringResource(R.string.home_fit_test),
        onBack = onBack,
        refreshing = refreshing,
        onRefresh = viewModel::reload,
    ) {
        when (val state = fitTest) {
            FitTestUiState.Idle -> {
                StateCard(description = stringResource(R.string.fit_description)) {
                    IconBadge(icon = Icons.Outlined.Earbuds, size = 72.dp)
                    ActionButton(label = R.string.fit_start, onClick = viewModel::startFitTest)
                }
            }

            FitTestUiState.Running -> {
                StateCard(description = stringResource(R.string.fit_running)) {
                    CircularProgressIndicator(modifier = Modifier.size(72.dp))
                }
            }

            FitTestUiState.NotInserted -> {
                StateCard(description = stringResource(R.string.fit_not_inserted)) {
                    IconBadge(icon = Icons.Outlined.Earbuds, size = 72.dp)
                    ActionButton(label = R.string.fit_try_again, onClick = viewModel::startFitTest)
                }
            }

            FitTestUiState.Failed -> {
                StateCard(description = stringResource(R.string.fit_failed)) {
                    IconBadge(icon = Icons.Outlined.Earbuds, size = 72.dp)
                    ActionButton(label = R.string.fit_try_again, onClick = viewModel::startFitTest)
                }
            }

            is FitTestUiState.Done -> {
                DoneContent(result = state, onRetry = viewModel::startFitTest)
            }
        }
    }
}

/**
 * Result card: a header with the success state when both earbuds are [FitStatus.GOOD], otherwise a
 * fit tip. Each earbud gets a status row, followed by a full-width retry button.
 *
 * @param result fit test outcome for the left and right earbud
 * @param onRetry callback that starts a new fit test
 */
@Composable
private fun DoneContent(
    result: FitTestUiState.Done,
    onRetry: () -> Unit,
) {
    val perfect = result.left == FitStatus.GOOD && result.right == FitStatus.GOOD
    SettingsCard(
        title = if (perfect) stringResource(R.string.fit_perfect) else null,
        icon = if (perfect) Icons.Outlined.CheckCircle else null,
        description = if (perfect) null else tipText(result),
    ) {
        SettingRow(
            title = stringResource(R.string.fit_left),
            subtitle = fitStatusText(result.left),
            leading = { FitStatusIcon(status = result.left) },
        )
        InsetDivider()
        SettingRow(
            title = stringResource(R.string.fit_right),
            subtitle = fitStatusText(result.right),
            leading = { FitStatusIcon(status = result.right) },
        )
        ActionButton(label = R.string.fit_test_again, onClick = onRetry)
    }
}

/**
 * Card used by the non-result states: an explanation as description, followed by the state body.
 *
 * @param description explanatory text shown inside the card
 * @param content state body (badge or progress indicator, and optional action button)
 */
@Composable
private fun StateCard(
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsCard(title = null, description = description) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/**
 * Full-width tonal button placed inside a card.
 *
 * @param label string resource of the button text
 * @param onClick click callback
 */
@Composable
private fun ActionButton(
    label: Int,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(label))
    }
}

/**
 * Leading status icon of an earbud row: a check in `primary` for [FitStatus.GOOD], otherwise an
 * alert in `error`.
 *
 * @param status fit status of one earbud
 */
@Composable
private fun FitStatusIcon(status: FitStatus) {
    val good = status == FitStatus.GOOD
    Icon(
        imageVector = if (good) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
        contentDescription = null,
        modifier = Modifier.size(24.dp),
        tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}

/**
 * Maps a per-earbud fit status to the subtitle shown in the result card.
 *
 * [FitStatus.GOOD] and [FitStatus.NOT_IDEAL] have their own labels. Every other status, including
 * [FitStatus.FAILED] and [FitStatus.NOT_INSERTED], falls back to the unknown label.
 *
 * @param status fit status of one earbud
 * @return localized subtitle text
 */
@Composable
private fun fitStatusText(status: FitStatus): String =
    when (status) {
        FitStatus.GOOD -> stringResource(R.string.fit_good)
        FitStatus.NOT_IDEAL -> stringResource(R.string.fit_adjust)
        else -> stringResource(R.string.fit_unknown)
    }

/**
 * Picks the fit tip matching which earbud(s) need adjusting.
 *
 * An earbud needs adjusting when its status is anything other than [FitStatus.GOOD].
 *
 * @param result fit test outcome for the left and right earbud
 * @return localized tip text for the left, right or both earbuds
 */
@Composable
private fun tipText(result: FitTestUiState.Done): String {
    val leftNeedsAdjust = result.left != FitStatus.GOOD
    val rightNeedsAdjust = result.right != FitStatus.GOOD
    return when {
        leftNeedsAdjust && rightNeedsAdjust -> stringResource(R.string.fit_tip_both)
        leftNeedsAdjust -> stringResource(R.string.fit_tip_left)
        else -> stringResource(R.string.fit_tip_right)
    }
}
