@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import android.view.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.SwitchDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.data.local.AVAILABLE_SUBTITLE_LANGUAGES
import com.nuvio.tv.data.local.AudioLanguageOption
import com.nuvio.tv.data.local.LibassRenderType
import com.nuvio.tv.data.local.PlayerSettings
import com.nuvio.tv.data.local.TrailerSettings
import com.nuvio.tv.ui.theme.NuvioColors
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Science

// Preset colors for subtitle customization
private val SUBTITLE_COLORS = listOf(
    Color.White,
    Color.Yellow,
    Color.Cyan,
    Color.Green,
    Color.Magenta,
    Color(0xFFFF6B6B), // Coral
    Color(0xFFFFA500), // Orange
    Color(0xFF90EE90), // Light Green
)

private val BACKGROUND_COLORS = listOf(
    Color.Transparent,
    Color.Black,
    Color(0x80000000), // Semi-transparent black
    Color(0xFF1A1A1A), // Dark gray
    Color(0xFF2D2D2D), // Gray
)

private val OUTLINE_COLORS = listOf(
    Color.Black,
    Color(0xFF1A1A1A),
    Color(0xFF333333),
    Color.White,
)

@Composable
fun PlaybackSettingsScreen(
    viewModel: PlaybackSettingsViewModel = hiltViewModel(),
    onBackPress: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioColors.Background)
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Header with back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBackPress) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = NuvioColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Playback Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = NuvioColors.TextPrimary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Configure video playback and subtitle options",
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioColors.TextSecondary,
            modifier = Modifier.padding(start = 56.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        PlaybackSettingsContent(viewModel = viewModel)
    }
}

