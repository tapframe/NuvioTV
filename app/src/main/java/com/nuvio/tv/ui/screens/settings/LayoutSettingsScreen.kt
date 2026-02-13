@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.ui.components.ClassicLayoutPreview
import com.nuvio.tv.ui.components.GridLayoutPreview
import com.nuvio.tv.ui.theme.NuvioColors

@Composable
fun LayoutSettingsScreen(
    viewModel: LayoutSettingsViewModel = hiltViewModel(),
    onBackPress: () -> Unit
) {
    BackHandler { onBackPress() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioColors.Background)
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Layout Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = NuvioColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Adjust home layout, content visibility, and poster behavior",
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        LayoutSettingsContent(viewModel = viewModel)
    }
}

private enum class LayoutSettingsSection {
    HOME_LAYOUT,
    HOME_CONTENT,
    FOCUSED_POSTER,
    POSTER_CARD_STYLE
}

@Composable
fun LayoutSettingsContent(
    viewModel: LayoutSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var homeLayoutExpanded by rememberSaveable { mutableStateOf(false) }
    var homeContentExpanded by rememberSaveable { mutableStateOf(false) }
    var focusedPosterExpanded by rememberSaveable { mutableStateOf(false) }
    var posterCardStyleExpanded by rememberSaveable { mutableStateOf(false) }

    val homeLayoutHeaderFocus = remember { FocusRequester() }
    val homeContentHeaderFocus = remember { FocusRequester() }
    val focusedPosterHeaderFocus = remember { FocusRequester() }
    val posterCardStyleHeaderFocus = remember { FocusRequester() }

    var focusedSection by remember { mutableStateOf<LayoutSettingsSection?>(null) }

    LaunchedEffect(homeLayoutExpanded, focusedSection) {
        if (!homeLayoutExpanded && focusedSection == LayoutSettingsSection.HOME_LAYOUT) {
            homeLayoutHeaderFocus.requestFocus()
        }
    }
    LaunchedEffect(homeContentExpanded, focusedSection) {
        if (!homeContentExpanded && focusedSection == LayoutSettingsSection.HOME_CONTENT) {
            homeContentHeaderFocus.requestFocus()
        }
    }
    LaunchedEffect(focusedPosterExpanded, focusedSection) {
        if (!focusedPosterExpanded && focusedSection == LayoutSettingsSection.FOCUSED_POSTER) {
            focusedPosterHeaderFocus.requestFocus()
        }
    }
    LaunchedEffect(posterCardStyleExpanded, focusedSection) {
        if (!posterCardStyleExpanded && focusedSection == LayoutSettingsSection.POSTER_CARD_STYLE) {
            posterCardStyleHeaderFocus.requestFocus()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            CollapsibleSectionCard(
                title = "Home Layout",
                description = "Choose structure and hero source.",
                expanded = homeLayoutExpanded,
                onToggle = { homeLayoutExpanded = !homeLayoutExpanded },
                focusRequester = homeLayoutHeaderFocus,
                onFocused = { focusedSection = LayoutSettingsSection.HOME_LAYOUT }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LayoutCard(
                        layout = HomeLayout.CLASSIC,
                        isSelected = uiState.selectedLayout == HomeLayout.CLASSIC,
                        onClick = {
                            viewModel.onEvent(LayoutSettingsEvent.SelectLayout(HomeLayout.CLASSIC))
                        },
                        onFocused = { focusedSection = LayoutSettingsSection.HOME_LAYOUT },
                        modifier = Modifier.weight(1f)
                    )
                    LayoutCard(
                        layout = HomeLayout.GRID,
                        isSelected = uiState.selectedLayout == HomeLayout.GRID,
                        onClick = {
                            viewModel.onEvent(LayoutSettingsEvent.SelectLayout(HomeLayout.GRID))
                        },
                        onFocused = { focusedSection = LayoutSettingsSection.HOME_LAYOUT },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (uiState.heroSectionEnabled && uiState.availableCatalogs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Hero Catalog",
                        style = MaterialTheme.typography.labelLarge,
                        color = NuvioColors.TextSecondary
                    )
                    LazyRow(
                        contentPadding = PaddingValues(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.availableCatalogs) { catalog ->
                            CatalogChip(
                                catalogInfo = catalog,
                                isSelected = catalog.key == uiState.heroCatalogKey,
                                onClick = {
                                    viewModel.onEvent(LayoutSettingsEvent.SelectHeroCatalog(catalog.key))
                                },
                                onFocused = { focusedSection = LayoutSettingsSection.HOME_LAYOUT }
                            )
                        }
                    }
                }
            }
        }

        item {
            CollapsibleSectionCard(
                title = "Home Content",
                description = "Control what appears on home and search.",
                expanded = homeContentExpanded,
                onToggle = { homeContentExpanded = !homeContentExpanded },
                focusRequester = homeContentHeaderFocus,
                onFocused = { focusedSection = LayoutSettingsSection.HOME_CONTENT }
            ) {
                if (!uiState.modernSidebarEnabled) {
                    CompactToggleRow(
                        title = "Collapse Sidebar",
                        subtitle = "Hide sidebar by default; show when focused.",
                        checked = uiState.sidebarCollapsedByDefault,
                        onToggle = {
                            viewModel.onEvent(
                                LayoutSettingsEvent.SetSidebarCollapsed(!uiState.sidebarCollapsedByDefault)
                            )
                        },
                        onFocused = { focusedSection = LayoutSettingsSection.HOME_CONTENT }
                    )
                }
                CompactToggleRow(
                    title = "Modern Sidebar ON/OFF",
                    subtitle = "Enable floating frosted sidebar navigation.",
                    checked = uiState.modernSidebarEnabled,
                    onToggle = {
                        viewModel.onEvent(
                            LayoutSettingsEvent.SetModernSidebarEnabled(!uiState.modernSidebarEnabled)
                        )
                    },
                    onFocused = { focusedSection = LayoutSettingsSection.HOME_CONTENT }
                )
                if (uiState.modernSidebarEnabled) {
                    CompactToggleRow(
                        title = "Modern Sidebar Blur",
                        subtitle = "Toggle blur effect for modern sidebar surfaces. Enabling may affect performance.",
                        checked = uiState.modernSidebarBlurEnabled,
                        onToggle = {
                            viewModel.onEvent(
                                LayoutSettingsEvent.SetModernSidebarBlurEnabled(!uiState.modernSidebarBlurEnabled)
                            )
                        },
                        onFocused = { focusedSection = LayoutSettingsSection.HOME_CONTENT }
                    )
                }
                CompactToggleRow(
                    title = "Show Hero Section",
                    subtitle = "Display hero carousel at top of home.",
                    checked = uiState.heroSectionEnabled,
                    onToggle = {
                        viewModel.onEvent(
                            LayoutSettingsEvent.SetHeroSectionEnabled(!uiState.heroSectionEnabled)
                        )
                    },
                    onFocused = { focusedSection = LayoutSettingsSection.HOME_CONTENT }
                )
                CompactToggleRow(
                    title = "Show Discover in Search",
                    subtitle = "Show browse section when search is empty.",
                    checked = uiState.searchDiscoverEnabled,
                    onToggle = {
                        viewModel.onEvent(
                            LayoutSettingsEvent.SetSearchDiscoverEnabled(!uiState.searchDiscoverEnabled)
                        )
                    },
                    onFocused = { focusedSection = LayoutSettingsSection.HOME_CONTENT }
                )
                CompactToggleRow(
                    title = "Show Poster Labels",
                    subtitle = "Show titles under posters in rows, grid, and see-all.",
                    checked = uiState.posterLabelsEnabled,
                    onToggle = {
                        viewModel.onEvent(
                            LayoutSettingsEvent.SetPosterLabelsEnabled(!uiState.posterLabelsEnabled)
                        )
                    },
                    onFocused = { focusedSection = LayoutSettingsSection.HOME_CONTENT }
                )
                CompactToggleRow(
                    title = "Show Addon Name",
                    subtitle = "Show source name under catalog titles.",
                    checked = uiState.catalogAddonNameEnabled,
                    onToggle = {
                        viewModel.onEvent(
                            LayoutSettingsEvent.SetCatalogAddonNameEnabled(!uiState.catalogAddonNameEnabled)
                        )
                    },
                    onFocused = { focusedSection = LayoutSettingsSection.HOME_CONTENT }
                )
            }
        }

        item {
            CollapsibleSectionCard(
                title = "Focused Poster",
                description = "Advanced behavior for focused poster cards.",
                expanded = focusedPosterExpanded,
                onToggle = { focusedPosterExpanded = !focusedPosterExpanded },
                focusRequester = focusedPosterHeaderFocus,
                onFocused = { focusedSection = LayoutSettingsSection.FOCUSED_POSTER }
            ) {
                CompactToggleRow(
                    title = "Expand Focused Poster to Backdrop",
                    subtitle = "Expand focused poster after idle delay.",
                    checked = uiState.focusedPosterBackdropExpandEnabled,
                    onToggle = {
                        viewModel.onEvent(
                            LayoutSettingsEvent.SetFocusedPosterBackdropExpandEnabled(
                                !uiState.focusedPosterBackdropExpandEnabled
                            )
                        )
                    },
                    onFocused = { focusedSection = LayoutSettingsSection.FOCUSED_POSTER }
                )

                if (uiState.focusedPosterBackdropExpandEnabled) {
                    SliderSettingsItem(
                        icon = Icons.Default.Timer,
                        title = "Backdrop Expand Delay",
                        subtitle = "How long to wait before expanding focused cards.",
                        value = uiState.focusedPosterBackdropExpandDelaySeconds,
                        valueText = "${uiState.focusedPosterBackdropExpandDelaySeconds}s",
                        minValue = 1,
                        maxValue = 10,
                        step = 1,
                        onValueChange = { seconds ->
                            viewModel.onEvent(
                                LayoutSettingsEvent.SetFocusedPosterBackdropExpandDelaySeconds(seconds)
                            )
                        },
                        onFocused = { focusedSection = LayoutSettingsSection.FOCUSED_POSTER }
                    )

                    CompactToggleRow(
                        title = "Autoplay Trailer in Expanded Card",
                        subtitle = "Play trailer inside expanded backdrop when available.",
                        checked = uiState.focusedPosterBackdropTrailerEnabled,
                        onToggle = {
                            viewModel.onEvent(
                                LayoutSettingsEvent.SetFocusedPosterBackdropTrailerEnabled(
                                    !uiState.focusedPosterBackdropTrailerEnabled
                                )
                            )
                        },
                        onFocused = { focusedSection = LayoutSettingsSection.FOCUSED_POSTER }
                    )
                }

                if (uiState.focusedPosterBackdropExpandEnabled && uiState.focusedPosterBackdropTrailerEnabled) {
                    CompactToggleRow(
                        title = "Play Trailer Muted",
                        subtitle = "Mute trailer audio in expanded cards.",
                        checked = uiState.focusedPosterBackdropTrailerMuted,
                        onToggle = {
                            viewModel.onEvent(
                                LayoutSettingsEvent.SetFocusedPosterBackdropTrailerMuted(
                                    !uiState.focusedPosterBackdropTrailerMuted
                                )
                            )
                        },
                        onFocused = { focusedSection = LayoutSettingsSection.FOCUSED_POSTER }
                    )
                }
            }
        }

        item {
            CollapsibleSectionCard(
                title = "Poster Card Style",
                description = "Tune card width and corner radius.",
                expanded = posterCardStyleExpanded,
                onToggle = { posterCardStyleExpanded = !posterCardStyleExpanded },
                focusRequester = posterCardStyleHeaderFocus,
                onFocused = { focusedSection = LayoutSettingsSection.POSTER_CARD_STYLE }
            ) {
                PosterCardStyleControls(
                    widthDp = uiState.posterCardWidthDp,
                    cornerRadiusDp = uiState.posterCardCornerRadiusDp,
                    onWidthSelected = { width ->
                        viewModel.onEvent(LayoutSettingsEvent.SetPosterCardWidth(width))
                    },
                    onCornerRadiusSelected = { radius ->
                        viewModel.onEvent(LayoutSettingsEvent.SetPosterCardCornerRadius(radius))
                    },
                    onReset = {
                        viewModel.onEvent(LayoutSettingsEvent.ResetPosterCardStyle)
                    },
                    onFocused = { focusedSection = LayoutSettingsSection.POSTER_CARD_STYLE }
                )
            }
        }
    }
}

