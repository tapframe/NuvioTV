package com.nuvio.tv.ui.screens.immersive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.components.formatRemainingTime
import com.nuvio.tv.ui.theme.NuvioColors

private val BadgeShape = RoundedCornerShape(4.dp)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ImmersiveTile(
    item: MetaPreview,
    isFocused: Boolean,
    tileWidth: Dp,
    tileHeight: Dp,
    watchProgress: WatchProgress? = null,
    isNextUp: Boolean = false,
    tileMargin: Dp = 3.dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val widthPx = with(density) { tileWidth.roundToPx() }
    val heightPx = with(density) { tileHeight.roundToPx() }

    Box(
        modifier = modifier
            .then(
                if (isFocused) {
                    Modifier.background(NuvioColors.FocusRing)
                } else {
                    Modifier
                }
            )
            .padding(tileMargin)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.poster)
                .crossfade(true)
                .size(widthPx, heightPx)
                .build(),
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Time left / Next Up badge
        if (watchProgress != null || isNextUp) {
            val badgeText = if (watchProgress != null) {
                remember(watchProgress.position, watchProgress.duration) {
                    formatRemainingTime(watchProgress.remainingTime)
                }
            } else {
                "Next Up"
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(BadgeShape)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        // Progress bar
        if (watchProgress != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(watchProgress.progressPercentage)
                        .height(3.dp)
                        .background(NuvioColors.Primary)
                )
            }
        }
    }
}
