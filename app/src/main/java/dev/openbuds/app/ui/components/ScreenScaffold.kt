package dev.openbuds.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.openbuds.app.R

/**
 * Lay out a screen with a collapsing top app bar and pull-to-refresh.
 *
 * The top app bar is large on the root screen (when [onBack] is null) and medium on sub-screens.
 * It starts expanded and collapses as the [content] scrolls. The [content] items are stacked in a
 * vertically scrolling column with 12 dp spacing. The back arrow is shown only when [onBack] is not
 * null. A refresh button sits at the end of the top app bar and is replaced by a progress indicator
 * while [refreshing] is true. Pulling down first expands the bar, then triggers [onRefresh].
 *
 * @param title Text shown in the top app bar.
 * @param onBack Navigation callback for the back arrow, or null to hide it.
 * @param refreshing Whether the pull-to-refresh indicator is shown.
 * @param onRefresh Called when the user pulls down to refresh.
 * @param content Items of the scrolling column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudsScaffold(
    title: String,
    onBack: (() -> Unit)?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val titleContent: @Composable () -> Unit = {
        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val navigationIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        }
    }
    val actions: @Composable RowScope.() -> Unit = {
        if (refreshing) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        } else {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                )
            }
        }
    }
    Scaffold(
        topBar = {
            if (onBack == null) {
                LargeTopAppBar(
                    title = titleContent,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                MediumTopAppBar(
                    title = titleContent,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(innerPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}