@Composable
private fun CollapsibleSectionCard(
    title: String,
    description: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) onFocused()
                },
            colors = CardDefaults.colors(
                containerColor = NuvioColors.BackgroundCard,
                focusedContainerColor = NuvioColors.FocusBackground
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(2.dp, NuvioColors.FocusRing),
                    shape = RoundedCornerShape(12.dp)
                )
            ),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
            scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = NuvioColors.TextSecondary
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NuvioColors.BackgroundCard.copy(alpha = 0.55f))
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }

        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(1.dp)
                    .background(NuvioColors.Border.copy(alpha = 0.78f))
            )
        }
    }
}

@Composable
private fun CompactToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    onFocused: () -> Unit
) {
    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .onFocusChanged {
                if (it.isFocused) onFocused()
            },
        colors = CardDefaults.colors(
            containerColor = NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.BackgroundCard
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(10.dp)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
            TogglePill(checked = checked)
        }
    }
}

@Composable
private fun TogglePill(checked: Boolean) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (checked) NuvioColors.FocusRing else Color.White.copy(alpha = 0.18f)
            )
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun LayoutCard(
    layout: HomeLayout,
    isSelected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged {
            isFocused = it.isFocused
            if (it.isFocused) onFocused()
        },
        colors = CardDefaults.colors(
            containerColor = NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.BackgroundCard
        ),
        border = CardDefaults.border(
            border = if (isSelected) Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(12.dp)
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
            ) {
                when (layout) {
                    HomeLayout.CLASSIC -> ClassicLayoutPreview(modifier = Modifier.fillMaxWidth())
                    HomeLayout.GRID -> GridLayoutPreview(modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = NuvioColors.FocusRing,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 6.dp)
                    )
                }
                Text(
                    text = layout.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected || isFocused) NuvioColors.TextPrimary else NuvioColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun CatalogChip(
    catalogInfo: CatalogInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = Modifier.onFocusChanged {
            isFocused = it.isFocused
            if (it.isFocused) onFocused()
        },
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(16.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (isSelected) NuvioColors.FocusRing.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = if (isSelected) NuvioColors.FocusRing.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)
        ),
        border = ButtonDefaults.border(
            border = if (isSelected) Border(
                border = BorderStroke(1.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(16.dp)
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(16.dp)
            )
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = catalogInfo.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected || isFocused) NuvioColors.TextPrimary else NuvioColors.TextSecondary
            )
            Text(
                text = catalogInfo.addonName,
                style = MaterialTheme.typography.labelSmall,
                color = NuvioColors.TextTertiary
            )
        }
    }
}

