package com.github.damontecres.wholphin.ui.main

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.components.SwitchWithLabel
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun CombinedResultsGrid(
    result: SearchResult,
    focusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val r = result) {
        is SearchResult.Error -> {
            SearchResultPlaceholder(
                title = stringResource(R.string.results),
                message = r.ex.localizedMessage ?: "Error occurred during search",
                messageColor = MaterialTheme.colorScheme.error,
                modifier = modifier,
            )
        }

        SearchResult.NoQuery -> {
            // no-op
        }

        SearchResult.Searching -> {
            SearchResultPlaceholder(
                title = stringResource(R.string.results),
                message = stringResource(R.string.searching),
                modifier = modifier,
            )
        }

        is SearchResult.Success -> {
            if (r.items.isNotEmpty()) {
                CardGrid(
                    pager = r.items,
                    onClickItem = onClickItem,
                    onLongClickItem = { _, _ -> },
                    onClickPlay = { _, _ -> },
                    letterPosition = { letter ->
                        r.items.indexOfFirst {
                            it?.sortName?.firstOrNull()?.uppercaseChar() == letter
                        }
                    },
                    gridFocusRequester = focusRequester,
                    showJumpButtons = true,
                    showLetterButtons = true,
                    modifier = modifier,
                    columns = 6,
                    cardContent = { item, onClick, onLongClick, mod ->
                        GridCard(
                            item = item as BaseItem?,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            imageContentScale = ContentScale.FillBounds,
                            modifier = mod,
                        )
                    },
                )
            }
        }

        else -> {}
    }
}

@Composable
fun SearchViewOptionsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(text = stringResource(R.string.view_options))
    }
}

@Composable
fun SearchViewOptionsDialog(
    combinedMode: Boolean,
    onDismissRequest: () -> Unit,
    onCombinedModeChange: (Boolean) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setGravity(Gravity.END)
            window.setDimAmount(0f)
        }

        Column(
            modifier = Modifier
                .width(256.dp)
                .padding(16.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                    shape = RoundedCornerShape(8.dp)
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.view_options),
                style = MaterialTheme.typography.titleMedium,
            )

            SwitchWithLabel(
                label = stringResource(R.string.combined_search_results),
                checked = combinedMode,
                onStateChange = onCombinedModeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            Text(
                text = if (combinedMode) {
                    stringResource(R.string.combined_search_results_on)
                } else {
                    stringResource(R.string.combined_search_results_off)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SearchResultPlaceholder(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    messageColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(bottom = 32.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = messageColor,
        )
    }
}