@Composable
fun PlaybackSettingsContent(
    viewModel: PlaybackSettingsViewModel = hiltViewModel()
) {
    val playerSettings by viewModel.playerSettings.collectAsState(initial = PlayerSettings())
    val trailerSettings by viewModel.trailerSettings.collectAsState(initial = TrailerSettings())
    val coroutineScope = rememberCoroutineScope()

    // Dialog states
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSecondaryLanguageDialog by remember { mutableStateOf(false) }
    var showTextColorDialog by remember { mutableStateOf(false) }
    var showBackgroundColorDialog by remember { mutableStateOf(false) }
    var showOutlineColorDialog by remember { mutableStateOf(false) }
    var showAudioLanguageDialog by remember { mutableStateOf(false) }
    var showDecoderPriorityDialog by remember { mutableStateOf(false) }
    var showAdvancedExperimental by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Playback",
            style = MaterialTheme.typography.headlineMedium,
            color = NuvioColors.Secondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Configure video playback and subtitle options",
            style = MaterialTheme.typography.bodyMedium,
            color = NuvioColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Settings list
        TvLazyColumn(
            contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ToggleSettingsItem(
                    icon = Icons.Default.Image,
                    title = "Loading Overlay",
                    subtitle = "Show a loading screen until the first video frame appears",
                    isChecked = playerSettings.loadingOverlayEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            viewModel.setLoadingOverlayEnabled(enabled)
                        }
                    }
                )
            }

            item {
                ToggleSettingsItem(
                    icon = Icons.Default.PauseCircle,
                    title = "Pause Overlay",
                    subtitle = "Show a details overlay after 5 seconds of no input while paused",
                    isChecked = playerSettings.pauseOverlayEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            viewModel.setPauseOverlayEnabled(enabled)
                        }
                    }
                )
            }

            item {
                ToggleSettingsItem(
                    icon = Icons.Default.Speed,
                    title = "Auto Frame Rate",
                    subtitle = "Switch display refresh rate to match video frame rate for judder-free playback",
                    isChecked = playerSettings.frameRateMatching,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            viewModel.setFrameRateMatching(enabled)
                        }
                    }
                )
            }

            // Trailer Section Header
            item {
                Text(
                    text = "Trailer",
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                ToggleSettingsItem(
                    icon = Icons.Default.PlayCircle,
                    title = "Auto-play Trailers",
                    subtitle = "Automatically play trailers on the detail screen after a period of inactivity",
                    isChecked = trailerSettings.enabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            viewModel.setTrailerEnabled(enabled)
                        }
                    }
                )
            }

            if (trailerSettings.enabled) {
                item {
                    SliderSettingsItem(
                        icon = Icons.Default.Timer,
                        title = "Trailer Delay",
                        value = trailerSettings.delaySeconds,
                        valueText = "${trailerSettings.delaySeconds}s",
                        minValue = 3,
                        maxValue = 15,
                        step = 1,
                        onValueChange = { newDelay ->
                            coroutineScope.launch {
                                viewModel.setTrailerDelaySeconds(newDelay)
                            }
                        }
                    )
                }
            }

            // Audio Section Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Audio",
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Text(
                    text = "Audio passthrough (TrueHD, DTS, AC-3, etc.) is automatic. When connected to a compatible AV receiver or soundbar via HDMI, lossless audio is sent as-is without decoding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Preferred Audio Language
            item {
                val audioLangName = when (playerSettings.preferredAudioLanguage) {
                    AudioLanguageOption.DEFAULT -> "Default (media file)"
                    AudioLanguageOption.DEVICE -> "Device language"
                    else -> AVAILABLE_SUBTITLE_LANGUAGES.find {
                        it.code == playerSettings.preferredAudioLanguage
                    }?.name ?: playerSettings.preferredAudioLanguage
                }

                NavigationSettingsItem(
                    icon = Icons.Default.Language,
                    title = "Preferred Audio Language",
                    subtitle = audioLangName,
                    onClick = { showAudioLanguageDialog = true }
                )
            }

            // Skip Silence
            item {
                ToggleSettingsItem(
                    icon = Icons.Default.Speed,
                    title = "Skip Silence",
                    subtitle = "Skip silent portions of audio during playback",
                    isChecked = playerSettings.skipSilence,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            viewModel.setSkipSilence(enabled)
                        }
                    }
                )
            }

            // Advanced Audio Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Advanced Audio",
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Text(
                    text = "These settings may cause issues on some devices. Change only if you know what you're doing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Decoder Priority
            item {
                val decoderName = when (playerSettings.decoderPriority) {
                    0 -> "Device decoders only"
                    1 -> "Prefer device decoders"
                    2 -> "Prefer app decoders (FFmpeg)"
                    else -> "Prefer device decoders"
                }

                NavigationSettingsItem(
                    icon = Icons.Default.Tune,
                    title = "Decoder Priority",
                    subtitle = decoderName,
                    onClick = { showDecoderPriorityDialog = true }
                )
            }

            // Tunneled Playback
            item {
                ToggleSettingsItem(
                    icon = Icons.Default.VolumeUp,
                    title = "Tunneled Playback",
                    subtitle = "Hardware-level audio/video sync. May improve playback on some Android TV devices",
                    isChecked = playerSettings.tunnelingEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            viewModel.setTunnelingEnabled(enabled)
                        }
                    }
                )
            }

            // Subtitle Style Settings Section Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Subtitles",
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            // Preferred Language
            item {
                val languageName = AVAILABLE_SUBTITLE_LANGUAGES.find { 
                    it.code == playerSettings.subtitleStyle.preferredLanguage 
                }?.name ?: "English"
                
                NavigationSettingsItem(
                    icon = Icons.Default.Language,
                    title = "Preferred Language",
                    subtitle = languageName,
                    onClick = { showLanguageDialog = true }
                )
            }
            
            // Secondary Preferred Language
            item {
                val secondaryLanguageName = playerSettings.subtitleStyle.secondaryPreferredLanguage?.let { code ->
                    AVAILABLE_SUBTITLE_LANGUAGES.find { it.code == code }?.name
                } ?: "Not set"
                
                NavigationSettingsItem(
                    icon = Icons.Default.Language,
                    title = "Secondary Preferred Language",
                    subtitle = secondaryLanguageName,
                    onClick = { showSecondaryLanguageDialog = true }
                )
            }
            
            // Size Slider
            item {
                SliderSettingsItem(
                    icon = Icons.Default.FormatSize,
                    title = "Size",
                    value = playerSettings.subtitleStyle.size,
                    valueText = "${playerSettings.subtitleStyle.size}%",
                    minValue = 50,
                    maxValue = 200,
                    step = 10,
                    onValueChange = { newSize ->
                        coroutineScope.launch {
                            viewModel.setSubtitleSize(newSize)
                        }
                    }
                )
            }
            
            // Vertical Offset Slider
            item {
                SliderSettingsItem(
                    icon = Icons.Default.VerticalAlignBottom,
                    title = "Vertical Offset",
                    value = playerSettings.subtitleStyle.verticalOffset,
                    valueText = "${playerSettings.subtitleStyle.verticalOffset}%",
                    minValue = 0,
                    maxValue = 50,
                    step = 1,
                    onValueChange = { newOffset ->
                        coroutineScope.launch {
                            viewModel.setSubtitleVerticalOffset(newOffset)
                        }
                    }
                )
            }
            
            // Bold Toggle
            item {
                ToggleSettingsItem(
                    icon = Icons.Default.FormatBold,
                    title = "Bold",
                    subtitle = "Use bold font weight for subtitles",
                    isChecked = playerSettings.subtitleStyle.bold,
                    onCheckedChange = { bold ->
                        coroutineScope.launch {
                            viewModel.setSubtitleBold(bold)
                        }
                    }
                )
            }
            
            // Text Color
            item {
                ColorSettingsItem(
                    icon = Icons.Default.Palette,
                    title = "Text Color",
                    currentColor = Color(playerSettings.subtitleStyle.textColor),
                    onClick = { showTextColorDialog = true }
                )
            }
            
            // Background Color
            item {
                ColorSettingsItem(
                    icon = Icons.Default.Palette,
                    title = "Background Color",
                    currentColor = Color(playerSettings.subtitleStyle.backgroundColor),
                    showTransparent = playerSettings.subtitleStyle.backgroundColor == Color.Transparent.toArgb(),
                    onClick = { showBackgroundColorDialog = true }
                )
            }
            
            // Outline Toggle
            item {
                ToggleSettingsItem(
                    icon = Icons.Default.ClosedCaption,
                    title = "Outline",
                    subtitle = "Add outline around subtitle text for better visibility",
                    isChecked = playerSettings.subtitleStyle.outlineEnabled,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            viewModel.setSubtitleOutlineEnabled(enabled)
                        }
                    }
                )
            }
            
            // Outline Color (only show when outline is enabled)
            if (playerSettings.subtitleStyle.outlineEnabled) {
                item {
                    ColorSettingsItem(
                        icon = Icons.Default.Palette,
                        title = "Outline Color",
                        currentColor = Color(playerSettings.subtitleStyle.outlineColor),
                        onClick = { showOutlineColorDialog = true }
                    )
                }
                
            }
            
            // Advanced Subtitle Settings Section Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Advanced Subtitle Rendering",
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // Libass Toggle
            item {
                ToggleSettingsItem(
                    icon = Icons.Default.Subtitles,
                    title = "Use libass for ASS/SSA subtitles",
                    subtitle = "Enable native libass rendering for advanced ASS/SSA subtitle features including animations, positioning, and styling",
                    isChecked = playerSettings.useLibass,
                    onCheckedChange = { enabled ->
                        coroutineScope.launch {
                            viewModel.setUseLibass(enabled)
                        }
                    }
                )
            }

            // Libass Render Type Selection (only visible when libass is enabled)
            if (playerSettings.useLibass) {
                item {
                    Text(
                        text = "Libass Render Mode",
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 0.dp)
                    )
                }

                item {
                    RenderTypeSettingsItem(
                        title = "Overlay OpenGL (Recommended)",
                        subtitle = "Best quality with HDR support. Renders subtitles on a separate thread.",
                        isSelected = playerSettings.libassRenderType == LibassRenderType.OVERLAY_OPEN_GL,
                        onClick = {
                            coroutineScope.launch {
                                viewModel.setLibassRenderType(LibassRenderType.OVERLAY_OPEN_GL)
                            }
                        }
                    )
                }

                item {
                    RenderTypeSettingsItem(
                        title = "Overlay Canvas",
                        subtitle = "HDR support with canvas rendering. May block UI thread.",
                        isSelected = playerSettings.libassRenderType == LibassRenderType.OVERLAY_CANVAS,
                        onClick = {
                            coroutineScope.launch {
                                viewModel.setLibassRenderType(LibassRenderType.OVERLAY_CANVAS)
                            }
                        }
                    )
                }

                item {
                    RenderTypeSettingsItem(
                        title = "Effects OpenGL",
                        subtitle = "Animation support using Media3 effects. Faster than Canvas.",
                        isSelected = playerSettings.libassRenderType == LibassRenderType.EFFECTS_OPEN_GL,
                        onClick = {
                            coroutineScope.launch {
                                viewModel.setLibassRenderType(LibassRenderType.EFFECTS_OPEN_GL)
                            }
                        }
                    )
                }

                item {
                    RenderTypeSettingsItem(
                        title = "Effects Canvas",
                        subtitle = "Animation support using Media3 effects with Canvas rendering.",
                        isSelected = playerSettings.libassRenderType == LibassRenderType.EFFECTS_CANVAS,
                        onClick = {
                            coroutineScope.launch {
                                viewModel.setLibassRenderType(LibassRenderType.EFFECTS_CANVAS)
                            }
                        }
                    )
                }

                item {
                    RenderTypeSettingsItem(
                        title = "Standard Cues",
                        subtitle = "Basic subtitle rendering without animation support. Most compatible.",
                        isSelected = playerSettings.libassRenderType == LibassRenderType.CUES,
                        onClick = {
                            coroutineScope.launch {
                                viewModel.setLibassRenderType(LibassRenderType.CUES)
                            }
                        }
                    )
                }
            }

            // Advanced / Experimental Section Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    onClick = { showAdvancedExperimental = !showAdvancedExperimental },
                    colors = CardDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = NuvioColors.FocusBackground
                    ),
                    border = CardDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, NuvioColors.FocusRing),
                            shape = RoundedCornerShape(12.dp)
                        )
                    ),
                    scale = CardDefaults.scale(focusedScale = 1.02f),
                    shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Advanced / Experimental",
                            style = MaterialTheme.typography.titleMedium,
                            color = NuvioColors.TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showAdvancedExperimental) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (showAdvancedExperimental) "Collapse" else "Expand",
                            tint = NuvioColors.TextSecondary
                        )
                    }
                }
            }

            if (showAdvancedExperimental) {
                item {
                    Text(
                        text = "These settings affect buffering behavior. Incorrect values may cause playback issues.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Buffer Settings Section
                item {
                    Text(
                        text = "Buffer Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    SliderSettingsItem(
                        icon = Icons.Default.Speed,
                        title = "Min Buffer Duration",
                        subtitle = "Minimum amount of media to buffer. The player will try to ensure at least this much content is always buffered ahead of the current playback position.",
                        value = playerSettings.bufferSettings.minBufferMs / 1000,
                        valueText = "${playerSettings.bufferSettings.minBufferMs / 1000}s",
                        minValue = 5,
                        maxValue = 120,
                        step = 5,
                        onValueChange = { newValue ->
                            coroutineScope.launch {
                                viewModel.setBufferMinBufferMs(newValue * 1000)
                            }
                        }
                    )
                }

                item {
                    val minBufferSeconds = playerSettings.bufferSettings.minBufferMs / 1000
                    SliderSettingsItem(
                        icon = Icons.Default.Speed,
                        title = "Max Buffer Duration",
                        subtitle = "Maximum amount of media to buffer. Higher values use more memory but provide smoother playback on unstable connections.",
                        value = playerSettings.bufferSettings.maxBufferMs / 1000,
                        valueText = "${playerSettings.bufferSettings.maxBufferMs / 1000}s",
                        minValue = minBufferSeconds,
                        maxValue = 120,
                        step = 5,
                        onValueChange = { newValue ->
                            coroutineScope.launch {
                                viewModel.setBufferMaxBufferMs(newValue * 1000)
                            }
                        }
                    )
                }

                item {
                    SliderSettingsItem(
                        icon = Icons.Default.PlayArrow,
                        title = "Buffer for Playback",
                        subtitle = "How much content must be buffered before playback starts. Lower values start faster but may cause initial stuttering on slow connections.",
                        value = playerSettings.bufferSettings.bufferForPlaybackMs / 1000,
                        valueText = "${playerSettings.bufferSettings.bufferForPlaybackMs / 1000}s",
                        minValue = 1,
                        maxValue = 30,
                        step = 1,
                        onValueChange = { newValue ->
                            coroutineScope.launch {
                                viewModel.setBufferForPlaybackMs(newValue * 1000)
                            }
                        }
                    )
                }

                item {
                    SliderSettingsItem(
                        icon = Icons.Default.Refresh,
                        title = "Buffer After Rebuffer",
                        subtitle = "How much content to buffer after playback stalls due to buffering. Higher values reduce repeated buffering interruptions.",
                        value = playerSettings.bufferSettings.bufferForPlaybackAfterRebufferMs / 1000,
                        valueText = "${playerSettings.bufferSettings.bufferForPlaybackAfterRebufferMs / 1000}s",
                        minValue = 1,
                        maxValue = 60,
                        step = 1,
                        onValueChange = { newValue ->
                            coroutineScope.launch {
                                viewModel.setBufferForPlaybackAfterRebufferMs(newValue * 1000)
                            }
                        }
                    )
                }

                item {
                    val bufferSizeMb = playerSettings.bufferSettings.targetBufferSizeMb
                    val maxBufferSize = viewModel.maxBufferSizeMb
                    SliderSettingsItem(
                        icon = Icons.Default.Storage,
                        title = "Target Buffer Size",
                        subtitle = "Maximum memory for buffering. 'Auto' calculates optimal size based available RAM (recommended).",
                        value = bufferSizeMb.coerceAtMost(maxBufferSize),
                        valueText = if (bufferSizeMb == 0) "Auto (recommended)" else "$bufferSizeMb MB",
                        minValue = 0,
                        maxValue = maxBufferSize,
                        step = 10,
                        onValueChange = { newValue ->
                            coroutineScope.launch {
                                viewModel.setBufferTargetSizeMb(newValue)
                            }
                        }
                    )
                }

                item {
                    SliderSettingsItem(
                        icon = Icons.Default.History,
                        title = "Back Buffer Duration",
                        subtitle = "How much already-played content to keep in memory. Enables fast backward seeking without re-downloading. Set to 0 to disable and save memory (recommended).",
                        value = playerSettings.bufferSettings.backBufferDurationMs / 1000,
                        valueText = "${playerSettings.bufferSettings.backBufferDurationMs / 1000}s",
                        minValue = 0,
                        maxValue = 120,
                        step = 5,
                        onValueChange = { newValue ->
                            coroutineScope.launch {
                                viewModel.setBufferBackBufferDurationMs(newValue * 1000)
                            }
                        }
                    )
                }

                // Parallel Connections Toggle
                item {
                    ToggleSettingsItem(
                        icon = Icons.Default.Wifi,
                        title = "Parallel Connections",
                        subtitle = "Use multiple TCP connections in parallel for a chance of faster streaming speed. Activate if you experience buffering even though speed tests on your device show you shouldn't.",
                        isChecked = playerSettings.bufferSettings.useParallelConnections,
                        onCheckedChange = { enabled ->
                            coroutineScope.launch {
                                viewModel.setUseParallelConnections(enabled)
                            }
                        }
                    )
                }
            }
        }
    }
    
    // Language Selection Dialog
    if (showLanguageDialog) {
        LanguageSelectionDialog(
            title = "Preferred Language",
            selectedLanguage = playerSettings.subtitleStyle.preferredLanguage,
            showNoneOption = false,
            onLanguageSelected = { language ->
                language?.let {
                    coroutineScope.launch {
                        viewModel.setSubtitlePreferredLanguage(it)
                    }
                }
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
    
    // Secondary Language Selection Dialog
    if (showSecondaryLanguageDialog) {
        LanguageSelectionDialog(
            title = "Secondary Preferred Language",
            selectedLanguage = playerSettings.subtitleStyle.secondaryPreferredLanguage,
            showNoneOption = true,
            onLanguageSelected = { language ->
                coroutineScope.launch {
                    viewModel.setSubtitleSecondaryLanguage(language)
                }
                showSecondaryLanguageDialog = false
            },
            onDismiss = { showSecondaryLanguageDialog = false }
        )
    }
    
    // Text Color Selection Dialog
    if (showTextColorDialog) {
        ColorSelectionDialog(
            title = "Text Color",
            colors = SUBTITLE_COLORS,
            selectedColor = Color(playerSettings.subtitleStyle.textColor),
            onColorSelected = { color ->
                coroutineScope.launch {
                    viewModel.setSubtitleTextColor(color.toArgb())
                }
                showTextColorDialog = false
            },
            onDismiss = { showTextColorDialog = false }
        )
    }
    
    // Background Color Selection Dialog
    if (showBackgroundColorDialog) {
        ColorSelectionDialog(
            title = "Background Color",
            colors = BACKGROUND_COLORS,
            selectedColor = Color(playerSettings.subtitleStyle.backgroundColor),
            showTransparentOption = true,
            onColorSelected = { color ->
                coroutineScope.launch {
                    viewModel.setSubtitleBackgroundColor(color.toArgb())
                }
                showBackgroundColorDialog = false
            },
            onDismiss = { showBackgroundColorDialog = false }
        )
    }
    
    // Outline Color Selection Dialog
    if (showOutlineColorDialog) {
        ColorSelectionDialog(
            title = "Outline Color",
            colors = OUTLINE_COLORS,
            selectedColor = Color(playerSettings.subtitleStyle.outlineColor),
            onColorSelected = { color ->
                coroutineScope.launch {
                    viewModel.setSubtitleOutlineColor(color.toArgb())
                }
                showOutlineColorDialog = false
            },
            onDismiss = { showOutlineColorDialog = false }
        )
    }

    // Audio Language Selection Dialog
    if (showAudioLanguageDialog) {
        AudioLanguageSelectionDialog(
            selectedLanguage = playerSettings.preferredAudioLanguage,
            onLanguageSelected = { language ->
                coroutineScope.launch {
                    viewModel.setPreferredAudioLanguage(language)
                }
                showAudioLanguageDialog = false
            },
            onDismiss = { showAudioLanguageDialog = false }
        )
    }

    // Decoder Priority Selection Dialog
    if (showDecoderPriorityDialog) {
        DecoderPriorityDialog(
            selectedPriority = playerSettings.decoderPriority,
            onPrioritySelected = { priority ->
                coroutineScope.launch {
                    viewModel.setDecoderPriority(priority)
                }
                showDecoderPriorityDialog = false
            },
            onDismiss = { showDecoderPriorityDialog = false }
        )
    }
}

@Composable
private fun ToggleSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = { onCheckedChange(!isChecked) },
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
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) NuvioColors.Primary else NuvioColors.TextSecondary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = isChecked,
                onCheckedChange = null, // Handled by Card onClick
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NuvioColors.Secondary,
                    checkedTrackColor = NuvioColors.Secondary.copy(alpha = 0.35f),
                    uncheckedThumbColor = NuvioColors.TextSecondary,
                    uncheckedTrackColor = NuvioColors.BackgroundCard
                )
            )
        }
    }
}

