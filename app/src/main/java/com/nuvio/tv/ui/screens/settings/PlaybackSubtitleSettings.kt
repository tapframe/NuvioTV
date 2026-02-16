@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.data.local.AVAILABLE_SUBTITLE_LANGUAGES
import com.nuvio.tv.data.local.LibassRenderType
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.local.SubtitleOrganizationMode
import com.nuvio.tv.ui.theme.NuvioColors

private val subtitleColors = listOf(
    Color.White,
    Color.Yellow,
    Color.Cyan,
    Color.Green,
    Color.Magenta,
    Color(0xFFFF6B6B),
    Color(0xFFFFA500),
    Color(0xFF90EE90)
)

private val subtitleBackgroundColors = listOf(
    Color.Transparent,
    Color.Black,
    Color(0x80000000),
    Color(0xFF1A1A1A),
    Color(0xFF2D2D2D)
)

private val subtitleOutlineColors = listOf(
    Color.Black,
    Color(0xFF1A1A1A),
    Color(0xFF333333),
    Color.White
)

internal fun LazyListScope.subtitleSettingsItems(
    playerSettings: PlayerSettings,
    onShowLanguageDialog: () -> Unit,
    onShowSecondaryLanguageDialog: () -> Unit,
    onShowSubtitleOrganizationDialog: () -> Unit,
    onShowTextColorDialog: () -> Unit,
    onShowBackgroundColorDialog: () -> Unit,
    onShowOutlineColorDialog: () -> Unit,
    onSetSubtitleSize: (Int) -> Unit,
    onSetSubtitleVerticalOffset: (Int) -> Unit,
    onSetSubtitleBold: (Boolean) -> Unit,
    onSetSubtitleOutlineEnabled: (Boolean) -> Unit,
    onSetUseLibass: (Boolean) -> Unit,
    onSetLibassRenderType: (LibassRenderType) -> Unit,
    onItemFocused: () -> Unit = {},
    enabled: Boolean = true
) {
    item {
        Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
        Text(
            text = "Subtitles",
            style = MaterialTheme.typography.titleMedium,
            color = NuvioColors.TextSecondary,
            modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp)
        )
    }

    item {
        val languageName = if (playerSettings.subtitleStyle.preferredLanguage == "none") {
            "None"
        } else {
            AVAILABLE_SUBTITLE_LANGUAGES.find {
                it.code == playerSettings.subtitleStyle.preferredLanguage
            }?.name ?: "English"
        }

        NavigationSettingsItem(
            icon = Icons.Default.Language,
            title = "Preferred Language",
            subtitle = languageName,
            onClick = onShowLanguageDialog,
            onFocused = onItemFocused,
            enabled = enabled
        )
    }

    item {
        val secondaryLanguageName = playerSettings.subtitleStyle.secondaryPreferredLanguage?.let { code ->
            AVAILABLE_SUBTITLE_LANGUAGES.find { it.code == code }?.name
        } ?: "Not set"

        NavigationSettingsItem(
            icon = Icons.Default.Language,
            title = "Secondary Preferred Language",
            subtitle = secondaryLanguageName,
            onClick = onShowSecondaryLanguageDialog,
            onFocused = onItemFocused,
            enabled = enabled
        )
    }

    item {
        NavigationSettingsItem(
            icon = Icons.Default.Subtitles,
            title = "Subtitle Organization",
            subtitle = subtitleOrganizationModeLabel(playerSettings.subtitleOrganizationMode),
            onClick = onShowSubtitleOrganizationDialog,
            onFocused = onItemFocused,
            enabled = enabled
        )
    }

    item {
        SliderSettingsItem(
            icon = Icons.Default.FormatSize,
            title = "Size",
            value = playerSettings.subtitleStyle.size,
            valueText = "${playerSettings.subtitleStyle.size}%",
            minValue = 50,
            maxValue = 200,
            step = 10,
            onValueChange = onSetSubtitleSize,
            onFocused = onItemFocused,
            enabled = enabled
        )
    }

    item {
        SliderSettingsItem(
            icon = Icons.Default.VerticalAlignBottom,
            title = "Vertical Offset",
            value = playerSettings.subtitleStyle.verticalOffset,
            valueText = "${playerSettings.subtitleStyle.verticalOffset}%",
            minValue = -20,
            maxValue = 50,
            step = 1,
            onValueChange = onSetSubtitleVerticalOffset,
            onFocused = onItemFocused,
            enabled = enabled
        )
    }

    item {
        ToggleSettingsItem(
            icon = Icons.Default.FormatBold,
            title = "Bold",
            subtitle = "Use bold font weight for subtitles",
            isChecked = playerSettings.subtitleStyle.bold,
            onCheckedChange = onSetSubtitleBold,
            onFocused = onItemFocused,
            enabled = enabled
        )
    }

    item {
        ColorSettingsItem(
            icon = Icons.Default.Palette,
            title = "Text Color",
            currentColor = Color(playerSettings.subtitleStyle.textColor),
            onClick = onShowTextColorDialog,
            onFocused = onItemFocused,
            enabled = enabled
        )
    }

    item {
        ColorSettingsItem(
            icon = Icons.Default.Palette,
            title = "Background Color",
            currentColor = Color(playerSettings.subtitleStyle.backgroundColor),
            showTransparent = playerSettings.subtitleStyle.backgroundColor == Color.Transparent.toArgb(),
            onClick = onShowBackgroundColorDialog,
            onFocused = onItemFocused,
            enabled = enabled
        )
    }

    item {
        ToggleSettingsItem(
            icon = Icons.Default.ClosedCaption,
            title = "Outline",
            subtitle = "Add outline around subtitle text for better visibility",
            isChecked = playerSettings.subtitleStyle.outlineEnabled,
            onCheckedChange = onSetSubtitleOutlineEnabled,
            onFocused = onItemFocused,
            enabled = enabled
        )
    }

    if (playerSettings.subtitleStyle.outlineEnabled) {
        item {
            ColorSettingsItem(
                icon = Icons.Default.Palette,
                title = "Outline Color",
                currentColor = Color(playerSettings.subtitleStyle.outlineColor),
                onClick = onShowOutlineColorDialog,
                onFocused = onItemFocused,
                enabled = enabled
            )
        }
    }

    item {
        Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))
        Text(
            text = "Advanced Subtitle Rendering",
            style = MaterialTheme.typography.titleMedium,
            color = NuvioColors.TextSecondary,
            modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp)
        )
    }

    item {
        ToggleSettingsItem(
            icon = Icons.Default.Subtitles,
            title = "Use libass for ASS/SSA subtitles",
            subtitle = "Temporarily disabled for maintenance",
            isChecked = false,
            onCheckedChange = {},
            onFocused = onItemFocused,
            enabled = false
        )
    }

    if (false) { // Libass temporarily disabled for maintenance
        item {
            Text(
                text = "Libass Render Mode",
                style = MaterialTheme.typography.titleMedium,
                color = NuvioColors.TextSecondary,
                modifier = androidx.compose.ui.Modifier.padding(vertical = 8.dp)
            )
        }

        item {
            RenderTypeSettingsItem(
                title = "Overlay OpenGL (Recommended)",
                subtitle = "Best quality with HDR support. Renders subtitles on a separate thread.",
                isSelected = playerSettings.libassRenderType == LibassRenderType.OVERLAY_OPEN_GL,
                onClick = { onSetLibassRenderType(LibassRenderType.OVERLAY_OPEN_GL) },
                onFocused = onItemFocused
            )
        }

        item {
            RenderTypeSettingsItem(
                title = "Overlay Canvas",
                subtitle = "HDR support with canvas rendering. May block UI thread.",
                isSelected = playerSettings.libassRenderType == LibassRenderType.OVERLAY_CANVAS,
                onClick = { onSetLibassRenderType(LibassRenderType.OVERLAY_CANVAS) },
                onFocused = onItemFocused
            )
        }

        item {
            RenderTypeSettingsItem(
                title = "Effects OpenGL",
                subtitle = "Animation support using Media3 effects. Faster than Canvas.",
                isSelected = playerSettings.libassRenderType == LibassRenderType.EFFECTS_OPEN_GL,
                onClick = { onSetLibassRenderType(LibassRenderType.EFFECTS_OPEN_GL) },
                onFocused = onItemFocused
            )
        }

        item {
            RenderTypeSettingsItem(
                title = "Effects Canvas",
                subtitle = "Animation support using Media3 effects with Canvas rendering.",
                isSelected = playerSettings.libassRenderType == LibassRenderType.EFFECTS_CANVAS,
                onClick = { onSetLibassRenderType(LibassRenderType.EFFECTS_CANVAS) },
                onFocused = onItemFocused
            )
        }

        item {
            RenderTypeSettingsItem(
                title = "Standard Cues",
                subtitle = "Basic subtitle rendering without animation support. Most compatible.",
                isSelected = playerSettings.libassRenderType == LibassRenderType.CUES,
                onClick = { onSetLibassRenderType(LibassRenderType.CUES) },
                onFocused = onItemFocused
            )
        }
    }
}

