package dev.openbuds.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.openbuds.app.R
import dev.openbuds.app.protocol.FitStatus
import dev.openbuds.app.ui.BudsViewModel
import dev.openbuds.app.ui.FitTestUiState
import dev.openbuds.app.ui.components.BudsScaffold
import dev.openbuds.app.ui.components.SettingRow
import dev.openbuds.app.ui.components.SettingsCard

/**
 * Earbud fit test screen: starts the fit check and shows the result for each earbud.
 *
 * The test state is reset when the screen leaves composition.
 *
 * @param viewModel Source of the fit test state, its actions and the refresh state.
 * @param onBack Navigation callback for the back arrow.
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
                StateColumn {
                    Text(
                        text = stringResource(R.string.fit_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    FilledTonalButton(onClick = viewModel::startFitTest) {
                        Text(text = stringResource(R.string.fit_start))
                    }
                }
            }

            FitTestUiState.Running -> {
                StateColumn {
                    CircularProgressIndicator()
                    Message(text = stringResource(R.string.fit_running))
                }
            }

            FitTestUiState.NotInserted -> {
                StateColumn {
                    Message(text = stringResource(R.string.fit_not_inserted))
                    FilledTonalButton(onClick = viewModel::startFitTest) {
                        Text(text = stringResource(R.string.fit_try_again))
                    }
                }
            }

            FitTestUiState.Failed -> {
                StateColumn {
                    Message(text = stringResource(R.string.fit_failed))
                    FilledTonalButton(onClick = viewModel::startFitTest) {
                        Text(text = stringResource(R.string.fit_try_again))
                    }
                }
            }

            is FitTestUiState.Done -> {
                DoneContent(result = state, onRetry = viewModel::startFitTest)
            }
        }
    }
}

/** Result view: a success badge when both earbuds are good, otherwise per-earbud status with a fit tip. */
@Composable
private fun DoneContent(
    result: FitTestUiState.Done,
    onRetry: () -> Unit,
) {
    if (result.left == FitStatus.GOOD && result.right == FitStatus.GOOD) {
        StateColumn {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.fit_perfect),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        SettingsCard(title = null) {
            SettingRow(
                title = stringResource(R.string.fit_left),
                subtitle = fitStatusText(result.left),
            )
            SettingRow(
                title = stringResource(R.string.fit_right),
                subtitle = fitStatusText(result.right),
            )
        }
        Text(
            text = tipText(result),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        StateColumn {
            FilledTonalButton(onClick = onRetry) {
                Text(text = stringResource(R.string.fit_test_again))
            }
        }
    }
}

/** Centered column holding the body of one test state. */
@Composable
private fun StateColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** Centered body text used for state messages. */
@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Maps a per-earbud fit status to the subtitle shown in the result card. */
@Composable
private fun fitStatusText(status: FitStatus): String =
    when (status) {
        FitStatus.GOOD -> stringResource(R.string.fit_good)
        FitStatus.NOT_IDEAL -> stringResource(R.string.fit_adjust)
        else -> stringResource(R.string.fit_unknown)
    }

/** Picks the fit tip matching which earbud(s) need adjusting. */
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