@Composable
private fun RenderTypeSettingsItem(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isSelected) NuvioColors.Primary.copy(alpha = 0.15f) else NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.FocusBackground
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(12.dp)
            ),
            border = if (isSelected) Border(
                border = BorderStroke(2.dp, NuvioColors.Primary),
                shape = RoundedCornerShape(12.dp)
            ) else Border.None
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isSelected) NuvioColors.Primary else NuvioColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextSecondary
                )
            }
            
            if (isSelected) {
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = NuvioColors.Primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun NavigationSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
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
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) NuvioColors.Primary else NuvioColors.TextSecondary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextSecondary
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = NuvioColors.TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun SliderSettingsItem(
    icon: ImageVector,
    title: String,
    value: Int,
    valueText: String,
    minValue: Int,
    maxValue: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
    subtitle: String? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val newValue = (value - step).coerceAtLeast(minValue)
                        if (newValue != value) onValueChange(newValue)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val newValue = (value + step).coerceAtMost(maxValue)
                        if (newValue != value) onValueChange(newValue)
                        true
                    }
                    else -> false
                }
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
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) NuvioColors.Primary else NuvioColors.TextSecondary,
                    modifier = Modifier.size(28.dp)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioColors.TextPrimary
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioColors.TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium,
                    color = NuvioColors.Primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom slider controls for TV - use Row with focusable buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Decrease button
                var decreaseFocused by remember { mutableStateOf(false) }
                Card(
                    onClick = { 
                        val newValue = (value - step).coerceAtLeast(minValue)
                        onValueChange(newValue)
                    },
                    modifier = Modifier
                        .onFocusChanged { decreaseFocused = it.isFocused },
                    colors = CardDefaults.colors(
                        containerColor = NuvioColors.BackgroundElevated,
                        focusedContainerColor = NuvioColors.Primary
                    ),
                    border = CardDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, NuvioColors.FocusRing),
                            shape = CircleShape
                        )
                    ),
                    shape = CardDefaults.shape(shape = CircleShape),
                    scale = CardDefaults.scale(focusedScale = 1.15f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease",
                            tint = if (decreaseFocused) NuvioColors.OnPrimary else NuvioColors.TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Progress bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(NuvioColors.BackgroundElevated)
                ) {
                    val progress = ((value - minValue).toFloat() / (maxValue - minValue).toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(NuvioColors.Primary)
                    )
                }
                
                // Increase button
                var increaseFocused by remember { mutableStateOf(false) }
                Card(
                    onClick = { 
                        val newValue = (value + step).coerceAtMost(maxValue)
                        onValueChange(newValue)
                    },
                    modifier = Modifier
                        .onFocusChanged { increaseFocused = it.isFocused },
                    colors = CardDefaults.colors(
                        containerColor = NuvioColors.BackgroundElevated,
                        focusedContainerColor = NuvioColors.Primary
                    ),
                    border = CardDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, NuvioColors.FocusRing),
                            shape = CircleShape
                        )
                    ),
                    shape = CardDefaults.shape(shape = CircleShape),
                    scale = CardDefaults.scale(focusedScale = 1.15f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase",
                            tint = if (increaseFocused) NuvioColors.OnPrimary else NuvioColors.TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSettingsItem(
    icon: ImageVector,
    title: String,
    currentColor: Color,
    showTransparent: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
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
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) NuvioColors.Primary else NuvioColors.TextSecondary,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NuvioColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )

            // Color preview
            if (showTransparent || currentColor.alpha == 0f) {
                // Transparent indicator (checkered pattern simulation)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                        .border(2.dp, NuvioColors.Border, CircleShape)
                ) {
                    // Diagonal line to indicate transparency
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(Color.White, Color.Gray, Color.White)
                                )
                            )
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .border(2.dp, NuvioColors.Border, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun LanguageSelectionDialog(
    title: String,
    selectedLanguage: String?,
    showNoneOption: Boolean,
    onLanguageSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            onClick = { },
            colors = CardDefaults.colors(
                containerColor = NuvioColors.BackgroundCard
            ),
            shape = CardDefaults.shape(shape = RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .padding(24.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioColors.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TvLazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showNoneOption) {
                        item {
                            LanguageOptionItem(
                                name = "None",
                                code = null,
                                isSelected = selectedLanguage == null,
                                onClick = { onLanguageSelected(null) },
                                modifier = Modifier.focusRequester(focusRequester)
                            )
                        }
                    }
                    
                    items(AVAILABLE_SUBTITLE_LANGUAGES.size) { index ->
                        val language = AVAILABLE_SUBTITLE_LANGUAGES[index]
                        LanguageOptionItem(
                            name = language.name,
                            code = language.code,
                            isSelected = selectedLanguage == language.code,
                            onClick = { onLanguageSelected(language.code) },
                            modifier = if (!showNoneOption && index == 0) {
                                Modifier.focusRequester(focusRequester)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionItem(
    name: String,
    code: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isSelected) NuvioColors.Primary.copy(alpha = 0.2f) else NuvioColors.BackgroundElevated,
            focusedContainerColor = NuvioColors.FocusBackground
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioColors.FocusRing),
                shape = RoundedCornerShape(8.dp)
            )
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        scale = CardDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) NuvioColors.Primary else NuvioColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            
            if (code != null) {
                Text(
                    text = code.uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextSecondary
                )
            }
            
            if (isSelected) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = NuvioColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ColorSelectionDialog(
    title: String,
    colors: List<Color>,
    selectedColor: Color,
    showTransparentOption: Boolean = false,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(NuvioColors.BackgroundCard, RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NuvioColors.TextPrimary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Color grid using TvLazyRow for proper TV focus
            TvLazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.focusRequester(focusRequester)
            ) {
                items(colors.size) { index ->
                    val color = colors[index]
                    ColorOption(
                        color = color,
                        isSelected = color.toArgb() == selectedColor.toArgb(),
                        isTransparent = color.alpha == 0f,
                        onClick = { onColorSelected(color) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Cancel button
            Card(
                onClick = onDismiss,
                colors = CardDefaults.colors(
                    containerColor = NuvioColors.BackgroundElevated,
                    focusedContainerColor = NuvioColors.Primary
                ),
                border = CardDefaults.border(
                    focusedBorder = Border(
                        border = BorderStroke(2.dp, NuvioColors.FocusRing),
                        shape = RoundedCornerShape(8.dp)
                    )
                ),
                shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.bodyLarge,
                    color = NuvioColors.TextPrimary,
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun ColorOption(
    color: Color,
    isSelected: Boolean,
    isTransparent: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = Color.Transparent
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, NuvioColors.FocusRing),
                shape = CircleShape
            ),
            border = if (isSelected) Border(
                border = BorderStroke(3.dp, NuvioColors.Primary),
                shape = CircleShape
            ) else Border.None
        ),
        shape = CardDefaults.shape(shape = CircleShape),
        scale = CardDefaults.scale(focusedScale = 1.15f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isTransparent) {
                // Checkered pattern for transparent
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                        .border(1.dp, NuvioColors.Border, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, NuvioColors.Border, CircleShape)
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = if (color == Color.White || color == Color.Yellow) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AudioLanguageSelectionDialog(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val specialOptions = listOf(
        AudioLanguageOption.DEFAULT to "Default (media file)",
        AudioLanguageOption.DEVICE to "Device language"
    )
    val allOptions = specialOptions + AVAILABLE_SUBTITLE_LANGUAGES.map { it.code to it.name }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            onClick = { },
            colors = CardDefaults.colors(
                containerColor = NuvioColors.BackgroundCard
            ),
            shape = CardDefaults.shape(shape = RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .padding(24.dp)
            ) {
                Text(
                    text = "Preferred Audio Language",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                TvLazyColumn(
                    modifier = Modifier.height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allOptions.size) { index ->
                        val (code, name) = allOptions[index]
                        val isSelected = code == selectedLanguage
                        var isFocused by remember { mutableStateOf(false) }

                        Card(
                            onClick = { onLanguageSelected(code) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                                .onFocusChanged { isFocused = it.isFocused },
                            colors = CardDefaults.colors(
                                containerColor = if (isSelected) NuvioColors.Primary.copy(alpha = 0.2f) else NuvioColors.BackgroundElevated,
                                focusedContainerColor = NuvioColors.FocusBackground
                            ),
                            border = CardDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, NuvioColors.FocusRing),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            ),
                            shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            scale = CardDefaults.scale(focusedScale = 1.02f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) NuvioColors.Primary else NuvioColors.TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = NuvioColors.Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecoderPriorityDialog(
    selectedPriority: Int,
    onPrioritySelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val options = listOf(
        Triple(0, "Device decoders only", "Only use built-in hardware decoders. Most compatible but may not support all formats."),
        Triple(1, "Prefer device decoders", "Use hardware decoders when available, fall back to FFmpeg. Recommended for most devices."),
        Triple(2, "Prefer app decoders (FFmpeg)", "Use FFmpeg decoders when available. Better format support but higher CPU usage.")
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            onClick = { },
            colors = CardDefaults.colors(
                containerColor = NuvioColors.BackgroundCard
            ),
            shape = CardDefaults.shape(shape = RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .padding(24.dp)
            ) {
                Text(
                    text = "Decoder Priority",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Controls whether hardware or software (FFmpeg) decoders are used for audio and video",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                TvLazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(options.size) { index ->
                        val (priority, title, description) = options[index]
                        val isSelected = priority == selectedPriority
                        var isFocused by remember { mutableStateOf(false) }

                        Card(
                            onClick = { onPrioritySelected(priority) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                                .onFocusChanged { isFocused = it.isFocused },
                            colors = CardDefaults.colors(
                                containerColor = if (isSelected) NuvioColors.Primary.copy(alpha = 0.2f) else NuvioColors.BackgroundElevated,
                                focusedContainerColor = NuvioColors.FocusBackground
                            ),
                            border = CardDefaults.border(
                                focusedBorder = Border(
                                    border = BorderStroke(2.dp, NuvioColors.FocusRing),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            ),
                            shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            scale = CardDefaults.scale(focusedScale = 1.02f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        color = if (isSelected) NuvioColors.Primary else NuvioColors.TextPrimary,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = description,
                                        color = NuvioColors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = NuvioColors.Primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