@Composable
internal fun SubtitleSettingsDialogs(
    showLanguageDialog: Boolean,
    showSecondaryLanguageDialog: Boolean,
    showSubtitleOrganizationDialog: Boolean,
    showTextColorDialog: Boolean,
    showBackgroundColorDialog: Boolean,
    showOutlineColorDialog: Boolean,
    playerSettings: PlayerSettings,
    onSetPreferredLanguage: (String?) -> Unit,
    onSetSecondaryLanguage: (String?) -> Unit,
    onSetSubtitleOrganizationMode: (SubtitleOrganizationMode) -> Unit,
    onSetTextColor: (Color) -> Unit,
    onSetBackgroundColor: (Color) -> Unit,
    onSetOutlineColor: (Color) -> Unit,
    onDismissLanguageDialog: () -> Unit,
    onDismissSecondaryLanguageDialog: () -> Unit,
    onDismissSubtitleOrganizationDialog: () -> Unit,
    onDismissTextColorDialog: () -> Unit,
    onDismissBackgroundColorDialog: () -> Unit,
    onDismissOutlineColorDialog: () -> Unit
) {
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            title = "Preferred Language",
            selectedLanguage = if (playerSettings.subtitleStyle.preferredLanguage == "none") null else playerSettings.subtitleStyle.preferredLanguage,
            showNoneOption = true,
            onLanguageSelected = {
                onSetPreferredLanguage(it)
                onDismissLanguageDialog()
            },
            onDismiss = onDismissLanguageDialog
        )
    }

    if (showSecondaryLanguageDialog) {
        LanguageSelectionDialog(
            title = "Secondary Preferred Language",
            selectedLanguage = playerSettings.subtitleStyle.secondaryPreferredLanguage,
            showNoneOption = true,
            onLanguageSelected = {
                onSetSecondaryLanguage(it)
                onDismissSecondaryLanguageDialog()
            },
            onDismiss = onDismissSecondaryLanguageDialog
        )
    }

    if (showSubtitleOrganizationDialog) {
        SubtitleOrganizationModeDialog(
            selectedMode = playerSettings.subtitleOrganizationMode,
            onModeSelected = {
                onSetSubtitleOrganizationMode(it)
                onDismissSubtitleOrganizationDialog()
            },
            onDismiss = onDismissSubtitleOrganizationDialog
        )
    }

    if (showTextColorDialog) {
        ColorSelectionDialog(
            title = "Text Color",
            colors = subtitleColors,
            selectedColor = Color(playerSettings.subtitleStyle.textColor),
            onColorSelected = {
                onSetTextColor(it)
                onDismissTextColorDialog()
            },
            onDismiss = onDismissTextColorDialog
        )
    }

    if (showBackgroundColorDialog) {
        ColorSelectionDialog(
            title = "Background Color",
            colors = subtitleBackgroundColors,
            selectedColor = Color(playerSettings.subtitleStyle.backgroundColor),
            showTransparentOption = true,
            onColorSelected = {
                onSetBackgroundColor(it)
                onDismissBackgroundColorDialog()
            },
            onDismiss = onDismissBackgroundColorDialog
        )
    }

    if (showOutlineColorDialog) {
        ColorSelectionDialog(
            title = "Outline Color",
            colors = subtitleOutlineColors,
            selectedColor = Color(playerSettings.subtitleStyle.outlineColor),
            onColorSelected = {
                onSetOutlineColor(it)
                onDismissOutlineColorDialog()
            },
            onDismiss = onDismissOutlineColorDialog
        )
    }
}

