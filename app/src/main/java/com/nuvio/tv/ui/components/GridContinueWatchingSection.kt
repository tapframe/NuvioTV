package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.screens.home.ContinueWatchingItem
import com.nuvio.tv.ui.theme.NuvioColors

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun GridContinueWatchingSection(
    items: List<ContinueWatchingItem>,
    onItemClick: (ContinueWatchingItem) -> Unit,
    onRemoveItem: (ContinueWatchingItem) -> Unit,
    modifier: Modifier = Modifier,
    focusedItemIndex: Int = -1
) {
    if (items.isEmpty()) return
    var optionsItem by remember { mutableStateOf<ContinueWatchingItem?>(null) }
    val itemFocusRequester = remember { FocusRequester() }
    val focusRequesters = remember(items.size) { List(items.size) { FocusRequester() } }
    var lastFocusedIndex by remember { mutableStateOf(-1) }
    var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(focusedItemIndex) {
        if (focusedItemIndex >= 0 && focusedItemIndex < items.size) {
            kotlinx.coroutines.delay(100)
            try {
                itemFocusRequester.requestFocus()
            } catch (_: IllegalStateException) {
            }
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 12.dp, start = 24.dp, end = 24.dp)
        ) {
            Column {
                Text(
                    text = "Continue Watching",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NuvioColors.TextPrimary
                )
            }
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { _, item ->
                    when (item) {
                        is ContinueWatchingItem.InProgress -> "cw_${item.progress.videoId}"
                        is ContinueWatchingItem.NextUp -> "nextup_${item.info.videoId}"
                    }
                }
            ) { index, progress ->
                val focusModifier = if (pendingFocusIndex == index && index < focusRequesters.size) {
                    Modifier.focusRequester(focusRequesters[index])
                } else if (index == focusedItemIndex) {
                    Modifier.focusRequester(itemFocusRequester)
                } else {
                    Modifier
                }

                ContinueWatchingCard(
                    item = progress,
                    onClick = { onItemClick(progress) },
                    onLongPress = { optionsItem = progress },
                    modifier = focusModifier
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                lastFocusedIndex = index
                            }
                        },
                    cardWidth = 240.dp,
                    imageHeight = 135.dp
                )
            }
        }
    }

    val menuItem = optionsItem
    if (menuItem != null) {
        ContinueWatchingOptionsDialog(
            item = menuItem,
            onDismiss = { optionsItem = null },
            onRemove = {
                val targetIndex = if (items.size <= 1) null else minOf(lastFocusedIndex, items.size - 2)
                pendingFocusIndex = targetIndex
                onRemoveItem(menuItem)
                optionsItem = null
            },
            onDetails = {
                onItemClick(menuItem)
                optionsItem = null
            }
        )
    }

    LaunchedEffect(items.size, pendingFocusIndex) {
        val target = pendingFocusIndex
        if (target != null && target >= 0 && target < focusRequesters.size) {
            kotlinx.coroutines.delay(100)
            try {
                focusRequesters[target].requestFocus()
            } catch (_: IllegalStateException) {
            }
            pendingFocusIndex = null
        }
    }
}
