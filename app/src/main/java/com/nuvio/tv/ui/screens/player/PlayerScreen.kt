@file:OptIn(
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package com.nuvio.tv.ui.screens.player

import android.util.Log
import android.view.KeyEvent
import androidx.annotation.RawRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.nuvio.tv.R
import com.nuvio.tv.core.player.ExternalPlayerLauncher
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioColors
import java.util.concurrent.TimeUnit

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel(),
    onBackPress: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val containerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val episodesFocusRequester = remember { FocusRequester() }
    val streamsFocusRequester = remember { FocusRequester() }
    val sourceStreamsFocusRequester = remember { FocusRequester() }
    val skipIntroFocusRequester = remember { FocusRequester() }

    BackHandler {
        if (uiState.showPauseOverlay) {
            viewModel.onEvent(PlayerEvent.OnDismissPauseOverlay)
        } else if (uiState.showSubtitleStylePanel) {
            viewModel.onEvent(PlayerEvent.OnDismissSubtitleStylePanel)
        } else if (uiState.showSourcesPanel) {
            viewModel.onEvent(PlayerEvent.OnDismissSourcesPanel)
        } else if (uiState.showEpisodesPanel) {
            if (uiState.showEpisodeStreams) {
                viewModel.onEvent(PlayerEvent.OnBackFromEpisodeStreams)
            } else {
                viewModel.onEvent(PlayerEvent.OnDismissEpisodesPanel)
            }
        } else if (uiState.showControls) {
            // If controls are visible, hide them instead of going back
            viewModel.hideControls()
        } else {
            // If controls are hidden, go back
            onBackPress()
        }
    }

    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.exoPlayer?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Don't auto-resume, let user control
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Frame rate matching: switch display refresh rate to match video frame rate
    // Like Just Player, we pause playback during the switch and resume when the display settles.
    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(uiState.detectedFrameRate, uiState.frameRateMatchingEnabled) {
        if (activity != null && uiState.frameRateMatchingEnabled && uiState.detectedFrameRate > 0f) {
            val switched = com.nuvio.tv.core.player.FrameRateUtils.matchFrameRate(
                activity,
                uiState.detectedFrameRate,
                onBeforeSwitch = { viewModel.exoPlayer?.pause() },
                onAfterSwitch = { viewModel.exoPlayer?.play() }
            )
        }
    }
    // Restore original display mode when leaving the player
    DisposableEffect(activity) {
        onDispose {
            if (activity != null) {
                com.nuvio.tv.core.player.FrameRateUtils.cleanupDisplayListener()
            }
        }
    }

    // Request focus for key events when controls visibility or panel state changes
    LaunchedEffect(
        uiState.showControls,
        uiState.showEpisodesPanel,
        uiState.showSourcesPanel,
        uiState.showSubtitleStylePanel,
        uiState.showAudioDialog,
        uiState.showSubtitleDialog,
        uiState.showSpeedDialog
    ) {
        if (uiState.showControls && !uiState.showEpisodesPanel && !uiState.showSourcesPanel &&
            !uiState.showAudioDialog && !uiState.showSubtitleDialog &&
            !uiState.showSubtitleStylePanel && !uiState.showSpeedDialog
        ) {
            // Wait for AnimatedVisibility animation to complete before focusing play/pause button
            kotlinx.coroutines.delay(250)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Focus requester may not be ready yet
            }
        } else if (!uiState.showControls) {
            // When controls are hidden, let skip intro button take focus if visible
            val skipVisible = uiState.activeSkipInterval != null && !uiState.skipIntervalDismissed
            if (!skipVisible) {
                try {
                    containerFocusRequester.requestFocus()
                } catch (e: Exception) {
                    // Focus requester may not be ready yet
                }
            }
            // If skip button is visible, its own LaunchedEffect will request focus
        }
    }

    // Initial focus on container - the LaunchedEffect above will handle focusing controls
    LaunchedEffect(Unit) {
        containerFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(containerFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                // When a side panel or dialog is open, let it handle all keys
                val panelOrDialogOpen = uiState.showEpisodesPanel || uiState.showSourcesPanel ||
                        uiState.showAudioDialog || uiState.showSubtitleDialog ||
                        uiState.showSubtitleStylePanel || uiState.showSpeedDialog
                if (panelOrDialogOpen) return@onKeyEvent false

                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!uiState.showControls) {
                                viewModel.onEvent(PlayerEvent.OnCommitPreviewSeek)
                                return@onKeyEvent true
                            }
                        }
                    }
                    return@onKeyEvent false
                }

                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (uiState.showPauseOverlay) {
                        viewModel.onEvent(PlayerEvent.OnDismissPauseOverlay)
                        return@onKeyEvent true
                    }
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (!uiState.showControls) {
                                viewModel.onEvent(PlayerEvent.OnPlayPause)
                                true
                            } else {
                                // Let the focused button handle it
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!uiState.showControls) {
                                val repeatCount = keyEvent.nativeKeyEvent.repeatCount
                                val deltaMs = when {
                                    repeatCount >= 8 -> 30_000L
                                    repeatCount >= 3 -> 20_000L
                                    else -> 10_000L
                                }
                                viewModel.onEvent(PlayerEvent.OnPreviewSeekBy(deltaMs))
                                true
                            } else {
                                // Let focus system handle navigation when controls are visible
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!uiState.showControls) {
                                val repeatCount = keyEvent.nativeKeyEvent.repeatCount
                                val deltaMs = when {
                                    repeatCount >= 8 -> -30_000L
                                    repeatCount >= 3 -> -20_000L
                                    else -> -10_000L
                                }
                                viewModel.onEvent(PlayerEvent.OnPreviewSeekBy(deltaMs))
                                true
                            } else {
                                // Let focus system handle navigation when controls are visible
                                false
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!uiState.showControls) {
                                viewModel.onEvent(PlayerEvent.OnToggleControls)
                            } else {
                                val skipVisible = uiState.activeSkipInterval != null && !uiState.skipIntervalDismissed
                                if (skipVisible) {
                                    try {
                                        skipIntroFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        // Focus requester may not be ready yet
                                    }
                                } else {
                                    viewModel.hideControls()
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!uiState.showControls) {
                                viewModel.onEvent(PlayerEvent.OnToggleControls)
                                true
                            } else {
                                // Let focus system handle navigation when controls are visible
                                false
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            viewModel.onEvent(PlayerEvent.OnPlayPause)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            viewModel.onEvent(PlayerEvent.OnSeekForward)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            viewModel.onEvent(PlayerEvent.OnSeekBackward)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Video Player
        viewModel.exoPlayer?.let { player ->
            val subtitleStyle = uiState.subtitleStyle
            val resizeMode = uiState.resizeMode
            
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = false
                        keepScreenOn = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { playerView ->
                    Log.d("PlayerScreen", "Applying resizeMode: $resizeMode")
                    playerView.resizeMode = resizeMode
                    playerView.subtitleView?.apply {
                        // Calculate font size based on percentage (100% = 24sp base)
                        val baseFontSize = 24f
                        val scaledFontSize = baseFontSize * (subtitleStyle.size / 100f)
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, scaledFontSize)
                        setApplyEmbeddedFontSizes(false)
                        
                        // Apply bold style via typeface
                        val typeface = if (subtitleStyle.bold) {
                            android.graphics.Typeface.DEFAULT_BOLD
                        } else {
                            android.graphics.Typeface.DEFAULT
                        }
                        
                        // Calculate edge type based on outline setting
                        val edgeType = if (subtitleStyle.outlineEnabled) {
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                        } else {
                            androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                        }
                        
                        setStyle(
                            androidx.media3.ui.CaptionStyleCompat(
                                subtitleStyle.textColor,
                                subtitleStyle.backgroundColor,
                                android.graphics.Color.TRANSPARENT, // Window color
                                edgeType,
                                subtitleStyle.outlineColor,
                                typeface
                            )
                        )
                        
                        setApplyEmbeddedStyles(false)
                        
                        // Apply vertical offset (-20 = very bottom, 0 = default, 50 = middle)
                        // Convert percentage to fraction for bottom padding
                        val bottomPaddingFraction = (0.06f + (subtitleStyle.verticalOffset / 250f)).coerceIn(0f, 0.4f)
                        setBottomPaddingFraction(bottomPaddingFraction)

                        // Also apply explicit bottom padding based on view height for stronger offset effect
                        post {
                            val extraPadding = (height * (subtitleStyle.verticalOffset / 400f)).toInt().coerceAtLeast(0)
                            setPadding(paddingLeft, paddingTop, paddingRight, extraPadding)
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        LoadingOverlay(
            visible = uiState.showLoadingOverlay && uiState.error == null,
            backdropUrl = uiState.backdrop,
            logoUrl = uiState.logo,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
        )

        PauseOverlay(
            visible = uiState.showPauseOverlay && uiState.error == null && !uiState.showLoadingOverlay,
            onClose = { viewModel.onEvent(PlayerEvent.OnDismissPauseOverlay) },
            title = uiState.title,
            episodeTitle = uiState.currentEpisodeTitle,
            season = uiState.currentSeason,
            episode = uiState.currentEpisode,
            year = uiState.releaseYear,
            type = uiState.contentType,
            description = uiState.description,
            cast = uiState.castMembers,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2.5f)
        )

        // Buffering indicator
        if (uiState.isBuffering && !uiState.showLoadingOverlay) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }

        // Error state
        if (uiState.error != null) {
            ErrorOverlay(
                message = uiState.error!!,
                onBack = onBackPress
            )
        }

        // Skip Intro button (bottom-left, independent of controls)
        SkipIntroButton(
            interval = uiState.activeSkipInterval,
            dismissed = uiState.skipIntervalDismissed,
            controlsVisible = uiState.showControls,
            onSkip = { viewModel.onEvent(PlayerEvent.OnSkipIntro) },
            onDismiss = { viewModel.onEvent(PlayerEvent.OnDismissSkipIntro) },
            focusRequester = skipIntroFocusRequester,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = if (uiState.showControls) 120.dp else 32.dp)
        )

        // Parental guide overlay (shows when video first starts playing)
        ParentalGuideOverlay(
            warnings = uiState.parentalWarnings,
            isVisible = uiState.showParentalGuide,
            onAnimationComplete = {
                viewModel.onEvent(PlayerEvent.OnParentalGuideHide)
            },
            modifier = Modifier.align(Alignment.TopStart)
        )

        // Controls overlay
        AnimatedVisibility(
            visible = uiState.showControls && uiState.error == null &&
                !uiState.showLoadingOverlay && !uiState.showPauseOverlay &&
                !uiState.showSubtitleStylePanel &&
                !uiState.showEpisodesPanel &&
                !uiState.showSourcesPanel &&
                !uiState.showAudioDialog &&
                !uiState.showSubtitleDialog &&
                !uiState.showSpeedDialog,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            val context = LocalContext.current
            PlayerControlsOverlay(
                uiState = uiState,
                playPauseFocusRequester = playPauseFocusRequester,
                hideTopInfo = uiState.showParentalGuide,
                onPlayPause = { viewModel.onEvent(PlayerEvent.OnPlayPause) },
                onSeekForward = { viewModel.onEvent(PlayerEvent.OnSeekForward) },
                onSeekBackward = { viewModel.onEvent(PlayerEvent.OnSeekBackward) },
                onSeekTo = { viewModel.onEvent(PlayerEvent.OnSeekTo(it)) },
                onShowEpisodesPanel = { viewModel.onEvent(PlayerEvent.OnShowEpisodesPanel) },
                onShowSourcesPanel = { viewModel.onEvent(PlayerEvent.OnShowSourcesPanel) },
                onShowAudioDialog = { viewModel.onEvent(PlayerEvent.OnShowAudioDialog) },
                onShowSubtitleDialog = { viewModel.onEvent(PlayerEvent.OnShowSubtitleDialog) },
                onShowSpeedDialog = { viewModel.onEvent(PlayerEvent.OnShowSpeedDialog) },
                onToggleAspectRatio = {
                    Log.d("PlayerScreen", "onToggleAspectRatio called - dispatching event")
                    viewModel.onEvent(PlayerEvent.OnToggleAspectRatio)
                },
                onOpenInExternalPlayer = {
                    val url = viewModel.getCurrentStreamUrl()
                    val title = uiState.title
                    val headers = viewModel.getCurrentHeaders()
                    viewModel.stopAndRelease()
                    onBackPress()
                    ExternalPlayerLauncher.launch(
                        context = context,
                        url = url,
                        title = title,
                        headers = headers
                    )
                },
                onResetHideTimer = { viewModel.scheduleHideControls() },
                onBack = onBackPress
            )
        }

        // Aspect ratio indicator (floating pill)
        AnimatedVisibility(
            visible = uiState.showAspectRatioIndicator,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            AspectRatioIndicator(text = uiState.aspectRatioIndicatorText)
        }

        // Seek-only overlay (progress bar + time) when controls are hidden
        AnimatedVisibility(
            visible = uiState.showSeekOverlay && !uiState.showControls && uiState.error == null &&
                !uiState.showLoadingOverlay && !uiState.showPauseOverlay,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SeekOverlay(uiState = uiState)
        }

        // Episodes/streams side panel (slides in from right)
        AnimatedVisibility(
            visible = uiState.showEpisodesPanel && uiState.error == null,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            // Scrim (fades in/out, no slide)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

        // Panel itself (slides in from right)
        AnimatedVisibility(
            visible = uiState.showEpisodesPanel && uiState.error == null,
            enter = slideInHorizontally(
                animationSpec = tween(220),
                initialOffsetX = { it }
            ),
            exit = slideOutHorizontally(
                animationSpec = tween(220),
                targetOffsetX = { it }
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                EpisodesSidePanel(
                    uiState = uiState,
                    episodesFocusRequester = episodesFocusRequester,
                    streamsFocusRequester = streamsFocusRequester,
                    onClose = { viewModel.onEvent(PlayerEvent.OnDismissEpisodesPanel) },
                    onBackToEpisodes = { viewModel.onEvent(PlayerEvent.OnBackFromEpisodeStreams) },
                    onReloadEpisodeStreams = { viewModel.onEvent(PlayerEvent.OnReloadEpisodeStreams) },
                    onSeasonSelected = { viewModel.onEvent(PlayerEvent.OnEpisodeSeasonSelected(it)) },
                    onAddonFilterSelected = { viewModel.onEvent(PlayerEvent.OnEpisodeAddonFilterSelected(it)) },
                    onEpisodeSelected = { viewModel.onEvent(PlayerEvent.OnEpisodeSelected(it)) },
                    onStreamSelected = { viewModel.onEvent(PlayerEvent.OnEpisodeStreamSelected(it)) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        // Sources panel scrim
        AnimatedVisibility(
            visible = uiState.showSourcesPanel && uiState.error == null,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(120))
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            )
        }

        // Sources panel (slides in from right)
        AnimatedVisibility(
            visible = uiState.showSourcesPanel && uiState.error == null,
            enter = slideInHorizontally(
                animationSpec = tween(220),
                initialOffsetX = { it }
            ),
            exit = slideOutHorizontally(
                animationSpec = tween(220),
                targetOffsetX = { it }
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                StreamSourcesSidePanel(
                    uiState = uiState,
                    streamsFocusRequester = sourceStreamsFocusRequester,
                    onClose = { viewModel.onEvent(PlayerEvent.OnDismissSourcesPanel) },
                    onReload = { viewModel.onEvent(PlayerEvent.OnReloadSourceStreams) },
                    onAddonFilterSelected = { viewModel.onEvent(PlayerEvent.OnSourceAddonFilterSelected(it)) },
                    onStreamSelected = { viewModel.onEvent(PlayerEvent.OnSourceStreamSelected(it)) },
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }

        // Subtitle style panel scrim
        AnimatedVisibility(
            visible = uiState.showSubtitleStylePanel && uiState.error == null,
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }

        // Subtitle style panel
        AnimatedVisibility(
            visible = uiState.showSubtitleStylePanel && uiState.error == null,
            enter = slideInVertically(
                animationSpec = tween(220),
                initialOffsetY = { -it }
            ),
            exit = slideOutVertically(
                animationSpec = tween(220),
                targetOffsetY = { -it }
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                SubtitleStyleSidePanel(
                    subtitleStyle = uiState.subtitleStyle,
                    onEvent = { viewModel.onEvent(it) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                )
            }
        }

        // Audio track dialog
        if (uiState.showAudioDialog) {
            AudioSelectionDialog(
                tracks = uiState.audioTracks,
                selectedIndex = uiState.selectedAudioTrackIndex,
                onTrackSelected = { viewModel.onEvent(PlayerEvent.OnSelectAudioTrack(it)) },
                onDismiss = { viewModel.onEvent(PlayerEvent.OnDismissDialog) }
            )
        }

        // Subtitle track dialog
        if (uiState.showSubtitleDialog) {
            SubtitleSelectionDialog(
                internalTracks = uiState.subtitleTracks,
                selectedInternalIndex = uiState.selectedSubtitleTrackIndex,
                addonSubtitles = uiState.addonSubtitles,
                selectedAddonSubtitle = uiState.selectedAddonSubtitle,
                preferredLanguage = uiState.subtitleStyle.preferredLanguage,
                subtitleOrganizationMode = uiState.subtitleOrganizationMode,
                isLoadingAddons = uiState.isLoadingAddonSubtitles,
                onInternalTrackSelected = { viewModel.onEvent(PlayerEvent.OnSelectSubtitleTrack(it)) },
                onAddonSubtitleSelected = { viewModel.onEvent(PlayerEvent.OnSelectAddonSubtitle(it)) },
                onDisableSubtitles = { viewModel.onEvent(PlayerEvent.OnDisableSubtitles) },
                onOpenStylePanel = { viewModel.onEvent(PlayerEvent.OnOpenSubtitleStylePanel) },
                onDismiss = { viewModel.onEvent(PlayerEvent.OnDismissDialog) }
            )
        }

        // Speed dialog
        if (uiState.showSpeedDialog) {
            SpeedSelectionDialog(
                currentSpeed = uiState.playbackSpeed,
                onSpeedSelected = { viewModel.onEvent(PlayerEvent.OnSetPlaybackSpeed(it)) },
                onDismiss = { viewModel.onEvent(PlayerEvent.OnDismissDialog) }
            )
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    uiState: PlayerUiState,
    playPauseFocusRequester: FocusRequester,
    hideTopInfo: Boolean,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onShowEpisodesPanel: () -> Unit,
    onShowSourcesPanel: () -> Unit,
    onShowAudioDialog: () -> Unit,
    onShowSubtitleDialog: () -> Unit,
    onShowSpeedDialog: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onOpenInExternalPlayer: () -> Unit,
    onResetHideTimer: () -> Unit,
    onBack: () -> Unit
) {
    val customPlayPainter = rememberRawSvgPainter(R.raw.ic_player_play)
    val customPausePainter = rememberRawSvgPainter(R.raw.ic_player_pause)
    val customSubtitlePainter = rememberRawSvgPainter(R.raw.ic_player_subtitles)
    val customAudioPainter = rememberRawSvgPainter(R.raw.ic_player_audio_filled)
    val customSourcePainter = rememberRawSvgPainter(R.raw.ic_player_source)
    val customAspectPainter = rememberRawSvgPainter(R.raw.ic_player_aspect_ratio)
    val customEpisodesPainter = rememberRawSvgPainter(R.raw.ic_player_episodes)

    Box(modifier = Modifier.fillMaxSize()) {
        // Top gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        // Top bar - Title and episode info (hidden when parental guide overlay is showing)
        AnimatedVisibility(
            visible = !hideTopInfo,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                // For series content, show series name; for movies, show title
                val displayName = if (uiState.currentSeason != null && uiState.currentEpisode != null) {
                    uiState.contentName ?: uiState.title
                } else {
                    uiState.title
                }

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Show episode info for series (S1E3 • Episode Title)
                if (uiState.currentSeason != null && uiState.currentEpisode != null) {
                    val episodeInfo = buildString {
                        append("S${uiState.currentSeason}E${uiState.currentEpisode}")
                        if (!uiState.currentEpisodeTitle.isNullOrBlank()) {
                            append(" • ${uiState.currentEpisodeTitle}")
                        }
                    }
                    Text(
                        text = episodeInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Show stream source if available
                if (!uiState.currentStreamName.isNullOrBlank()) {
                    val sourceText = if (!uiState.releaseYear.isNullOrBlank()) {
                        "${uiState.releaseYear} - via ${uiState.currentStreamName}"
                    } else {
                        "via ${uiState.currentStreamName}"
                    }
                    Text(
                        text = sourceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            // Progress bar
            ProgressBar(
                currentPosition = uiState.currentPosition,
                duration = uiState.duration,
                onSeekTo = onSeekTo
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Control buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side - All controls in a flat row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/Pause
                    ControlButton(
                        icon = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        iconPainter = if (uiState.isPlaying) customPausePainter else customPlayPainter,
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        onClick = onPlayPause,
                        focusRequester = playPauseFocusRequester,
                        onFocused = onResetHideTimer
                    )

                    // Subtitles
                    if (uiState.subtitleTracks.isNotEmpty() || uiState.addonSubtitles.isNotEmpty()) {
                        ControlButton(
                            icon = Icons.Default.ClosedCaption,
                            iconPainter = customSubtitlePainter,
                            contentDescription = "Subtitles",
                            onClick = onShowSubtitleDialog,
                            onFocused = onResetHideTimer
                        )
                    }

                    // Audio tracks
                    if (uiState.audioTracks.isNotEmpty()) {
                        ControlButton(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            iconPainter = customAudioPainter,
                            contentDescription = "Audio tracks",
                            onClick = onShowAudioDialog,
                            onFocused = onResetHideTimer
                        )
                    }

                    // Speed
                    ControlButton(
                        icon = Icons.Default.Speed,
                        contentDescription = "Playback speed",
                        onClick = onShowSpeedDialog,
                        onFocused = onResetHideTimer
                    )

                    // Aspect Ratio
                    ControlButton(
                        icon = Icons.Default.AspectRatio,
                        iconPainter = customAspectPainter,
                        contentDescription = "Aspect ratio",
                        onClick = {
                            Log.d("PlayerScreen", "Aspect ratio button clicked")
                            onToggleAspectRatio()
                        },
                        onFocused = onResetHideTimer
                    )

                    // Sources - switch stream source
                    ControlButton(
                        icon = Icons.Default.SwapHoriz,
                        iconPainter = customSourcePainter,
                        contentDescription = "Sources",
                        onClick = onShowSourcesPanel,
                        onFocused = onResetHideTimer
                    )

                    // Open in external player
                    ControlButton(
                        icon = Icons.Default.OpenInNew,
                        contentDescription = "Open in external player",
                        onClick = onOpenInExternalPlayer,
                        onFocused = onResetHideTimer
                    )

                    // Episodes (only show when playing a specific episode)
                    if (uiState.currentSeason != null && uiState.currentEpisode != null) {
                        ControlButton(
                            icon = Icons.AutoMirrored.Filled.List,
                            iconPainter = customEpisodesPainter,
                            contentDescription = "Episodes",
                            onClick = onShowEpisodesPanel,
                            onFocused = onResetHideTimer
                        )
                    }
                }

                // Right side - Time display only
                Text(
                    text = "${formatTime(uiState.currentPosition)} / ${formatTime(uiState.duration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    iconPainter: Painter? = null,
    contentDescription: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            },
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        shape = IconButtonDefaults.shape(shape = CircleShape)
    ) {
        if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun ProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit
) {
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(100),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.3f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(RoundedCornerShape(3.dp))
                .background(NuvioColors.Secondary)
        )
    }
}

@Composable
private fun SeekOverlay(uiState: PlayerUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        ProgressBar(
            currentPosition = uiState.currentPosition,
            duration = uiState.duration,
            onSeekTo = {}
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime(uiState.currentPosition)} / ${formatTime(uiState.duration)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun AspectRatioIndicator(text: String) {
    val customAspectPainter = rememberRawSvgPainter(R.raw.ic_player_aspect_ratio)

    // Floating pill indicator for aspect ratio changes
    Row(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon background circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = Color(0xFF3B3B3B),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = customAspectPainter,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Text
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = Color.White
        )
    }
}

@Composable
private fun rememberRawSvgPainter(@RawRes iconRes: Int): Painter {
    val context = LocalContext.current
    val request = remember(iconRes, context) {
        ImageRequest.Builder(context)
            .data(iconRes)
            .decoderFactory(SvgDecoder.Factory())
            .build()
    }
    return rememberAsyncImagePainter(model = request)
}

@Composable
private fun ErrorOverlay(
    message: String,
    onBack: () -> Unit
) {
    val exitFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        exitFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .zIndex(3f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Playback Error",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DialogButton(
                    text = "Go Back",
                    onClick = onBack,
                    isPrimary = true,
                    modifier = Modifier
                        .focusRequester(exitFocusRequester)
                        .focusable()
                )
            }
        }
    }
}

@Composable
private fun SpeedSelectionDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(NuvioColors.BackgroundElevated)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Playback Speed",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 4.dp)
                ) {
                    items(PLAYBACK_SPEEDS) { speed ->
                        SpeedItem(
                            speed = speed,
                            isSelected = speed == currentSpeed,
                            onClick = { onSpeedSelected(speed) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedItem(
    speed: Float,
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
            containerColor = if (isSelected) NuvioColors.Secondary.copy(alpha = 0.2f) else NuvioColors.BackgroundCard,
            focusedContainerColor = NuvioColors.FocusBackground
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (speed == 1f) "Normal" else "${speed}x",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) NuvioColors.Primary else NuvioColors.TextPrimary
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = NuvioColors.Secondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
internal fun DialogButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.colors(
            containerColor = if (isPrimary) NuvioColors.Secondary else NuvioColors.BackgroundCard,
            focusedContainerColor = if (isPrimary) NuvioColors.Secondary else NuvioColors.FocusBackground
        ),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isPrimary) NuvioColors.OnPrimary else NuvioColors.TextPrimary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
