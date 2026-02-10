package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.ui.components.ErrorState
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioColors

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToCatalogSeeAll: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusState by viewModel.focusState.collectAsState()
    val gridFocusState by viewModel.gridFocusState.collectAsState()

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
            uiState.error == "No addons installed" && uiState.catalogRows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No addons installed. Add one to get started.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioColors.TextSecondary
                    )
                }
            }
            uiState.error == "No catalog addons installed" && uiState.catalogRows.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No catalog addons installed. Install a catalog addon to see content.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = NuvioColors.TextSecondary
                    )
                }
            }
            uiState.error != null && uiState.catalogRows.isEmpty() -> {
                ErrorState(
                    message = uiState.error ?: "An error occurred",
                    onRetry = { viewModel.onEvent(HomeEvent.OnRetry) }
                )
            }
            else -> {
                when (uiState.homeLayout) {
                    HomeLayout.CLASSIC -> ClassicHomeContent(
                        uiState = uiState,
                        focusState = focusState,
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToCatalogSeeAll = onNavigateToCatalogSeeAll,
                        onRemoveContinueWatching = { contentId ->
                            viewModel.onEvent(HomeEvent.OnRemoveContinueWatching(contentId))
                        },
                        onSaveFocusState = { vi, vo, ri, ii, m ->
                            viewModel.saveFocusState(vi, vo, ri, ii, m)
                        }
                    )
                    HomeLayout.GRID -> GridHomeContent(
                        uiState = uiState,
                        gridFocusState = gridFocusState,
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToCatalogSeeAll = onNavigateToCatalogSeeAll,
                        onRemoveContinueWatching = { contentId ->
                            viewModel.onEvent(HomeEvent.OnRemoveContinueWatching(contentId))
                        },
                        onSaveGridFocusState = { vi, vo ->
                            viewModel.saveGridFocusState(vi, vo)
                        }
                    )
                    HomeLayout.IMMERSIVE -> ClassicHomeContent(
                        uiState = uiState,
                        focusState = focusState,
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToCatalogSeeAll = onNavigateToCatalogSeeAll,
                        onRemoveContinueWatching = { contentId ->
                            viewModel.onEvent(HomeEvent.OnRemoveContinueWatching(contentId))
                        },
                        onSaveFocusState = { vi, vo, ri, ii, m ->
                            viewModel.saveFocusState(vi, vo, ri, ii, m)
                        }
                    )
                }
            }
        }
    }
}
