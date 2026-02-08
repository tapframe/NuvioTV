package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.ui.theme.NuvioColors

private val SeeAllCardShape = RoundedCornerShape(8.dp)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CatalogRowSection(
    catalogRow: CatalogRow,
    onItemClick: (String, String, String) -> Unit,
    onLoadMore: () -> Unit,
    onSeeAll: () -> Unit = {},
    modifier: Modifier = Modifier,
    isLoadingMore: Boolean = false,
    initialScrollIndex: Int = 0,
    focusedItemIndex: Int = -1,
    onItemFocused: (itemIndex: Int) -> Unit = {}
) {

    val listState = rememberTvLazyListState()

    // Restore scroll position if needed
    LaunchedEffect(initialScrollIndex) {
        if (initialScrollIndex > 0) {
            listState.scrollToItem(initialScrollIndex)
        }
    }

    val currentOnItemFocused by rememberUpdatedState(onItemFocused)
    val currentHasMore by rememberUpdatedState(catalogRow.hasMore)
    val currentIsLoadingMore by rememberUpdatedState(isLoadingMore)
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    // Only allocate FocusRequester when actually restoring focus
    val itemFocusRequester = if (focusedItemIndex >= 0) {
        val requester = remember { FocusRequester() }
        LaunchedEffect(focusedItemIndex) {
            if (focusedItemIndex < catalogRow.items.size) {
                kotlinx.coroutines.delay(100)
                try {
                    requester.requestFocus()
                } catch (_: IllegalStateException) { }
            }
        }
        requester
    } else null

    // Check load-more only when scrolling stops — avoids per-frame layoutInfo reads
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val total = listState.layoutInfo.totalItemsCount
                    if (lastVisible >= total - 5 && currentHasMore && !currentIsLoadingMore) {
                        currentOnLoadMore()
                    }
                }
            }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${catalogRow.catalogName.replaceFirstChar { it.uppercase() }} - ${catalogRow.type.toApiString().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = NuvioColors.TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Clip
                )
                Text(
                    text = "from ${catalogRow.addonName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioColors.TextTertiary
                )
            }
        }

        TvLazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = catalogRow.items,
                key = { index, item -> "${catalogRow.type}_${catalogRow.catalogId}_${item.id}_$index" },
                contentType = { _, _ -> "content_card" }
            ) { index, item ->
                ContentCard(
                    item = item,
                    onClick = { onItemClick(item.id, item.type.toApiString(), catalogRow.addonBaseUrl) },
                    modifier = Modifier.onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            currentOnItemFocused(index)
                        }
                    },
                    focusRequester = if (index == focusedItemIndex) itemFocusRequester else null
                )
            }

            if (isLoadingMore || catalogRow.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(225.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
            }

            // "See All" card — only when catalog has more items available
            if (!isLoadingMore && !catalogRow.isLoading && catalogRow.items.size >= 15) {
                item(key = "${catalogRow.type}_${catalogRow.catalogId}_see_all") {
                    Card(
                        onClick = onSeeAll,
                        modifier = Modifier
                            .width(140.dp)
                            .height(210.dp),
                        shape = CardDefaults.shape(shape = SeeAllCardShape),
                        colors = CardDefaults.colors(
                            containerColor = NuvioColors.BackgroundCard,
                            focusedContainerColor = NuvioColors.BackgroundCard
                        ),
                        border = CardDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                                shape = SeeAllCardShape
                            )
                        ),
                        scale = CardDefaults.scale(focusedScale = 1.02f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "See All",
                                    modifier = Modifier.size(32.dp),
                                    tint = NuvioColors.TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "See All",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NuvioColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