private fun subtitleOrganizationModeLabel(mode: SubtitleOrganizationMode): String {
    return when (mode) {
        SubtitleOrganizationMode.NONE -> "None (default order)"
        SubtitleOrganizationMode.BY_LANGUAGE -> "By language"
        SubtitleOrganizationMode.BY_ADDON -> "By addon"
    }
}

@Composable
private fun SubtitleOrganizationModeDialog(
    selectedMode: SubtitleOrganizationMode,
    onModeSelected: (SubtitleOrganizationMode) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        Triple(SubtitleOrganizationMode.NONE, "None", "Show subtitles in default addon result order."),
        Triple(SubtitleOrganizationMode.BY_LANGUAGE, "By language", "Group subtitles by language."),
        Triple(SubtitleOrganizationMode.BY_ADDON, "By addon", "Group subtitles by addon source.")
    )

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                .background(NuvioColors.BackgroundCard)
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier
                    .width(460.dp)
                    .padding(24.dp)
            ) {
                Text(
                    text = "Subtitle Organization",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioColors.TextPrimary
                )
                Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))

                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    items(options) { (mode, title, description) ->
                        val isSelected = mode == selectedMode

                        RenderTypeSettingsItem(
                            title = title,
                            subtitle = description,
                            isSelected = isSelected,
                            onClick = { onModeSelected(mode) },
                            onFocused = {}
                        )
                    }
                }
            }
        }
    }
}
