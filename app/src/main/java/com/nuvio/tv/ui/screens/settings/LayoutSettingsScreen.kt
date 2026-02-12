@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
            text = "Home Layout",
            style = MaterialTheme.typography.headlineLarge,
            color = NuvioColors.TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose your preferred home screen style",
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        LayoutSettingsContent(viewModel = viewModel)
    }
}

@Composable
fun LayoutSettingsContent(
    viewModel: LayoutSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val sections = remember(uiState.availableCatalogs) {
        buildList {
            add(LayoutSettingsSection.SidebarToggle)
            add(LayoutSettingsSection.HeroSectionToggle)
            add(LayoutSettingsSection.DiscoverToggle)
            add(LayoutSettingsSection.PosterLabelsToggle)
            add(LayoutSettingsSection.FocusedPosterBackdropExpandToggle)
            add(LayoutSettingsSection.CatalogAddonNameToggle)
            add(LayoutSettingsSection.LayoutCards)
            add(LayoutSettingsSection.PosterCards)
            if (uiState.availableCatalogs.isNotEmpty()) {
                add(LayoutSettingsSection.HeroCatalog)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(sections) { section ->
            when (section) {
                LayoutSettingsSection.LayoutCards -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        LayoutCard(
                            layout = HomeLayout.CLASSIC,
                            isSelected = uiState.selectedLayout == HomeLayout.CLASSIC,
                            onClick = {
                                viewModel.onEvent(LayoutSettingsEvent.SelectLayout(HomeLayout.CLASSIC))
                            },
                            modifier = Modifier.weight(1f)
                        )

                        LayoutCard(
                            layout = HomeLayout.GRID,
                            isSelected = uiState.selectedLayout == HomeLayout.GRID,
                            onClick = {
                                viewModel.onEvent(LayoutSettingsEvent.SelectLayout(HomeLayout.GRID))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                LayoutSettingsSection.SidebarToggle -> {
                    SidebarToggle(
                        isCollapsed = uiState.sidebarCollapsedByDefault,
                        onToggle = {
                            viewModel.onEvent(LayoutSettingsEvent.SetSidebarCollapsed(!uiState.sidebarCollapsedByDefault))
                        }
                    )
                }
                LayoutSettingsSection.HeroSectionToggle -> {
                    HeroSectionToggle(
                        isEnabled = uiState.heroSectionEnabled,
                        onToggle = {
                            viewModel.onEvent(LayoutSettingsEvent.SetHeroSectionEnabled(!uiState.heroSectionEnabled))
                        }
                    )
                }
                LayoutSettingsSection.DiscoverToggle -> {
                    DiscoverToggle(
                        isEnabled = uiState.searchDiscoverEnabled,
                        onToggle = {
                            viewModel.onEvent(LayoutSettingsEvent.SetSearchDiscoverEnabled(!uiState.searchDiscoverEnabled))
                        }
                    )
                }
                LayoutSettingsSection.PosterLabelsToggle -> {
                    PosterLabelsToggle(
                        isEnabled = uiState.posterLabelsEnabled,
                        onToggle = {
                            viewModel.onEvent(LayoutSettingsEvent.SetPosterLabelsEnabled(!uiState.posterLabelsEnabled))
                        }
                    )
                }
                LayoutSettingsSection.FocusedPosterBackdropExpandToggle -> {
                    FocusedPosterBackdropExpandToggle(
                        isEnabled = uiState.focusedPosterBackdropExpandEnabled,
                        onToggle = {
                            viewModel.onEvent(
                                LayoutSettingsEvent.SetFocusedPosterBackdropExpandEnabled(
                                    !uiState.focusedPosterBackdropExpandEnabled
                                )
                            )
                        }
                    )
                }
                LayoutSettingsSection.CatalogAddonNameToggle -> {
                    CatalogAddonNameToggle(
                        isEnabled = uiState.catalogAddonNameEnabled,
                        onToggle = {
                            viewModel.onEvent(LayoutSettingsEvent.SetCatalogAddonNameEnabled(!uiState.catalogAddonNameEnabled))
                        }
                    )
                }
                LayoutSettingsSection.HeroCatalog -> {
                    Text(
                        text = "Hero Catalog",
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Choose which catalog powers the hero carousel",
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(start = 4.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.availableCatalogs) { catalog ->
                            CatalogChip(
                                catalogInfo = catalog,
                                isSelected = catalog.key == uiState.heroCatalogKey,
                                onClick = {
                                    viewModel.onEvent(LayoutSettingsEvent.SelectHeroCatalog(catalog.key))
                                }
                            )
                        }
                    }
                }
                LayoutSettingsSection.PosterCards -> {
                    PosterCardStyleSettings(
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
                        }
                    )
                }
            }
        }
    }
}

private enum class LayoutSettingsSection {
    LayoutCards,
    SidebarToggle,
    HeroSectionToggle,
    DiscoverToggle,
    PosterLabelsToggle,
    FocusedPosterBackdropExpandToggle,
    CatalogAddonNameToggle,
    PosterCards,
    HeroCatalog
}

@Composable
private fun LayoutCard(
    layout: HomeLayout,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.FocusBackground
        ),
        border = CardDefaults.border(
            border = if (isSelected) Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(16.dp)
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(16.dp)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
        scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                when (layout) {
                    HomeLayout.CLASSIC -> ClassicLayoutPreview(
                        modifier = Modifier.fillMaxSize()
                    )
                    HomeLayout.GRID -> GridLayoutPreview(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = NuvioColors.FocusRing,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Text(
                    text = layout.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected || isFocused) NuvioColors.TextPrimary else NuvioColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = when (layout) {
                    HomeLayout.CLASSIC -> "Horizontal rows per category"
                    HomeLayout.GRID -> "Vertical grid with sticky headers"
                },
                style = MaterialTheme.typography.bodySmall,
                color = NuvioColors.TextTertiary
            )
        }
    }
}

@Composable
private fun SidebarToggle(
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
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
        scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Collapse Sidebar",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) NuvioColors.TextPrimary else NuvioColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Hide sidebar by default, only show when focused",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextTertiary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Toggle indicator
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isCollapsed) NuvioColors.FocusRing else Color.White.copy(alpha = 0.15f)
                    )
                    .padding(3.dp),
                contentAlignment = if (isCollapsed) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun HeroSectionToggle(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
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
        scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show Hero Section",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) NuvioColors.TextPrimary else NuvioColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Display hero carousel at the top of the home screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextTertiary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isEnabled) NuvioColors.FocusRing else Color.White.copy(alpha = 0.15f)
                    )
                    .padding(3.dp),
                contentAlignment = if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun DiscoverToggle(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
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
        scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show Discover in Search",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) NuvioColors.TextPrimary else NuvioColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Show featured browse section when search field is empty",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextTertiary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isEnabled) NuvioColors.FocusRing else Color.White.copy(alpha = 0.15f)
                    )
                    .padding(3.dp),
                contentAlignment = if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun PosterLabelsToggle(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
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
        scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show Poster Labels",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) NuvioColors.TextPrimary else NuvioColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Show/hide poster titles in catalog rows, grid, and see-all screens",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextTertiary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isEnabled) NuvioColors.FocusRing else Color.White.copy(alpha = 0.15f)
                    )
                    .padding(3.dp),
                contentAlignment = if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun CatalogAddonNameToggle(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
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
        scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show Addon Name",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) NuvioColors.TextPrimary else NuvioColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Show/hide addon source under catalog titles (for example, Cinemeta)",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextTertiary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isEnabled) NuvioColors.FocusRing else Color.White.copy(alpha = 0.15f)
                    )
                    .padding(3.dp),
                contentAlignment = if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun FocusedPosterBackdropExpandToggle(
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
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
        scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Expand Focused Poster To Backdrop",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isFocused) NuvioColors.TextPrimary else NuvioColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "On home catalog rows, expand the focused poster to a backdrop after 3 seconds idle",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextTertiary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isEnabled) NuvioColors.FocusRing else Color.White.copy(alpha = 0.15f)
                    )
                    .padding(3.dp),
                contentAlignment = if (isEnabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun CatalogChip(
    catalogInfo: CatalogInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused },
        shape = ButtonDefaults.shape(
            shape = RoundedCornerShape(20.dp)
        ),
        colors = ButtonDefaults.colors(
            containerColor = if (isSelected) NuvioColors.FocusRing.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = NuvioColors.FocusBackground
        ),
        border = ButtonDefaults.border(
            border = if (isSelected) Border(
                border = BorderStroke(1.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(20.dp)
            ) else Border.None,
            focusedBorder = Border(
                border = BorderStroke(1.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(20.dp)
            )
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = catalogInfo.name,
                style = MaterialTheme.typography.labelLarge,
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
private fun PosterCardStyleSettings(
    widthDp: Int,
    cornerRadiusDp: Int,
    onWidthSelected: (Int) -> Unit,
    onCornerRadiusSelected: (Int) -> Unit,
    onReset: () -> Unit
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = "Poster Card Style",
            style = MaterialTheme.typography.titleMedium,
            color = NuvioColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Customize poster width and corner radius (height is auto 2:3)",
            style = MaterialTheme.typography.bodySmall,
            color = NuvioColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))
        OptionRow(
            title = "Width",
            selectedValue = widthDp,
            options = widthOptions,
            onSelected = onWidthSelected
        )
        Spacer(modifier = Modifier.height(8.dp))
        OptionRow(
            title = "Corner Radius",
            selectedValue = cornerRadiusDp,
            options = radiusOptions,
            onSelected = onCornerRadiusSelected
        )

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onReset,
            shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
            colors = ButtonDefaults.colors(
                containerColor = NuvioColors.BackgroundCard,
                focusedContainerColor = NuvioColors.FocusBackground
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
    onSelected: (Int) -> Unit
) {
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: "Custom"
    Text(
        text = "$title ($selectedLabel)",
        style = MaterialTheme.typography.labelLarge,
        color = NuvioColors.TextSecondary
    )
    Spacer(modifier = Modifier.height(6.dp))

    LazyRow(
        contentPadding = PaddingValues(end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { option ->
            ValueChip(
                label = option.label,
                isSelected = option.value == selectedValue,
                onClick = { onSelected(option.value) }
            )
        }
    }
}

@Composable
private fun ValueChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused },
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(14.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (isSelected) NuvioColors.FocusRing.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
            focusedContainerColor = NuvioColors.FocusBackground
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
