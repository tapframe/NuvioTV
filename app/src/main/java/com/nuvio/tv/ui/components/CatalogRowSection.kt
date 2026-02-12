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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CatalogRowSection(
    catalogRow: CatalogRow,
    onItemClick: (String, String, String) -> Unit,
    onSeeAll: () -> Unit = {},
    posterCardStyle: PosterCardStyle = PosterCardDefaults.Style,
    showPosterLabels: Boolean = true,
    showAddonName: Boolean = true,
    focusedPosterBackdropExpandEnabled: Boolean = false,
    modifier: Modifier = Modifier,
    initialScrollIndex: Int = 0,
    focusedItemIndex: Int = -1,
    onItemFocused: (itemIndex: Int) -> Unit = {},
    upFocusRequester: FocusRequester? = null,
    listState: LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
) {
    val seeAllCardShape = RoundedCornerShape(posterCardStyle.cornerRadius)

    val currentOnItemFocused by rememberUpdatedState(onItemFocused)

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
                if (showAddonName) {
                    Text(
                        text = "from ${catalogRow.addonName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = NuvioColors.TextTertiary
                    )
                }
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = catalogRow.items,
                key = { _, item -> "${catalogRow.type}_${catalogRow.catalogId}_${item.id}" },
                contentType = { _, _ -> "content_card" }
            ) { index, item ->
                ContentCard(
                    item = item,
                    posterCardStyle = posterCardStyle,
                    showLabels = showPosterLabels,
                    focusedPosterBackdropExpandEnabled = focusedPosterBackdropExpandEnabled,
                    onClick = { onItemClick(item.id, item.type.toApiString(), catalogRow.addonBaseUrl) },
                    modifier = Modifier
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                currentOnItemFocused(index)
                            }
                        }
                        .then(
                            if (upFocusRequester != null) {
                                Modifier.focusProperties { up = upFocusRequester }
                            } else {
                                Modifier
                            }
                        ),
                    focusRequester = if (index == focusedItemIndex) itemFocusRequester else null
                )
            }

            // "See All" card
            if (catalogRow.items.size >= 15) {
                item(key = "${catalogRow.type}_${catalogRow.catalogId}_see_all") {
                    Card(
                        onClick = onSeeAll,
                        modifier = Modifier
                            .width(posterCardStyle.width)
                            .height(posterCardStyle.height)
                            .then(
                                if (upFocusRequester != null) {
                                    Modifier.focusProperties { up = upFocusRequester }
                                } else {
                                    Modifier
                                }
                            ),
                        shape = CardDefaults.shape(shape = seeAllCardShape),
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
                        scale = CardDefaults.scale(focusedScale = posterCardStyle.focusedScale)
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
