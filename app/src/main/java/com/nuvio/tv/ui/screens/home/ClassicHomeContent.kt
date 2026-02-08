package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.ui.components.CatalogRowSection
import com.nuvio.tv.ui.components.ContinueWatchingSection
import com.nuvio.tv.ui.components.HeroCarousel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ClassicHomeContent(
    uiState: HomeUiState,
    focusState: HomeScreenFocusState,
    loadingCatalogs: Set<String> = emptySet(),
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToCatalogSeeAll: (String, String, String) -> Unit,
    onLoadMore: (catalogId: String, addonId: String, type: String) -> Unit,
    onSaveFocusState: (Int, Int, Int, Int, Map<String, Int>) -> Unit
) {

    val columnListState = rememberTvLazyListState()

    LaunchedEffect(focusState.verticalScrollIndex, focusState.verticalScrollOffset) {
        if (focusState.verticalScrollIndex > 0 || focusState.verticalScrollOffset > 0) {
            columnListState.scrollToItem(
                focusState.verticalScrollIndex,
                focusState.verticalScrollOffset
            )
        }
    }

    var currentFocusedRowIndex by remember { mutableStateOf(focusState.focusedRowIndex) }
    var currentFocusedItemIndex by remember { mutableStateOf(focusState.focusedItemIndex) }
    val catalogRowScrollStates = remember { mutableMapOf<String, Int>() }

    DisposableEffect(Unit) {
        onDispose {
            onSaveFocusState(
                columnListState.firstVisibleItemIndex,
                columnListState.firstVisibleItemScrollOffset,
                currentFocusedRowIndex,
                currentFocusedItemIndex,
                catalogRowScrollStates.toMap()
            )
        }
    }

    TvLazyColumn(
        state = columnListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        if (uiState.heroItems.isNotEmpty()) {
            item(key = "hero_carousel", contentType = "hero") {
                HeroCarousel(
                    items = uiState.heroItems,
                    onItemClick = { item ->
                        onNavigateToDetail(
                            item.id,
                            item.type.toApiString(),
                            ""
                        )
                    }
                )
            }
        }

        if (uiState.continueWatchingItems.isNotEmpty()) {
            item(key = "continue_watching", contentType = "continue_watching") {
                ContinueWatchingSection(
                    items = uiState.continueWatchingItems,
                    onItemClick = { progress ->
                        onNavigateToDetail(
                            progress.contentId,
                            progress.contentType,
                            ""
                        )
                    },
                    focusedItemIndex = if (focusState.focusedRowIndex == -1) focusState.focusedItemIndex else -1,
                    onItemFocused = { itemIndex ->
                        currentFocusedRowIndex = -1
                        currentFocusedItemIndex = itemIndex
                    }
                )
            }
        }

        val visibleCatalogRows = uiState.catalogRows.filter { it.items.isNotEmpty() }

        itemsIndexed(
            items = visibleCatalogRows,
            key = { _, item -> "${item.addonId}_${item.type}_${item.catalogId}" },
            contentType = { _, _ -> "catalog_row" }
        ) { index, catalogRow ->
            val catalogKey = "${catalogRow.addonId}_${catalogRow.type.toApiString()}_${catalogRow.catalogId}"
            val shouldRestoreFocus = index == focusState.focusedRowIndex
            val focusedItemIndex = if (shouldRestoreFocus) focusState.focusedItemIndex else -1

            val loadMoreKey = "${catalogRow.addonId}_${catalogRow.type.toApiString()}_${catalogRow.catalogId}"

            CatalogRowSection(
                catalogRow = catalogRow,
                isLoadingMore = loadMoreKey in loadingCatalogs,
                onItemClick = { id, type, addonBaseUrl ->
                    onNavigateToDetail(id, type, addonBaseUrl)
                },
                onLoadMore = {
                    onLoadMore(
                        catalogRow.catalogId,
                        catalogRow.addonId,
                        catalogRow.type.toApiString()
                    )
                },
                onSeeAll = {
                    onNavigateToCatalogSeeAll(
                        catalogRow.catalogId,
                        catalogRow.addonId,
                        catalogRow.type.toApiString()
                    )
                },
                initialScrollIndex = focusState.catalogRowScrollStates[catalogKey] ?: 0,
                focusedItemIndex = focusedItemIndex,
                onItemFocused = { itemIndex ->
                    currentFocusedRowIndex = index
                    currentFocusedItemIndex = itemIndex
                    catalogRowScrollStates[catalogKey] = itemIndex
                }
            )
        }
    }
}
