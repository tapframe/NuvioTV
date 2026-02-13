package com.nuvio.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import com.nuvio.tv.ui.theme.NuvioColors

@Composable
internal fun ModernSidebarBlurPanel(
    drawerItems: List<DrawerItem>,
    selectedDrawerRoute: String?,
    keepSidebarFocusDuringCollapse: Boolean,
    sidebarLabelAlpha: Float,
    sidebarIconScale: Float,
    sidebarExpandProgress: Float,
    isSidebarExpanded: Boolean,
    sidebarCollapsePending: Boolean,
    blurEnabled: Boolean,
    sidebarHazeState: HazeState,
    panelShape: RoundedCornerShape,
    drawerItemFocusRequesters: Map<String, FocusRequester>,
    onDrawerItemFocused: (Int) -> Unit,
    onDrawerItemClick: (String) -> Unit
) {
    val delayedBlurProgress =
        ((sidebarExpandProgress - 0.34f) / 0.66f).coerceIn(0f, 1f)
    val showPanelBlur = blurEnabled &&
        isSidebarExpanded &&
        !sidebarCollapsePending &&
        delayedBlurProgress > 0f
    val expandedPanelBlurModifier = if (showPanelBlur) {
        Modifier.hazeChild(
            state = sidebarHazeState,
            shape = panelShape,
            tint = Color.Unspecified,
            blurRadius = (26f * delayedBlurProgress).dp,
            noiseFactor = 0.04f * delayedBlurProgress
        )
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .graphicsLayer(
                alpha = sidebarExpandProgress,
                scaleX = 0.97f + (0.03f * sidebarExpandProgress),
                scaleY = 0.97f + (0.03f * sidebarExpandProgress),
                transformOrigin = TransformOrigin(0f, 0f)
            )
            .then(expandedPanelBlurModifier)
            .graphicsLayer {
                shape = panelShape
                clip = true
            }
            .clip(panelShape)
            .background(
                brush = if (blurEnabled) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xD64A4F59),
                            Color(0xCC3F454F),
                            Color(0xC640474F)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            NuvioColors.BackgroundElevated,
                            NuvioColors.BackgroundCard
                        )
                    )
                },
                shape = panelShape
            )
            .border(
                width = 1.dp,
                color = if (blurEnabled) {
                    Color.White.copy(alpha = 0.14f)
                } else {
                    NuvioColors.Border.copy(alpha = 0.9f)
                },
                shape = panelShape
            )
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        val headerLogoRes = if (isSidebarExpanded) R.drawable.app_logo_wordmark else R.drawable.app_logo_mark
        val headerLogoHeight = if (isSidebarExpanded) 42.dp else 34.dp
        val headerLogoContentDescription = if (isSidebarExpanded) "NuvioTV" else "Nuvio"

        Image(
            painter = painterResource(id = headerLogoRes),
            contentDescription = headerLogoContentDescription,
            modifier = Modifier
                .fillMaxWidth()
                .height(headerLogoHeight)
                .padding(top = 2.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            drawerItems.forEachIndexed { index, item ->
                SidebarNavigationItem(
                    label = item.label,
                    icon = item.icon,
                    selected = selectedDrawerRoute == item.route,
                    focusEnabled = keepSidebarFocusDuringCollapse,
                    labelAlpha = sidebarLabelAlpha,
                    iconScale = sidebarIconScale,
                    onFocusChanged = {
                        if (it) {
                            onDrawerItemFocused(index)
                        }
                    },
                    onClick = { onDrawerItemClick(item.route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(drawerItemFocusRequesters.getValue(item.route))
                )
            }
        }
    }
}

@Composable
private fun SidebarNavigationItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    focusEnabled: Boolean,
    labelAlpha: Float,
    iconScale: Float,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    val backgroundColor by animateColorAsState(
        targetValue = when {
            selected -> Color.White
            isFocused -> Color.White.copy(alpha = 0.18f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 180),
        label = "sidebarItemBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.4f) else Color.Transparent,
        animationSpec = tween(durationMillis = 180),
        label = "sidebarItemBorder"
    )

    val contentColor = if (selected) Color(0xFF10151F) else Color.White
    val iconCircleColor = if (selected) Color(0xFFE7E2EF) else Color(0xFF6A6A74)

    Row(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(width = 1.5.dp, color = borderColor, shape = shape)
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .focusable(enabled = focusEnabled)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconCircleColor)
                .padding(8.dp)
                .graphicsLayer(
                    scaleX = iconScale,
                    scaleY = iconScale
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            text = label,
            color = contentColor,
            modifier = Modifier.graphicsLayer(alpha = labelAlpha),
            style = androidx.tv.material3.MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
