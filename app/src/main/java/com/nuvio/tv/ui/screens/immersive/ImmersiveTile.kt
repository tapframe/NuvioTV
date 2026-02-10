package com.nuvio.tv.ui.screens.immersive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nuvio.tv.domain.model.MetaPreview

private val FocusBorderColor = Color(0xFFf3ab02)

@Composable
fun ImmersiveTile(
    item: MetaPreview,
    isFocused: Boolean,
    tileWidth: Dp,
    tileHeight: Dp,
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
                    Modifier.background(FocusBorderColor)
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
    }
}