@Composable
private fun PosterCardStyleControls(
    widthDp: Int,
    cornerRadiusDp: Int,
    onWidthSelected: (Int) -> Unit,
    onCornerRadiusSelected: (Int) -> Unit,
    onReset: () -> Unit,
    onFocused: () -> Unit
) {
    val widthOptions = listOf(
        PresetOption("Compact", 104),
        PresetOption("Dense", 112),
        PresetOption("Standard", 120),
        PresetOption("Balanced", 126),
        PresetOption("Comfort", 134),
        PresetOption("Large", 140)
    )
    val radiusOptions = listOf(
        PresetOption("Sharp", 0),
        PresetOption("Subtle", 4),
        PresetOption("Classic", 8),
        PresetOption("Rounded", 12),
        PresetOption("Pill", 16)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OptionRow(
            title = "Width",
            selectedValue = widthDp,
            options = widthOptions,
            onSelected = onWidthSelected,
            onFocused = onFocused
        )
        OptionRow(
            title = "Corner Radius",
            selectedValue = cornerRadiusDp,
            options = radiusOptions,
            onSelected = onCornerRadiusSelected,
            onFocused = onFocused
        )

        Button(
            onClick = onReset,
            modifier = Modifier.onFocusChanged {
                if (it.isFocused) onFocused()
            },
            shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
            colors = ButtonDefaults.colors(
                containerColor = NuvioColors.BackgroundCard,
                focusedContainerColor = NuvioColors.BackgroundCard
            ),
            border = ButtonDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(1.dp, NuvioColors.FocusRing),
                    shape = RoundedCornerShape(10.dp)
                )
            )
        ) {
            Text(
                text = "Reset to Default",
                style = MaterialTheme.typography.labelLarge,
                color = NuvioColors.TextPrimary
            )
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    selectedValue: Int,
    options: List<PresetOption>,
    onSelected: (Int) -> Unit,
    onFocused: () -> Unit
) {
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: "Custom"

    Text(
        text = "$title ($selectedLabel)",
        style = MaterialTheme.typography.labelLarge,
        color = NuvioColors.TextSecondary
    )

    LazyRow(
        contentPadding = PaddingValues(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { option ->
            ValueChip(
                label = option.label,
                isSelected = option.value == selectedValue,
                onClick = { onSelected(option.value) },
                onFocused = onFocused
            )
        }
    }
}

@Composable
private fun ValueChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = Modifier.onFocusChanged {
            isFocused = it.isFocused
            if (it.isFocused) onFocused()
        },
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (isSelected) NuvioColors.FocusRing.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = if (isSelected) NuvioColors.FocusRing.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f)
        ),
        border = ButtonDefaults.border(
            border = if (isSelected) Border(
                border = BorderStroke(1.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(14.dp)
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(14.dp)
            )
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected || isFocused) NuvioColors.TextPrimary else NuvioColors.TextSecondary
        )
    }
}

private data class PresetOption(
    val label: String,
    val value: Int
)
