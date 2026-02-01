package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.ui.components.CatalogRowSection
import com.nuvio.tv.ui.components.ContinueWatchingSection
import com.nuvio.tv.ui.components.ErrorState
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Remember the column scroll state across navigation
    val columnListState = rememberTvLazyListState()

    // Track focused row index to restore focus after navigation
    var focusedRowIndex by rememberSaveable { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioColors.Background)
    ) {
        when {
            uiState.isLoading && uiState.catalogRows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
            uiState.error != null && uiState.catalogRows.isEmpty() -> {
                ErrorState(
                    message = uiState.error ?: "An error occurred",
                    onRetry = { viewModel.onEvent(HomeEvent.OnRetry) }
                )
            }
            else -> {
                TvLazyColumn(
                    state = columnListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Continue Watching section at the top
                    if (uiState.continueWatchingItems.isNotEmpty()) {
                        item(key = "continue_watching") {
                            ContinueWatchingSection(
                                items = uiState.continueWatchingItems,
                                onItemClick = { progress ->
                                    onNavigateToDetail(
                                        progress.contentId,
                                        progress.contentType,
                                        ""  // No specific addon
                                    )
                                }
                            )
                        }
                    }
                    
                    itemsIndexed(
                        items = uiState.catalogRows,
                        key = { _, item -> "${item.addonId}_${item.type}_${item.catalogId}" }
                    ) { index, catalogRow ->
                        // Adjust index to account for continue watching section
                        val adjustedIndex = if (uiState.continueWatchingItems.isNotEmpty()) index + 1 else index
                        
                        CatalogRowSection(
                            catalogRow = catalogRow,
                            rowIndex = adjustedIndex,
                            isRestoreFocus = adjustedIndex == focusedRowIndex,
                            onItemClick = { id, type, addonBaseUrl ->
                                onNavigateToDetail(id, type, addonBaseUrl)
                            },
                            onRowFocused = { rowIndex ->
                                focusedRowIndex = rowIndex
                            },
                            onLoadMore = {
                                viewModel.onEvent(
                                    HomeEvent.OnLoadMoreCatalog(
                                        catalogId = catalogRow.catalogId,
                                        addonId = catalogRow.addonId,
                                        type = catalogRow.type.toApiString()
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
