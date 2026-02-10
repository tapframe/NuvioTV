package com.nuvio.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuvio.tv.ui.theme.NuvioColors

/**
 * Animated preview of the classic horizontal row layout.
 * Shows 3 rows with colored placeholder rectangles scrolling horizontally.
 */
@Composable
fun ClassicLayoutPreview(
    modifier: Modifier = Modifier,
    accentColor: Color = NuvioColors.Primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "classicPreview")
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "classicScroll"
    )

    val bgColor = NuvioColors.Background
    val cardColor = accentColor.copy(alpha = 0.6f)
    val cardColorDim = accentColor.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val rowCount = 3
            val rowSpacing = h * 0.04f
            val rowHeight = (h - rowSpacing * (rowCount + 1)) / rowCount
            val cardWidth = w / 5.5f
            val cardHeight = rowHeight * 0.85f
            val gap = w / 40f

            for (rowIndex in 0 until rowCount) {
                val rowY = rowSpacing + rowIndex * (rowHeight + rowSpacing)

                // Cards - middle row scrolls
                val numCards = 7
                val baseOffset = if (rowIndex == 1) {
                    -scrollOffset * cardWidth * 2
                } else {
                    0f
                }

                for (i in 0 until numCards) {
                    val cardX = gap * 2 + i * (cardWidth + gap) + baseOffset
                    if (cardX + cardWidth > -cardWidth && cardX < w + cardWidth) {
                        drawRoundRect(
                            color = if (rowIndex == 1) cardColor else cardColorDim,
                            topLeft = Offset(cardX, rowY + (rowHeight - cardHeight) / 2f),
                            size = Size(cardWidth, cardHeight),
                            cornerRadius = CornerRadius(h * 0.02f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated preview of the grid layout.
 * Shows a 5-column grid of cards scrolling upward.
 */
@Composable
fun GridLayoutPreview(
    modifier: Modifier = Modifier,
    accentColor: Color = NuvioColors.Primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gridPreview")
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gridScroll"
    )

    val bgColor = NuvioColors.Background
    val cardColor = accentColor.copy(alpha = 0.5f)
    val cardColorAlt = accentColor.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val cols = 5
            val cardGap = w * 0.025f
            val cardW = (w - cardGap * (cols + 1)) / cols
            val cardH = cardW * 1.4f
            val totalScrollY = scrollOffset * cardH * 1.5f

            for (row in 0..6) {
                for (col in 0 until cols) {
                    val cardX = cardGap + col * (cardW + cardGap)
                    val cardY = cardGap + row * (cardH + cardGap) - totalScrollY

                    if (cardY + cardH > 0f && cardY < h) {
                        val color = if (row % 3 < 2) cardColor else cardColorAlt
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(cardX, cardY),
                            size = Size(cardW, cardH),
                            cornerRadius = CornerRadius(h * 0.015f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated preview of the Infinite poster wall layout.
 * Shows a dense grid of poster tiles scrolling horizontally in lockstep.
 */
@Composable
fun ImmersiveLayoutPreview(
    modifier: Modifier = Modifier,
    accentColor: Color = NuvioColors.Primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "immersivePreview")
    val scrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "immersiveScroll"
    )

    val bgColor = Color.Black
    val cardColor = accentColor.copy(alpha = 0.5f)
    val cardColorDim = accentColor.copy(alpha = 0.25f)
    val focusBorder = accentColor

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val rows = 3
            val cardGap = w * 0.012f
            val cardH = (h - cardGap * (rows + 1)) / rows
            val cardW = cardH * 2f / 3f
            val cols = (w / (cardW + cardGap)).toInt() + 2
            val totalScrollX = scrollOffset * (cardW + cardGap) * 2f

            val centerRow = rows / 2
            val centerCol = cols / 2

            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val cardX = cardGap + col * (cardW + cardGap) - totalScrollX
                    val cardY = cardGap + row * (cardH + cardGap)

                    if (cardX + cardW > -cardW && cardX < w + cardW) {
                        val isFocused = row == centerRow && col == centerCol
                        val color = if (isFocused) cardColor else cardColorDim

                        drawRoundRect(
                            color = color,
                            topLeft = Offset(cardX, cardY),
                            size = Size(cardW, cardH),
                            cornerRadius = CornerRadius(h * 0.015f)
                        )

                        if (isFocused) {
                            drawRoundRect(
                                color = focusBorder,
                                topLeft = Offset(cardX - 1.5f, cardY - 1.5f),
                                size = Size(cardW + 3f, cardH + 3f),
                                cornerRadius = CornerRadius(h * 0.015f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
