package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.ui.components.CatalogRowSection
import com.nuvio.tv.ui.components.ContinueWatchingSection
import com.nuvio.tv.ui.components.HeroCarousel
import com.nuvio.tv.ui.components.PosterCardStyle

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ClassicHomeContent(
    uiState: HomeUiState,
    posterCardStyle: PosterCardStyle,
    focusState: HomeScreenFocusState,
    onNavigateToDetail: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onNavigateToCatalogSeeAll: (String, String, String) -> Unit,
    onRemoveContinueWatching: (String) -> Unit,
    onSaveFocusState: (Int, Int, Int, Int, Map<String, Int>) -> Unit
) {

    val columnListState = rememberLazyListState()

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
    
    // Store scroll state for each row to persist position during recycling
    val rowStates = remember { mutableMapOf<String, LazyListState>() }
    
    val catalogRowScrollStates = remember { mutableMapOf<String, Int>() }
    val perCatalogFocusedItem = remember { mutableMapOf<String, Int>() }
    var restoringFocus by remember { mutableStateOf(focusState.hasSavedFocus) }
    val heroFocusRequester = remember { FocusRequester() }
    val shouldRequestInitialFocus = remember(focusState) {
        !focusState.hasSavedFocus &&
            focusState.verticalScrollIndex == 0 &&
            focusState.verticalScrollOffset == 0
    }
    val visibleCatalogRows = remember(uiState.catalogRows) {
        uiState.catalogRows.filter { it.items.isNotEmpty() }
    }

    DisposableEffect(Unit) {
        onDispose {

            onSaveFocusState(
                columnListState.firstVisibleItemIndex,
                columnListState.firstVisibleItemScrollOffset,
                currentFocusedRowIndex,
                currentFocusedItemIndex,
                focusState.catalogRowScrollStates + rowStates.mapValues { it.value.firstVisibleItemIndex }
            )
        }
    }

    val heroVisible = uiState.heroSectionEnabled && uiState.heroItems.isNotEmpty()

    LaunchedEffect(shouldRequestInitialFocus, heroVisible, uiState.heroItems.size) {
        if (!shouldRequestInitialFocus || !heroVisible) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        try {
            heroFocusRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    LazyColumn(
        state = columnListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = if (heroVisible) 0.dp else 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        if (heroVisible) {
            item(key = "hero_carousel", contentType = "hero") {
                HeroCarousel(
                    items = uiState.heroItems,
                    focusRequester = if (shouldRequestInitialFocus) heroFocusRequester else null,
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
                    onItemClick = { item ->
                        onContinueWatchingClick(item)
                    },
                    onDetailsClick = { item ->
                        onNavigateToDetail(
                            when (item) {
                                is ContinueWatchingItem.InProgress -> item.progress.contentId
                                is ContinueWatchingItem.NextUp -> item.info.contentId
                            },
                            when (item) {
                                is ContinueWatchingItem.InProgress -> item.progress.contentType
                                is ContinueWatchingItem.NextUp -> item.info.contentType
                            },
                            ""
                        )
                    },
                    onRemoveItem = { item ->
                        val contentId = when (item) {
                            is ContinueWatchingItem.InProgress -> item.progress.contentId
                            is ContinueWatchingItem.NextUp -> item.info.contentId
                        }
                        onRemoveContinueWatching(contentId)
                    },
                    focusedItemIndex = when {
                        focusState.hasSavedFocus && focusState.focusedRowIndex == -1 -> focusState.focusedItemIndex
                        shouldRequestInitialFocus && !heroVisible -> 0
                        else -> -1
                    },
                    onItemFocused = { itemIndex ->
                        currentFocusedRowIndex = -1
                        currentFocusedItemIndex = itemIndex
                    }
                )
            }
        }

        itemsIndexed(
            items = visibleCatalogRows,
            key = { _, item -> "${item.addonId}_${item.type}_${item.catalogId}" },
            contentType = { _, _ -> "catalog_row" }
        ) { index, catalogRow ->
            val catalogKey = "${catalogRow.addonId}_${catalogRow.type.toApiString()}_${catalogRow.catalogId}"
            val shouldRestoreFocus = restoringFocus && index == focusState.focusedRowIndex
            val shouldInitialFocusFirstCatalogRow =
                shouldRequestInitialFocus &&
                    !heroVisible &&
                    uiState.continueWatchingItems.isEmpty() &&
                    index == 0
            val focusedItemIndex = when {
                shouldRestoreFocus -> focusState.focusedItemIndex
                shouldInitialFocusFirstCatalogRow -> 0
                else -> -1
            }

            val listState = rowStates.getOrPut(catalogKey) {
                LazyListState(
                    firstVisibleItemIndex = focusState.catalogRowScrollStates[catalogKey] ?: 0
                )
            }

            CatalogRowSection(
                catalogRow = catalogRow,
                posterCardStyle = posterCardStyle,
                showPosterLabels = uiState.posterLabelsEnabled,
                showAddonName = uiState.catalogAddonNameEnabled,
                focusedPosterBackdropExpandEnabled = uiState.focusedPosterBackdropExpandEnabled,
                onItemClick = { id, type, addonBaseUrl ->
                    onNavigateToDetail(id, type, addonBaseUrl)
                },
                onSeeAll = {
                    onNavigateToCatalogSeeAll(
                        catalogRow.catalogId,
                        catalogRow.addonId,
                        catalogRow.type.toApiString()
                    )
                },
                listState = listState,
                // We don't need initialScrollIndex anymore as listState handles it
                focusedItemIndex = focusedItemIndex,
                onItemFocused = { itemIndex ->
                    restoringFocus = false
                    currentFocusedRowIndex = index
                    currentFocusedItemIndex = itemIndex
                    perCatalogFocusedItem[catalogKey] = itemIndex
                    catalogRowScrollStates[catalogKey] = itemIndex
                    // Update the state as well, though getOrPut handles creation
                    // rowStates[catalogKey] already holds the live state object
                }
            )
        }
    }
}
