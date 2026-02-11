package com.nuvio.tv.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.material3.Border
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import com.nuvio.tv.ui.components.GridContentCard
import com.nuvio.tv.ui.components.GridContinueWatchingSection
import com.nuvio.tv.ui.components.HeroCarousel
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.theme.NuvioColors
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GridHomeContent(
    uiState: HomeUiState,
    gridFocusState: HomeScreenFocusState,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToCatalogSeeAll: (String, String, String) -> Unit,
    onRemoveContinueWatching: (String) -> Unit,
    posterCardStyle: PosterCardStyle = PosterCardDefaults.Style,
    onSaveGridFocusState: (Int, Int) -> Unit
) {
    val gridState = rememberTvLazyGridState(
        initialFirstVisibleItemIndex = gridFocusState.verticalScrollIndex,
        initialFirstVisibleItemScrollOffset = gridFocusState.verticalScrollOffset
    )

    // Save scroll state when leaving
    DisposableEffect(Unit) {
        onDispose {
            onSaveGridFocusState(
                gridState.firstVisibleItemIndex,
                gridState.firstVisibleItemScrollOffset
            )
        }
    }

    // Offset for section indices when continue watching is present
    val continueWatchingOffset = if (uiState.continueWatchingItems.isNotEmpty()) 1 else 0

    // Build index-to-section mapping for sticky header
    val sectionMapping = remember(uiState.gridItems, continueWatchingOffset) {
        buildSectionMapping(uiState.gridItems, continueWatchingOffset)
    }

    // Track current section for sticky header
    var currentSectionName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(gridState, sectionMapping) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisibleIndex ->
                val section = sectionMapping.findSectionForIndex(firstVisibleIndex)
                currentSectionName = section?.catalogName
            }
    }

    // Pre-compute whether hero exists to avoid repeated list scan in derivedStateOf
    val hasHero = remember(uiState.gridItems) {
        uiState.gridItems.firstOrNull() is GridItem.Hero
    }
    val topPadding = if (hasHero) 0.dp else 24.dp

    // Determine if hero is scrolled past
    val isScrolledPastHero by remember(hasHero) {
        derivedStateOf {
            if (hasHero) {
                gridState.firstVisibleItemIndex > 0
            } else {
                true
            }
        }
    }
    val shouldRequestInitialFocus = remember(gridFocusState.verticalScrollIndex, gridFocusState.verticalScrollOffset) {
        gridFocusState.verticalScrollIndex == 0 && gridFocusState.verticalScrollOffset == 0
    }
    val heroFocusRequester = remember { FocusRequester() }
    val firstGridItemFocusRequester = remember { FocusRequester() }
    val hasContinueWatching = uiState.continueWatchingItems.isNotEmpty()
    val hasStandaloneFocusableGridItem = remember(uiState.gridItems) {
        uiState.gridItems.any { it is GridItem.Content || it is GridItem.SeeAll }
    }

    LaunchedEffect(
        shouldRequestInitialFocus,
        hasHero,
        hasContinueWatching,
        hasStandaloneFocusableGridItem,
        uiState.gridItems.size
    ) {
        if (!shouldRequestInitialFocus) return@LaunchedEffect
        if (hasContinueWatching && !hasHero) return@LaunchedEffect
        val targetRequester = when {
            hasHero -> heroFocusRequester
            hasStandaloneFocusableGridItem -> firstGridItemFocusRequester
            else -> null
        } ?: return@LaunchedEffect

        repeat(2) { withFrameNanos { } }
        try {
            targetRequester.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TvLazyVerticalGrid(
            state = gridState,
            columns = TvGridCells.Adaptive(minSize = posterCardStyle.width),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = topPadding,
                bottom = 32.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            var continueWatchingInserted = false
            var firstGridFocusableAssigned = false

            uiState.gridItems.forEachIndexed { index, gridItem ->
                when (gridItem) {
                    is GridItem.Hero -> {
                        item(
                            key = "hero",
                            span = { TvGridItemSpan(maxLineSpan) },
                            contentType = "hero"
                        ) {
                            HeroCarousel(
                                items = gridItem.items,
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

                    is GridItem.SectionDivider -> {
                        // Insert continue watching before the first section divider
                        if (!continueWatchingInserted && uiState.continueWatchingItems.isNotEmpty()) {
                            continueWatchingInserted = true
                            item(
                                key = "continue_watching",
                                span = { TvGridItemSpan(maxLineSpan) },
                                contentType = "continue_watching"
                            ) {
                                GridContinueWatchingSection(
                                    items = uiState.continueWatchingItems,
                                    focusedItemIndex = if (shouldRequestInitialFocus && !hasHero) 0 else -1,
                                    onItemClick = { item ->
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
                                    }
                                )
                            }
                        }

                        item(
                            key = "divider_${index}_${gridItem.catalogId}_${gridItem.addonId}_${gridItem.type}",
                            span = { TvGridItemSpan(maxLineSpan) },
                            contentType = "divider"
                        ) {
                            SectionDivider(
                                catalogName = gridItem.catalogName
                            )
                        }
                    }

                    is GridItem.Content -> {
                        val focusRequester = if (
                            shouldRequestInitialFocus &&
                            !hasHero &&
                            !hasContinueWatching &&
                            !firstGridFocusableAssigned
                        ) {
                            firstGridFocusableAssigned = true
                            firstGridItemFocusRequester
                        } else {
                            null
                        }
                        item(
                            key = "content_${index}_${gridItem.catalogId}_${gridItem.item.id}",
                            span = { TvGridItemSpan(1) },
                            contentType = "content"
                        ) {
                            GridContentCard(
                                item = gridItem.item,
                                focusRequester = focusRequester,
                                posterCardStyle = posterCardStyle,
                                onClick = {
                                    onNavigateToDetail(
                                        gridItem.item.id,
                                        gridItem.item.type.toApiString(),
                                        gridItem.addonBaseUrl
                                    )
                                }
                            )
                        }
                    }

                    is GridItem.SeeAll -> {
                        val focusRequester = if (
                            shouldRequestInitialFocus &&
                            !hasHero &&
                            !hasContinueWatching &&
                            !firstGridFocusableAssigned
                        ) {
                            firstGridFocusableAssigned = true
                            firstGridItemFocusRequester
                        } else {
                            null
                        }
                        item(
                            key = "see_all_${gridItem.catalogId}_${gridItem.addonId}_${gridItem.type}",
                            span = { TvGridItemSpan(1) },
                            contentType = "see_all"
                        ) {
                            SeeAllGridCard(
                                posterCardStyle = posterCardStyle,
                                focusRequester = focusRequester,
                                onClick = {
                                    onNavigateToCatalogSeeAll(
                                        gridItem.catalogId,
                                        gridItem.addonId,
                                        gridItem.type
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Sticky header overlay
        AnimatedVisibility(
            visible = isScrolledPastHero && currentSectionName != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            StickyCategoryHeader(
                sectionName = currentSectionName ?: ""
            )
        }
    }
}

@Composable
private fun SectionDivider(
    catalogName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 12.dp, start = 24.dp, end = 24.dp)
    ) {
        Text(
            text = catalogName,
            style = MaterialTheme.typography.headlineMedium,
            color = NuvioColors.TextPrimary
        )
    }
}

@Composable
private fun StickyCategoryHeader(
    sectionName: String,
    modifier: Modifier = Modifier
) {
    val bgColor = NuvioColors.Background
    val headerGradient = remember(bgColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to bgColor,
                0.7f to bgColor.copy(alpha = 0.95f),
                1.0f to Color.Transparent
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(headerGradient)
            .padding(horizontal = 48.dp, vertical = 12.dp)
    ) {
        Crossfade(
            targetState = sectionName,
            animationSpec = tween(150),
            label = "sectionNameCrossfade"
        ) { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = NuvioColors.TextPrimary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeeAllGridCard(
    onClick: () -> Unit,
    posterCardStyle: PosterCardStyle,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val seeAllCardShape = RoundedCornerShape(posterCardStyle.cornerRadius)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(posterCardStyle.aspectRatio)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        shape = CardDefaults.shape(
            shape = seeAllCardShape
        ),
        colors = CardDefaults.colors(
            containerColor = NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.BackgroundCard
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(posterCardStyle.focusedBorderWidth, NuvioColors.FocusRing),
                shape = seeAllCardShape
            )
        ),
        scale = CardDefaults.scale(
            focusedScale = posterCardStyle.focusedScale
        )
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

// Section mapping utilities

private data class SectionInfo(
    val catalogName: String,
    val catalogId: String,
    val addonId: String,
    val type: String
)

private class SectionMapping(
    private val indexToSection: Map<Int, SectionInfo>
) {
    private val sortedSectionStarts = indexToSection.keys.sorted()

    fun findSectionForIndex(index: Int): SectionInfo? {
        if (sortedSectionStarts.isEmpty()) return null
        val insertionPoint = sortedSectionStarts.binarySearch(index)
        val targetIdx = if (insertionPoint >= 0) insertionPoint else (-insertionPoint - 2)
        if (targetIdx < 0) return null
        return indexToSection[sortedSectionStarts[targetIdx]]
    }
}

private fun buildSectionMapping(gridItems: List<GridItem>, indexOffset: Int = 0): SectionMapping {
    val mapping = mutableMapOf<Int, SectionInfo>()
    gridItems.forEachIndexed { index, item ->
        if (item is GridItem.SectionDivider) {
            mapping[index + indexOffset] = SectionInfo(
                catalogName = item.catalogName,
                catalogId = item.catalogId,
                addonId = item.addonId,
                type = item.type
            )
        }
    }
    return SectionMapping(mapping)
}
