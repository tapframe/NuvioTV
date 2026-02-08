package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.theme.NuvioColors
import com.nuvio.tv.ui.theme.NuvioTheme
import java.util.concurrent.TimeUnit

private val CwCardShape = RoundedCornerShape(12.dp)
private val CwClipShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
private val BadgeShape = RoundedCornerShape(4.dp)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ContinueWatchingSection(
    items: List<WatchProgress>,
    onItemClick: (WatchProgress) -> Unit,
    modifier: Modifier = Modifier,
    focusedItemIndex: Int = -1,
    onItemFocused: (itemIndex: Int) -> Unit = {}
) {
    if (items.isEmpty()) return

    val itemFocusRequester = remember { FocusRequester() }

    // Restore focus to specific item if requested
    LaunchedEffect(focusedItemIndex) {
        if (focusedItemIndex >= 0 && focusedItemIndex < items.size) {
            kotlinx.coroutines.delay(100)
            try {
                itemFocusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // Item not yet composed, ignore
            }
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleLarge,
            color = NuvioColors.TextPrimary,
            modifier = Modifier.padding(start = 48.dp, bottom = 16.dp)
        )

        TvLazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { _, progress ->
                    progress.videoId
                }
            ) { index, progress ->
                ContinueWatchingCard(
                    progress = progress,
                    onClick = { onItemClick(progress) },
                    modifier = Modifier
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onItemFocused(index)
                            }
                        }
                        .then(
                            if (index == focusedItemIndex) Modifier.focusRequester(itemFocusRequester)
                            else Modifier
                        )
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ContinueWatchingCard(
    progress: WatchProgress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 320.dp,
    imageHeight: Dp = 180.dp
) {
    var isFocused by remember { mutableStateOf(false) }

    val episodeStr = progress.episodeDisplayString
    val remainingText = remember(progress.position, progress.duration) {
        formatRemainingTime(progress.remainingTime)
    }
    val progressFraction = progress.progressPercentage

    val bgColor = NuvioColors.Background
    val overlayBrush = remember(bgColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.Transparent,
                0.5f to Color.Transparent,
                0.8f to bgColor.copy(alpha = 0.7f),
                1.0f to bgColor.copy(alpha = 0.95f)
            )
        )
    }
    val badgeBackground = remember(bgColor) { bgColor.copy(alpha = 0.8f) }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(cardWidth)
            .onFocusChanged { isFocused = it.isFocused },
        shape = CardDefaults.shape(shape = CwCardShape),
        colors = CardDefaults.colors(
            containerColor = NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.FocusBackground
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = CwCardShape
            )
        ),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Column {
            // Thumbnail with progress overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .clip(CwClipShape)
            ) {
                // Background image with size hints for efficient decoding
                FadeInAsyncImage(
                    model = progress.backdrop ?: progress.poster,
                    contentDescription = progress.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    requestedWidthDp = cardWidth,
                    requestedHeightDp = imageHeight
                )

                // Gradient overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayBrush)
                )

                // Content info at bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    // Episode info (for series)
                    if (episodeStr != null) {
                        Text(
                            text = episodeStr,
                            style = MaterialTheme.typography.labelMedium,
                            color = NuvioColors.Primary
                        )
                    }

                    Text(
                        text = progress.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = NuvioColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Episode title if available
                    progress.episodeTitle?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioTheme.extendedColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Remaining time badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(BadgeShape)
                        .background(badgeBackground)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = remainingText,
                        style = MaterialTheme.typography.labelSmall,
                        color = NuvioColors.TextPrimary
                    )
                }
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(NuvioColors.SurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .height(4.dp)
                        .background(NuvioColors.Primary)
                )
            }
        }
    }
}

internal fun formatRemainingTime(remainingMs: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m left"
        minutes > 0 -> "${minutes}m left"
        else -> "Almost done"
    }
}
