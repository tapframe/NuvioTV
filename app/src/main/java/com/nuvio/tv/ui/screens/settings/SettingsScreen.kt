@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.plugin.PluginScreenContent
import com.nuvio.tv.ui.screens.plugin.PluginViewModel
import com.nuvio.tv.ui.theme.NuvioColors

private enum class SettingsCategory(
    val displayName: String,
    val icon: ImageVector,
    @param:RawRes val rawIconRes: Int? = null
) {
    APPEARANCE("Appearance", Icons.Default.Palette),
    LAYOUT("Layout", Icons.Default.GridView),
    PLUGINS("Plugins", Icons.Default.Build),
    PLAYBACK("Playback", Icons.Default.Settings),
    TMDB("TMDB", Icons.Default.Tune),
    TRAKT("Trakt", Icons.Default.Tune, rawIconRes = R.raw.trakt_tv_glyph),
    ABOUT("About", Icons.Default.Info)
}

@Composable
fun SettingsScreen(
    showBuiltInHeader: Boolean = true,
    onNavigateToPlugins: () -> Unit = {},
    onNavigateToTrakt: () -> Unit = {}
) {
    var selectedCategory by remember { mutableStateOf(SettingsCategory.APPEARANCE) }
    var previousIndex by remember { mutableIntStateOf(0) }
    val pluginViewModel: PluginViewModel = hiltViewModel()
    val pluginUiState by pluginViewModel.uiState.collectAsState()

    val accentColor = NuvioColors.Secondary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioColors.Background)
    ) {
        // ── Top header area with gradient accent line ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Subtle gradient glow at the very top
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height
                        )
                    )
                }
                .padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (showBuiltInHeader) NuvioColors.TextPrimary else Color.Transparent,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )

                // Current category label on the right
                Text(
                    text = selectedCategory.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (showBuiltInHeader) NuvioColors.TextTertiary else Color.Transparent,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Horizontal category tabs ──
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(SettingsCategory.entries.toList()) { index, category ->
                CategoryTab(
                    category = category,
                    isSelected = selectedCategory == category,
                    accentColor = accentColor,
                    onClick = {
                        if (category == SettingsCategory.TRAKT) {
                            onNavigateToTrakt()
                        } else {
                            previousIndex = selectedCategory.ordinal
                            selectedCategory = category
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Thin accent divider ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NuvioColors.Border.copy(alpha = 0.6f),
                            NuvioColors.Border.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── Content area with animated transitions ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 8.dp)
                .background(
                    color = NuvioColors.BackgroundElevated.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp)
        ) {
            val currentIndex = selectedCategory.ordinal

            AnimatedContent(
                targetState = selectedCategory,
                transitionSpec = {
                    val direction = if (currentIndex >= previousIndex) 1 else -1
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> direction * (fullWidth / 5) },
                        animationSpec = tween(300)
                    ) + fadeIn(animationSpec = tween(300)))
                        .togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -direction * (fullWidth / 5) },
                                animationSpec = tween(300)
                            ) + fadeOut(animationSpec = tween(200))
                        )
                },
                label = "settings_content"
            ) { category ->
                when (category) {
                    SettingsCategory.APPEARANCE -> ThemeSettingsContent()
                    SettingsCategory.LAYOUT -> LayoutSettingsContent()
                    SettingsCategory.PLAYBACK -> PlaybackSettingsContent()
                    SettingsCategory.TMDB -> TmdbSettingsContent()
                    SettingsCategory.ABOUT -> AboutSettingsContent()
                    SettingsCategory.PLUGINS -> PluginScreenContent(
                        uiState = pluginUiState,
                        viewModel = pluginViewModel
                    )
                    SettingsCategory.TRAKT -> Unit
                }
            }
        }
    }
}

@Composable
private fun CategoryTab(
    category: SettingsCategory,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val iconTint = when {
        isSelected -> accentColor
        isFocused -> accentColor.copy(alpha = 0.8f)
        else -> NuvioColors.TextTertiary
    }

    val bottomBarWidth by animateDpAsState(
        targetValue = if (isSelected) 24.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "tab_indicator"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .onFocusChanged { isFocused = it.isFocused },
            colors = CardDefaults.colors(
                containerColor = when {
                    isSelected -> accentColor.copy(alpha = 0.12f)
                    else -> Color.Transparent
                },
                focusedContainerColor = accentColor.copy(alpha = 0.18f)
            ),
            border = CardDefaults.border(
                border = if (isSelected) Border(
                    border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(14.dp)
                ) else Border.None,
                focusedBorder = Border(
                    border = BorderStroke(2.dp, accentColor.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(14.dp)
                )
            ),
            shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
            scale = CardDefaults.scale(focusedScale = 1.05f)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (category.rawIconRes != null) {
                    Image(
                        painter = rememberRawSvgPainter(category.rawIconRes),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(iconTint)
                    )
                } else {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = iconTint
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        isSelected -> NuvioColors.TextPrimary
                        isFocused -> NuvioColors.TextPrimary
                        else -> NuvioColors.TextSecondary
                    }
                )
            }
        }

        // Animated accent underline indicator
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(bottomBarWidth)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (isSelected) accentColor else Color.Transparent
                )
        )
    }
}

@Composable
private fun rememberRawSvgPainter(@RawRes iconRes: Int) = rememberAsyncImagePainter(
    model = ImageRequest.Builder(LocalContext.current)
        .data(iconRes)
        .decoderFactory(SvgDecoder.Factory())
        .build()
)
