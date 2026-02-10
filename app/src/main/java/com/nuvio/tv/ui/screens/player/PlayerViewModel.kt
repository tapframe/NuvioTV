package com.nuvio.tv.ui.screens.player

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.view.accessibility.CaptioningManager
import androidx.media3.session.MediaSession
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.common.MimeTypes
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.player.FrameRateUtils
import com.nuvio.tv.data.local.AudioLanguageOption
import com.nuvio.tv.data.local.LibassRenderType
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.SubtitleStyleSettings
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.data.repository.ParentalGuideRepository
import com.nuvio.tv.data.repository.SkipIntroRepository
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.StreamRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.peerless2012.ass.media.kt.buildWithAssSupport
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val watchProgressRepository: WatchProgressRepository,
    private val metaRepository: MetaRepository,
    private val streamRepository: StreamRepository,
    private val subtitleRepository: com.nuvio.tv.domain.repository.SubtitleRepository,
    private val parentalGuideRepository: ParentalGuideRepository,
    private val skipIntroRepository: SkipIntroRepository,
    private val playerSettingsDataStore: PlayerSettingsDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val initialStreamUrl: String = savedStateHandle.get<String>("streamUrl")?.let {
        URLDecoder.decode(it, "UTF-8")
    } ?: ""
    private val title: String = savedStateHandle.get<String>("title")?.let {
        URLDecoder.decode(it, "UTF-8")
    } ?: ""
    private val streamName: String? = savedStateHandle.get<String>("streamName")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }
    private val year: String? = savedStateHandle.get<String>("year")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }
    private val headersJson: String? = savedStateHandle.get<String>("headers")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }

    
    private val contentId: String? = savedStateHandle.get<String>("contentId")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }
    private val contentType: String? = savedStateHandle.get<String>("contentType")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }
    private val contentName: String? = savedStateHandle.get<String>("contentName")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }
    private val poster: String? = savedStateHandle.get<String>("poster")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }
    private val backdrop: String? = savedStateHandle.get<String>("backdrop")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }
    private val logo: String? = savedStateHandle.get<String>("logo")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }
    private val videoId: String? = savedStateHandle.get<String>("videoId")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }
    private val initialSeason: Int? = savedStateHandle.get<String>("season")?.toIntOrNull()
    private val initialEpisode: Int? = savedStateHandle.get<String>("episode")?.toIntOrNull()
    private val initialEpisodeTitle: String? = savedStateHandle.get<String>("episodeTitle")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }

    private var currentStreamUrl: String = initialStreamUrl
    private var currentHeaders: Map<String, String> = parseHeaders(headersJson)
    private var currentVideoId: String? = videoId
    private var currentSeason: Int? = initialSeason
    private var currentEpisode: Int? = initialEpisode
    private var currentEpisodeTitle: String? = initialEpisodeTitle

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            title = title,
            contentName = contentName,
            currentStreamName = streamName,
            releaseYear = year,
            contentType = contentType,
            backdrop = backdrop,
            logo = logo,
            showLoadingOverlay = true,
            currentSeason = currentSeason,
            currentEpisode = currentEpisode,
            currentEpisodeTitle = currentEpisodeTitle
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer?
        get() = _exoPlayer

    private var progressJob: Job? = null
    private var hideControlsJob: Job? = null
    private var hideSeekOverlayJob: Job? = null
    private var watchProgressSaveJob: Job? = null
    private var hideAspectRatioIndicatorJob: Job? = null
    
    
    private var lastSavedPosition: Long = 0L
    private val saveThresholdMs = 5000L 
    private var lastKnownDuration: Long = 0L

    
    private var playbackStartedForParentalGuide = false
    private var hasRenderedFirstFrame = false
    private var metaVideos: List<Video> = emptyList()
    private var userPausedManually = false

    
    private var skipIntervals: List<SkipInterval> = emptyList()
    private var skipIntroEnabled: Boolean = true
    private var skipIntroFetchedKey: String? = null
    private var lastActiveSkipType: String? = null
    private var autoSubtitleSelected: Boolean = false
    private var pendingAddonSubtitleLanguage: String? = null

    
    private var okHttpClient: OkHttpClient? = null
    private var currentUseParallelConnections: Boolean = true
    private var lastBufferLogTimeMs: Long = 0L
    
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var currentMediaSession: MediaSession? = null
    private var pauseOverlayJob: Job? = null
    private val pauseOverlayDelayMs = 5000L

    init {
        initializePlayer(currentStreamUrl, currentHeaders)
        loadSavedProgressFor(currentSeason, currentEpisode)
        fetchParentalGuide(contentId, contentType, currentSeason, currentEpisode)
        observeSubtitleSettings()
        fetchAddonSubtitles()
        fetchMetaDetails(contentId, contentType)
    }
    
    private fun fetchAddonSubtitles() {
        val id = contentId ?: return
        val type = contentType ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAddonSubtitles = true, addonSubtitlesError = null) }
            
            try {
                
                val videoId = if (type == "series" && currentSeason != null && currentEpisode != null) {
                    "${id.split(":").firstOrNull() ?: id}:$currentSeason:$currentEpisode"
                } else {
                    null
                }
                
                val subtitles = subtitleRepository.getSubtitles(
                    type = type,
                    id = id.split(":").firstOrNull() ?: id, 
                    videoId = videoId
                )
                
                _uiState.update { 
                    it.copy(
                        addonSubtitles = subtitles,
                        isLoadingAddonSubtitles = false
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoadingAddonSubtitles = false,
                        addonSubtitlesError = e.message
                    ) 
                }
            }
        }
    }
    
    private fun observeSubtitleSettings() {
        viewModelScope.launch {
            playerSettingsDataStore.playerSettings.collect { settings ->
                _uiState.update { state ->
                    val shouldShowOverlay = if (settings.loadingOverlayEnabled && !hasRenderedFirstFrame) {
                        true
                    } else if (!settings.loadingOverlayEnabled) {
                        false
                    } else {
                        state.showLoadingOverlay
                    }

                    state.copy(
                        subtitleStyle = settings.subtitleStyle,
                        loadingOverlayEnabled = settings.loadingOverlayEnabled,
                        showLoadingOverlay = shouldShowOverlay,
                        pauseOverlayEnabled = settings.pauseOverlayEnabled
                    )
                }

                if (!settings.pauseOverlayEnabled) {
                    cancelPauseOverlay()
                } else if (!_uiState.value.isPlaying &&
                    !_uiState.value.showPauseOverlay && pauseOverlayJob == null &&
                    userPausedManually && hasRenderedFirstFrame
                ) {
                    schedulePauseOverlay()
                }

                applySubtitlePreferences(
                    settings.subtitleStyle.preferredLanguage,
                    settings.subtitleStyle.secondaryPreferredLanguage
                )

                val wasEnabled = skipIntroEnabled
                skipIntroEnabled = settings.skipIntroEnabled
                if (!skipIntroEnabled) {
                    if (skipIntervals.isNotEmpty() || _uiState.value.activeSkipInterval != null) {
                        skipIntervals = emptyList()
                        skipIntroFetchedKey = null
                        _uiState.update { it.copy(activeSkipInterval = null, skipIntervalDismissed = true) }
                    }
                } else {
                    if (!wasEnabled || skipIntroFetchedKey == null) {
                        _uiState.update { it.copy(skipIntervalDismissed = false) }
                        fetchSkipIntervals(contentId, currentSeason, currentEpisode)
                    }
                }
            }
        }
    }

    private fun loadSavedProgressFor(season: Int?, episode: Int?) {
        if (contentId == null) return
        
        viewModelScope.launch {
            val progress = if (season != null && episode != null) {
                watchProgressRepository.getEpisodeProgress(contentId, season, episode).firstOrNull()
            } else {
                watchProgressRepository.getProgress(contentId).firstOrNull()
            }
            
            progress?.let { saved ->
                
                if (saved.isInProgress()) {
                    _exoPlayer?.let { player ->
                        
                        if (player.playbackState == Player.STATE_READY) {
                            player.seekTo(saved.position)
                        } else {
                            
                            _uiState.update { it.copy(pendingSeekPosition = saved.position) }
                        }
                    }
                }
            }
        }
    }

    private fun fetchSkipIntervals(id: String?, season: Int?, episode: Int?) {
        if (!skipIntroEnabled) return
        if (id.isNullOrBlank()) return
        val imdbId = id.split(":").firstOrNull()?.takeIf { it.startsWith("tt") } ?: return
        if (season == null || episode == null) return

        val key = "$imdbId:$season:$episode"
        if (skipIntroFetchedKey == key) return
        skipIntroFetchedKey = key

        viewModelScope.launch {
            val intervals = skipIntroRepository.getSkipIntervals(imdbId, season, episode)
            skipIntervals = intervals
        }
    }

    private fun fetchMetaDetails(id: String?, type: String?) {
        if (id.isNullOrBlank() || type.isNullOrBlank()) return

        viewModelScope.launch {
            when (
                val result = metaRepository.getMetaFromAllAddons(type = type, id = id)
                    .first { it !is NetworkResult.Loading }
            ) {
                is NetworkResult.Success -> {
                    applyMetaDetails(result.data)
                }
                is NetworkResult.Error -> {
                    
                }
                NetworkResult.Loading -> {
                    
                }
            }
        }
    }

    private fun applyMetaDetails(meta: Meta) {
        metaVideos = meta.videos
        val description = resolveDescription(meta)

        _uiState.update { state ->
            state.copy(
                description = description ?: state.description,
                castMembers = if (meta.castMembers.isNotEmpty()) meta.castMembers else state.castMembers
            )
        }
    }

    private fun resolveDescription(meta: Meta): String? {
        val type = contentType
        if (type in listOf("series", "tv") && currentSeason != null && currentEpisode != null) {
            val episodeOverview = meta.videos.firstOrNull { video ->
                video.season == currentSeason && video.episode == currentEpisode
            }?.overview
            if (!episodeOverview.isNullOrBlank()) return episodeOverview
        }

        return meta.description
    }

    private fun updateEpisodeDescription() {
        val overview = metaVideos.firstOrNull { video ->
            video.season == currentSeason && video.episode == currentEpisode
        }?.overview

        if (!overview.isNullOrBlank()) {
            _uiState.update { it.copy(description = overview) }
        }
    }

    private fun updateActiveSkipInterval(positionMs: Long) {
        if (skipIntervals.isEmpty()) {
            if (_uiState.value.activeSkipInterval != null) {
                _uiState.update { it.copy(activeSkipInterval = null) }
            }
            return
        }

        val positionSec = positionMs / 1000.0
        val active = skipIntervals.find { interval ->
            positionSec >= interval.startTime && positionSec < (interval.endTime - 0.5)
        }

        val currentActive = _uiState.value.activeSkipInterval

        if (active != null) {
            
            if (currentActive == null || active.type != currentActive.type || active.startTime != currentActive.startTime) {
                lastActiveSkipType = active.type
                _uiState.update { it.copy(activeSkipInterval = active, skipIntervalDismissed = false) }
            }
        } else if (currentActive != null) {
            
            _uiState.update { it.copy(activeSkipInterval = null, skipIntervalDismissed = false) }
        }
    }

    private fun tryShowParentalGuide() {
        val state = _uiState.value
        if (!state.parentalGuideHasShown && state.parentalWarnings.isNotEmpty() && !playbackStartedForParentalGuide) {
            playbackStartedForParentalGuide = true
            _uiState.update { it.copy(showParentalGuide = true, parentalGuideHasShown = true) }
        }
    }

    private fun fetchParentalGuide(id: String?, type: String?, season: Int?, episode: Int?) {
        if (id.isNullOrBlank()) return
        
        val imdbId = id.split(":").firstOrNull()?.takeIf { it.startsWith("tt") } ?: return

        viewModelScope.launch {
            val response = if (type in listOf("series", "tv") && season != null && episode != null) {
                parentalGuideRepository.getTVGuide(imdbId, season, episode)
            } else {
                parentalGuideRepository.getMovieGuide(imdbId)
            }

            if (response?.parentalGuide != null) {
                val guide = response.parentalGuide
                val labels = mapOf(
                    "nudity" to "Nudity",
                    "violence" to "Violence",
                    "profanity" to "Profanity",
                    "alcohol" to "Alcohol/Drugs",
                    "frightening" to "Frightening"
                )
                val severityOrder = mapOf(
                    "severe" to 0, "moderate" to 1, "mild" to 2
                )

                val entries = listOfNotNull(
                    guide.nudity?.let { "nudity" to it },
                    guide.violence?.let { "violence" to it },
                    guide.profanity?.let { "profanity" to it },
                    guide.alcohol?.let { "alcohol" to it },
                    guide.frightening?.let { "frightening" to it }
                )

                val warnings = entries
                    .filter { it.second.lowercase() != "none" }
                    .map { ParentalWarning(label = labels[it.first] ?: it.first, severity = it.second) }
                    .sortedBy { severityOrder[it.severity.lowercase()] ?: 3 }
                    .take(5)

                _uiState.update {
                    it.copy(
                        parentalWarnings = warnings,
                        showParentalGuide = false,
                        parentalGuideHasShown = false
                    )
                }

                
                if (_uiState.value.isPlaying) {
                    tryShowParentalGuide()
                }
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(UnstableApi::class)
    private fun initializePlayer(url: String, headers: Map<String, String>) {
        if (url.isEmpty()) {
            _uiState.update { it.copy(error = "No stream URL provided", showLoadingOverlay = false) }
            return
        }

        viewModelScope.launch {
            try {
                autoSubtitleSelected = false
                resetLoadingOverlayForNewStream()
                val playerSettings = playerSettingsDataStore.playerSettings.first()
                val useLibass = playerSettings.useLibass
                val libassRenderType = playerSettings.libassRenderType.toAssRenderType()
                val bufferSettings = playerSettings.bufferSettings
                currentUseParallelConnections = bufferSettings.useParallelConnections

                val loadControlBuilder = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        bufferSettings.minBufferMs,
                        bufferSettings.maxBufferMs,
                        bufferSettings.bufferForPlaybackMs,
                        bufferSettings.bufferForPlaybackAfterRebufferMs
                    )
                    .setBackBuffer(
                        bufferSettings.backBufferDurationMs,
                        bufferSettings.retainBackBufferFromKeyframe
                    )

                if (bufferSettings.targetBufferSizeMb > 0) {
                    loadControlBuilder.setTargetBufferBytes(bufferSettings.targetBufferSizeMb * 1024 * 1024)
                }

                val loadControl = loadControlBuilder.build()

                trackSelector = DefaultTrackSelector(context).apply {
                    setParameters(
                        buildUponParameters()
                            .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                    )
                    if (playerSettings.tunnelingEnabled) {
                        setParameters(
                            buildUponParameters().setTunnelingEnabled(true)
                        )
                    }
                    
                    when (playerSettings.preferredAudioLanguage) {
                        AudioLanguageOption.DEFAULT -> {   }
                        AudioLanguageOption.DEVICE -> {
                            
                            val deviceLanguages = if (Build.VERSION.SDK_INT >= 24) {
                                val localeList = Resources.getSystem().configuration.locales
                                Array(localeList.size()) { localeList[it].isO3Language }
                            } else {
                                arrayOf(Resources.getSystem().configuration.locale.isO3Language)
                            }
                            setParameters(
                                buildUponParameters().setPreferredAudioLanguages(*deviceLanguages)
                            )
                        }
                        else -> {
                            setParameters(
                                buildUponParameters().setPreferredAudioLanguages(
                                    playerSettings.preferredAudioLanguage
                                )
                            )
                        }
                    }

                    
                    val appContext = this@PlayerViewModel.context
                    val captioningManager = appContext.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
                    if (captioningManager != null) {
                        if (!captioningManager.isEnabled) {
                            setParameters(
                                buildUponParameters().setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            )
                        }
                        captioningManager.locale?.let { locale ->
                            setParameters(
                                buildUponParameters().setPreferredTextLanguage(locale.isO3Language)
                            )
                        }
                    }
                }

                
                val extractorsFactory = DefaultExtractorsFactory()
                    .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
                    .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)

                
                val renderersFactory = DefaultRenderersFactory(context)
                    .setExtensionRendererMode(playerSettings.decoderPriority)
                    .setMapDV7ToHevc(playerSettings.mapDV7ToHevc)

                _exoPlayer = if (useLibass) {
                    
                    ExoPlayer.Builder(context)
                        .setLoadControl(loadControl)
                        .setTrackSelector(trackSelector!!)
                        .setMediaSourceFactory(DefaultMediaSourceFactory(context, extractorsFactory))
                        .buildWithAssSupport(
                            context = context,
                            renderType = libassRenderType,
                            renderersFactory = renderersFactory
                        )
                } else {
                    
                    ExoPlayer.Builder(context)
                        .setTrackSelector(trackSelector!!)
                        .setMediaSourceFactory(DefaultMediaSourceFactory(context, extractorsFactory))
                        .setRenderersFactory(renderersFactory)
                        .setLoadControl(loadControl)
                        .build()
                }

                _exoPlayer?.apply {
                    
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build()
                    setAudioAttributes(audioAttributes, true)

                    
                    if (playerSettings.skipSilence) {
                        skipSilenceEnabled = true
                    }

                    
                    setHandleAudioBecomingNoisy(true)

                    
                    try {
                        currentMediaSession?.release()
                        if (canAdvertiseSession()) {
                            currentMediaSession = MediaSession.Builder(context, this).build()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    
                    _uiState.update { it.copy(frameRateMatchingEnabled = playerSettings.frameRateMatching) }

                    
                    try {
                        loudnessEnhancer?.release()
                        loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    
                    notifyAudioSessionUpdate(true)

                    val preferred = playerSettings.subtitleStyle.preferredLanguage
                    val secondary = playerSettings.subtitleStyle.secondaryPreferredLanguage
                    applySubtitlePreferences(preferred, secondary)
                    setMediaSource(createMediaSource(url, headers))

                    playWhenReady = true
                    prepare()

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            val playerDuration = duration
                            if (playerDuration > lastKnownDuration) {
                                lastKnownDuration = playerDuration
                            }
                            val isBuffering = playbackState == Player.STATE_BUFFERING
                            _uiState.update { 
                                it.copy(
                                    isBuffering = isBuffering,
                                    duration = playerDuration.coerceAtLeast(0L)
                                )
                            }

                            if (playbackState == Player.STATE_BUFFERING && !hasRenderedFirstFrame) {
                                _uiState.update { state ->
                                    if (state.loadingOverlayEnabled && !state.showLoadingOverlay) {
                                        state.copy(showLoadingOverlay = true, showControls = false)
                                    } else {
                                        state
                                    }
                                }
                            }
                        
                            
                            if (playbackState == Player.STATE_READY) {
                                _uiState.value.pendingSeekPosition?.let { position ->
                                    seekTo(position)
                                    _uiState.update { it.copy(pendingSeekPosition = null) }
                                }
                            }
                        
                            
                            if (playbackState == Player.STATE_ENDED) {
                                saveWatchProgress()
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _uiState.update { it.copy(isPlaying = isPlaying) }
                            if (isPlaying) {
                                userPausedManually = false
                                cancelPauseOverlay()
                                startProgressUpdates()
                                startWatchProgressSaving()
                                scheduleHideControls()
                                tryShowParentalGuide()
                            } else {
                                if (userPausedManually) {
                                    schedulePauseOverlay()
                                } else {
                                    cancelPauseOverlay()
                                }
                                stopProgressUpdates()
                                stopWatchProgressSaving()
                                
                                saveWatchProgress()
                            }
                        }

                        override fun onTracksChanged(tracks: Tracks) {
                            updateAvailableTracks(tracks)
                        }

                        override fun onRenderedFirstFrame() {
                            hasRenderedFirstFrame = true
                            _uiState.update { it.copy(showLoadingOverlay = false) }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            _uiState.update { 
                                it.copy(
                                    error = error.message ?: "Playback error occurred",
                                    showLoadingOverlay = false,
                                    showPauseOverlay = false
                                )
                            }
                        }
                    })
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Failed to initialize player",
                        showLoadingOverlay = false
                    )
                }
            }
        }
    }

    private fun resetLoadingOverlayForNewStream() {
        hasRenderedFirstFrame = false
        userPausedManually = false
        lastKnownDuration = 0L
        _uiState.update { state ->
            state.copy(
                showLoadingOverlay = state.loadingOverlayEnabled,
                showControls = false
            )
        }
    }

    

 
    private fun LibassRenderType.toAssRenderType(): AssRenderType {
        return when (this) {
            LibassRenderType.CUES -> AssRenderType.CUES
            LibassRenderType.EFFECTS_CANVAS -> AssRenderType.EFFECTS_CANVAS
            LibassRenderType.EFFECTS_OPEN_GL -> AssRenderType.EFFECTS_OPEN_GL
            LibassRenderType.OVERLAY_CANVAS -> AssRenderType.OVERLAY_CANVAS
            LibassRenderType.OVERLAY_OPEN_GL -> AssRenderType.OVERLAY_OPEN_GL
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(UnstableApi::class)
    private fun getOrCreateOkHttpClient(): OkHttpClient {
        return okHttpClient ?: OkHttpClient.Builder()
            .connectTimeout(8000, TimeUnit.MILLISECONDS)
            .readTimeout(8000, TimeUnit.MILLISECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .build()
            .also { okHttpClient = it }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(UnstableApi::class)
    private fun createMediaSource(url: String, headers: Map<String, String>): MediaSource {
        val okHttpFactory = OkHttpDataSource.Factory(getOrCreateOkHttpClient()).apply {
            setDefaultRequestProperties(headers)
            setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }

        val isHls = url.contains(".m3u8", ignoreCase = true) ||
            url.contains("/playlist", ignoreCase = true) ||
            url.contains("/hls", ignoreCase = true) ||
            url.contains("m3u8", ignoreCase = true)

        val isDash = url.contains(".mpd", ignoreCase = true) ||
            url.contains("/dash", ignoreCase = true)

        val mediaItemBuilder = MediaItem.Builder().setUri(url)
        when {
            isHls -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            isDash -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
        }

        val mediaItem = mediaItemBuilder.build()

        return when {
            isHls -> HlsMediaSource.Factory(okHttpFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
            isDash -> DashMediaSource.Factory(okHttpFactory)
                .createMediaSource(mediaItem)
            else -> {
                
                val progressiveFactory: DataSource.Factory = if (currentUseParallelConnections) {
                    ParallelRangeDataSource.Factory(okHttpFactory)
                } else {
                    okHttpFactory
                }
                DefaultMediaSourceFactory(progressiveFactory)
                    .createMediaSource(mediaItem)
            }
        }
    }

    private fun parseHeaders(headers: String?): Map<String, String> {
        if (headers.isNullOrEmpty()) return emptyMap()
        
        return try {
            
            headers.split("&").associate { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts[1], "UTF-8")
                } else {
                    "" to ""
                }
            }.filterKeys { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun showEpisodesPanel() {
        _uiState.update {
            it.copy(
                showEpisodesPanel = true,
                showControls = true,
                showAudioDialog = false,
                showSubtitleDialog = false,
                showSpeedDialog = false
            )
        }

        
        val desiredSeason = currentSeason ?: _uiState.value.episodesSelectedSeason
        if (_uiState.value.episodesAll.isNotEmpty() && desiredSeason != null) {
            selectEpisodesSeason(desiredSeason)
        } else {
            loadEpisodesIfNeeded()
        }
    }

    private fun showSourcesPanel() {
        _uiState.update {
            it.copy(
                showSourcesPanel = true,
                showControls = true,
                showAudioDialog = false,
                showSubtitleDialog = false,
                showSpeedDialog = false,
                showEpisodesPanel = false,
                showEpisodeStreams = false
            )
        }
        loadSourceStreams()
    }

    private fun loadSourceStreams() {
        val type: String
        val vid: String

        if (contentType in listOf("series", "tv") && currentSeason != null && currentEpisode != null) {
            type = contentType ?: return
            vid = currentVideoId ?: contentId ?: return
        } else {
            type = contentType ?: "movie"
            vid = contentId ?: return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingSourceStreams = true,
                    sourceStreamsError = null,
                    sourceAllStreams = emptyList(),
                    sourceSelectedAddonFilter = null,
                    sourceFilteredStreams = emptyList(),
                    sourceAvailableAddons = emptyList()
                )
            }

            streamRepository.getStreamsFromAllAddons(
                type = type,
                videoId = vid,
                season = if (contentType in listOf("series", "tv")) currentSeason else null,
                episode = if (contentType in listOf("series", "tv")) currentEpisode else null
            ).collect { result ->
                when (result) {
                    is NetworkResult.Success -> {
                        val addonStreams = result.data
                        val allStreams = addonStreams.flatMap { it.streams }
                        val availableAddons = addonStreams.map { it.addonName }
                        _uiState.update {
                            it.copy(
                                isLoadingSourceStreams = false,
                                sourceAllStreams = allStreams,
                                sourceSelectedAddonFilter = null,
                                sourceFilteredStreams = allStreams,
                                sourceAvailableAddons = availableAddons,
                                sourceStreamsError = null
                            )
                        }
                    }

                    is NetworkResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoadingSourceStreams = false,
                                sourceStreamsError = result.message
                            )
                        }
                    }

                    NetworkResult.Loading -> {
                        _uiState.update { it.copy(isLoadingSourceStreams = true) }
                    }
                }
            }
        }
    }

    private fun dismissSourcesPanel() {
        _uiState.update {
            it.copy(
                showSourcesPanel = false,
                isLoadingSourceStreams = false,
                sourceStreamsError = null,
                sourceAllStreams = emptyList(),
                sourceSelectedAddonFilter = null,
                sourceFilteredStreams = emptyList(),
                sourceAvailableAddons = emptyList()
            )
        }
        scheduleHideControls()
    }

    private fun filterSourceStreamsByAddon(addonName: String?) {
        val allStreams = _uiState.value.sourceAllStreams
        val filteredStreams = if (addonName == null) {
            allStreams
        } else {
            allStreams.filter { it.addonName == addonName }
        }
        _uiState.update {
            it.copy(
                sourceSelectedAddonFilter = addonName,
                sourceFilteredStreams = filteredStreams
            )
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun switchToSourceStream(stream: Stream) {
        val url = stream.getStreamUrl()
        if (url.isNullOrBlank()) {
            _uiState.update { it.copy(sourceStreamsError = "Invalid stream URL") }
            return
        }

        saveWatchProgress()

        val newHeaders = stream.behaviorHints?.proxyHeaders?.request ?: emptyMap()
        currentStreamUrl = url
        currentHeaders = newHeaders
        lastSavedPosition = 0L
        resetLoadingOverlayForNewStream()

        _uiState.update {
            it.copy(
                isBuffering = true,
                error = null,
                currentStreamName = stream.name ?: stream.addonName,
                showSourcesPanel = false,
                isLoadingSourceStreams = false,
                sourceStreamsError = null,
                sourceAllStreams = emptyList(),
                sourceSelectedAddonFilter = null,
                sourceFilteredStreams = emptyList(),
                sourceAvailableAddons = emptyList()
            )
        }

        _exoPlayer?.let { player ->
            try {
                player.setMediaSource(createMediaSource(url, newHeaders))
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to play selected stream") }
                return
            }
        } ?: run {
            initializePlayer(url, newHeaders)
        }

        loadSavedProgressFor(currentSeason, currentEpisode)
    }

    private fun dismissEpisodesPanel() {
        _uiState.update {
            it.copy(
                showEpisodesPanel = false,
                showEpisodeStreams = false,
                isLoadingEpisodeStreams = false,
                episodeStreamsError = null,
                episodeAllStreams = emptyList(),
                episodeSelectedAddonFilter = null,
                episodeFilteredStreams = emptyList(),
                episodeAvailableAddons = emptyList(),
                episodeStreamsForVideoId = null,
                episodeStreamsSeason = null,
                episodeStreamsEpisode = null,
                episodeStreamsTitle = null
            )
        }
        scheduleHideControls()
    }

    private fun selectEpisodesSeason(season: Int) {
        val all = _uiState.value.episodesAll
        if (all.isEmpty()) return

        val seasons = _uiState.value.episodesAvailableSeasons
        if (seasons.isNotEmpty() && season !in seasons) return

        val episodesForSeason = all
            .filter { (it.season ?: -1) == season }
            .sortedWith(compareBy<Video> { it.episode ?: Int.MAX_VALUE }.thenBy { it.title })

        _uiState.update {
            it.copy(
                episodesSelectedSeason = season,
                episodes = episodesForSeason
            )
        }
    }

    private fun loadEpisodesIfNeeded() {
        val type = contentType
        val id = contentId
        if (type.isNullOrBlank() || id.isNullOrBlank()) return
        if (type !in listOf("series", "tv")) return
        if (_uiState.value.episodesAll.isNotEmpty() || _uiState.value.isLoadingEpisodes) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingEpisodes = true, episodesError = null) }

            when (
                val result = metaRepository.getMetaFromAllAddons(type = type, id = id)
                    .first { it !is NetworkResult.Loading }
            ) {
                is NetworkResult.Success -> {
                    val allEpisodes = result.data.videos
                        .sortedWith(
                            compareBy<Video> { it.season ?: Int.MAX_VALUE }
                                .thenBy { it.episode ?: Int.MAX_VALUE }
                                .thenBy { it.title }
                        )

                    applyMetaDetails(result.data)

                    val seasons = allEpisodes
                        .mapNotNull { it.season }
                        .distinct()
                        .sorted()

                    val preferredSeason = when {
                        currentSeason != null && seasons.contains(currentSeason) -> currentSeason
                        initialSeason != null && seasons.contains(initialSeason) -> initialSeason
                        else -> seasons.firstOrNull { it > 0 } ?: seasons.firstOrNull() ?: 1
                    }

                    val selectedSeason = preferredSeason ?: 1
                    val episodesForSeason = allEpisodes
                        .filter { (it.season ?: -1) == selectedSeason }
                        .sortedWith(compareBy<Video> { it.episode ?: Int.MAX_VALUE }.thenBy { it.title })

                    _uiState.update {
                        it.copy(
                            isLoadingEpisodes = false,
                            episodesAll = allEpisodes,
                            episodesAvailableSeasons = seasons,
                            episodesSelectedSeason = selectedSeason,
                            episodes = episodesForSeason,
                            episodesError = null
                        )
                    }
                }

                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isLoadingEpisodes = false, episodesError = result.message) }
                }

                NetworkResult.Loading -> {
                    
                }
            }
        }
    }

    private fun loadStreamsForEpisode(video: Video) {
        val type = contentType
        if (type.isNullOrBlank()) {
            _uiState.update { it.copy(episodeStreamsError = "Missing content type") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showEpisodeStreams = true,
                    isLoadingEpisodeStreams = true,
                    episodeStreamsError = null,
                    episodeAllStreams = emptyList(),
                    episodeSelectedAddonFilter = null,
                    episodeFilteredStreams = emptyList(),
                    episodeAvailableAddons = emptyList(),
                    episodeStreamsForVideoId = video.id,
                    episodeStreamsSeason = video.season,
                    episodeStreamsEpisode = video.episode,
                    episodeStreamsTitle = video.title
                )
            }

            streamRepository.getStreamsFromAllAddons(
                type = type,
                videoId = video.id,
                season = video.season,
                episode = video.episode
            ).collect { result ->
                when (result) {
                    is NetworkResult.Success -> {
                        val addonStreams = result.data
                        val allStreams = addonStreams.flatMap { it.streams }
                        val availableAddons = addonStreams.map { it.addonName }
                        val filteredStreams = allStreams
                        _uiState.update {
                            it.copy(
                                isLoadingEpisodeStreams = false,
                                episodeAllStreams = allStreams,
                                episodeSelectedAddonFilter = null,
                                episodeFilteredStreams = filteredStreams,
                                episodeAvailableAddons = availableAddons,
                                episodeStreamsError = null
                            )
                        }
                    }

                    is NetworkResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoadingEpisodeStreams = false,
                                episodeStreamsError = result.message
                            )
                        }
                    }

                    NetworkResult.Loading -> {
                        _uiState.update { it.copy(isLoadingEpisodeStreams = true) }
                    }
                }
            }
        }
    }

    private fun switchToEpisodeStream(stream: Stream) {
        val url = stream.getStreamUrl()
        if (url.isNullOrBlank()) {
            _uiState.update { it.copy(episodeStreamsError = "Invalid stream URL") }
            return
        }

        saveWatchProgress()

        val newHeaders = stream.behaviorHints?.proxyHeaders?.request ?: emptyMap()
        val targetVideo = _uiState.value.episodes.firstOrNull { it.id == _uiState.value.episodeStreamsForVideoId }

        currentStreamUrl = url
        currentHeaders = newHeaders
        currentVideoId = targetVideo?.id ?: _uiState.value.episodeStreamsForVideoId ?: currentVideoId
        currentSeason = targetVideo?.season ?: _uiState.value.episodeStreamsSeason ?: currentSeason
        currentEpisode = targetVideo?.episode ?: _uiState.value.episodeStreamsEpisode ?: currentEpisode
        currentEpisodeTitle = targetVideo?.title ?: _uiState.value.episodeStreamsTitle ?: currentEpisodeTitle

        lastSavedPosition = 0L
        resetLoadingOverlayForNewStream()

        _uiState.update {
            it.copy(
                isBuffering = true,
                error = null,
                currentSeason = currentSeason,
                currentEpisode = currentEpisode,
                currentEpisodeTitle = currentEpisodeTitle,
                currentStreamName = stream.name ?: stream.addonName, 
                showEpisodesPanel = false,
                showEpisodeStreams = false,
                isLoadingEpisodeStreams = false,
                episodeStreamsError = null,
                episodeAllStreams = emptyList(),
                episodeSelectedAddonFilter = null,
                episodeFilteredStreams = emptyList(),
                episodeAvailableAddons = emptyList(),
                episodeStreamsForVideoId = null,
                episodeStreamsSeason = null,
                episodeStreamsEpisode = null,
                episodeStreamsTitle = null,
                
                parentalWarnings = emptyList(),
                showParentalGuide = false,
                parentalGuideHasShown = false,
                
                activeSkipInterval = null,
                skipIntervalDismissed = false
            )
        }

        updateEpisodeDescription()

        playbackStartedForParentalGuide = false
        skipIntervals = emptyList()
        skipIntroFetchedKey = null
        lastActiveSkipType = null

        
        fetchParentalGuide(contentId, contentType, currentSeason, currentEpisode)
        fetchSkipIntervals(contentId, currentSeason, currentEpisode)

        _exoPlayer?.let { player ->
            try {
                player.setMediaSource(createMediaSource(url, newHeaders))
                player.prepare()
                player.playWhenReady = true
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to play selected stream") }
                return
            }
        } ?: run {
            initializePlayer(url, newHeaders)
        }

        loadSavedProgressFor(currentSeason, currentEpisode)
    }

    @OptIn(UnstableApi::class)
    private fun updateAvailableTracks(tracks: Tracks) {
        val audioTracks = mutableListOf<TrackInfo>()
        val subtitleTracks = mutableListOf<TrackInfo>()
        var selectedAudioIndex = -1
        var selectedSubtitleIndex = -1

        tracks.groups.forEachIndexed { groupIndex, trackGroup ->
            val trackType = trackGroup.type
            
            when (trackType) {
                C.TRACK_TYPE_VIDEO -> {
                    
                    for (i in 0 until trackGroup.length) {
                        if (trackGroup.isTrackSelected(i)) {
                            val format = trackGroup.getTrackFormat(i)
                            if (format.frameRate > 0f) {
                                val snapped = FrameRateUtils.snapToStandardRate(format.frameRate)
                                _uiState.update { it.copy(detectedFrameRate = snapped) }
                            }
                            break
                        }
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val isSelected = trackGroup.isTrackSelected(i)
                        if (isSelected) selectedAudioIndex = audioTracks.size

                        
                        val codecName = CustomDefaultTrackNameProvider.formatNameFromMime(format.sampleMimeType)
                        val channelLayout = CustomDefaultTrackNameProvider.getChannelLayoutName(
                            format.channelCount
                        )
                        val baseName = format.label ?: format.language ?: "Audio ${audioTracks.size + 1}"
                        val suffix = listOfNotNull(codecName, channelLayout).joinToString(" ")
                        val displayName = if (suffix.isNotEmpty()) "$baseName ($suffix)" else baseName

                        audioTracks.add(
                            TrackInfo(
                                index = audioTracks.size,
                                name = displayName,
                                language = format.language,
                                codec = codecName,
                                channelCount = format.channelCount.takeIf { it > 0 },
                                isSelected = isSelected
                            )
                        )
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val isSelected = trackGroup.isTrackSelected(i)
                        if (isSelected) selectedSubtitleIndex = subtitleTracks.size
                        
                        subtitleTracks.add(
                            TrackInfo(
                                index = subtitleTracks.size,
                                name = format.label ?: format.language ?: "Subtitle ${subtitleTracks.size + 1}",
                                language = format.language,
                                isSelected = isSelected
                            )
                        )
                    }
                }
            }
        }

        fun matchesLanguage(track: TrackInfo, target: String): Boolean {
            val lang = track.language?.lowercase() ?: return false
            return lang == target || lang.startsWith(target) || lang.contains(target)
        }

        val pendingLang = pendingAddonSubtitleLanguage
        if (pendingLang != null && subtitleTracks.isNotEmpty()) {
            val preferredIndex = subtitleTracks.indexOfFirst { matchesLanguage(it, pendingLang) }
            val fallbackIndex = if (preferredIndex >= 0) preferredIndex else 0

            selectSubtitleTrack(fallbackIndex)
            selectedSubtitleIndex = if (_uiState.value.selectedAddonSubtitle != null) -1 else fallbackIndex
            pendingAddonSubtitleLanguage = null
        } else if (selectedSubtitleIndex == -1 && subtitleTracks.isNotEmpty() && !autoSubtitleSelected) {
            val preferred = _uiState.value.subtitleStyle.preferredLanguage.lowercase()

            
            if (preferred == "none") {
                autoSubtitleSelected = true
                // Leave selectedSubtitleIndex as -1 (no subtitle)
            } else {
                val secondary = _uiState.value.subtitleStyle.secondaryPreferredLanguage?.lowercase()

                val preferredMatch = subtitleTracks.indexOfFirst { matchesLanguage(it, preferred) }
                val secondaryMatch = secondary?.let { target ->
                    subtitleTracks.indexOfFirst { matchesLanguage(it, target) }
                } ?: -1

                val autoIndex = when {
                    preferredMatch >= 0 -> preferredMatch
                    secondaryMatch >= 0 -> secondaryMatch
                    else -> -1
                }

                autoSubtitleSelected = true
                if (autoIndex >= 0) {
                    selectSubtitleTrack(autoIndex)
                    selectedSubtitleIndex = autoIndex
                }
            }
        }

        _uiState.update {
            it.copy(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                selectedAudioTrackIndex = selectedAudioIndex,
                selectedSubtitleTrackIndex = selectedSubtitleIndex
            )
        }
    }

    private fun applySubtitlePreferences(preferred: String, secondary: String?) {
        _exoPlayer?.let { player ->
            val builder = player.trackSelectionParameters.buildUpon()

            if (preferred == "none") {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                builder.setPreferredTextLanguage(preferred)
            }

            player.trackSelectionParameters = builder.build()
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                _exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val playerDuration = player.duration
                    if (playerDuration > lastKnownDuration) {
                        lastKnownDuration = playerDuration
                    }
                    _uiState.update {
                        it.copy(
                            currentPosition = pos,
                            duration = playerDuration.coerceAtLeast(0L)
                        )
                    }
                    updateActiveSkipInterval(pos)

                    
                    if (player.isPlaying) {
                        val now = System.currentTimeMillis()
                        if (now - lastBufferLogTimeMs >= 10_000) {
                            lastBufferLogTimeMs = now
                            val bufAhead = (player.bufferedPosition - player.currentPosition) / 1000
                            val loading = player.isLoading
                            val runtime = Runtime.getRuntime()
                            val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                            val maxMb = runtime.maxMemory() / (1024 * 1024)
                            Log.d(TAG, "BUFFER: ahead=${bufAhead}s, loading=$loading, heap=${usedMb}/${maxMb}MB, pos=${pos / 1000}s")
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun startWatchProgressSaving() {
        watchProgressSaveJob?.cancel()
        watchProgressSaveJob = viewModelScope.launch {
            while (isActive) {
                delay(10000) 
                saveWatchProgressIfNeeded()
            }
        }
    }

    private fun stopWatchProgressSaving() {
        watchProgressSaveJob?.cancel()
        watchProgressSaveJob = null
    }

    private fun saveWatchProgressIfNeeded() {
        val currentPosition = _exoPlayer?.currentPosition ?: return
        val duration = getEffectiveDuration(currentPosition)
        if (duration <= 0L) return
        
        
        if (kotlin.math.abs(currentPosition - lastSavedPosition) >= saveThresholdMs) {
            lastSavedPosition = currentPosition
            saveWatchProgressInternal(currentPosition, duration)
        }
    }

    private fun saveWatchProgress() {
        val currentPosition = _exoPlayer?.currentPosition ?: return
        val duration = getEffectiveDuration(currentPosition)
        if (duration <= 0L) return
        saveWatchProgressInternal(currentPosition, duration)
    }

    private fun getEffectiveDuration(position: Long): Long {
        val playerDuration = _exoPlayer?.duration ?: 0L
        val effectiveDuration = maxOf(playerDuration, lastKnownDuration)
        if (effectiveDuration <= 0L) return 0L

        val isEnded = _exoPlayer?.playbackState == Player.STATE_ENDED
        if (!isEnded && effectiveDuration < position) return 0L

        return effectiveDuration
    }

    private fun saveWatchProgressInternal(position: Long, duration: Long) {
        
        if (contentId.isNullOrEmpty() || contentType.isNullOrEmpty()) return
        
        if (duration <= 0) return
        
        if (position < 1000) return

        val progress = WatchProgress(
            contentId = contentId,
            contentType = contentType,
            name = contentName ?: title,
            poster = poster,
            backdrop = backdrop,
            logo = logo,
            videoId = currentVideoId ?: contentId,
            season = currentSeason,
            episode = currentEpisode,
            episodeTitle = currentEpisodeTitle,
            position = position,
            duration = duration,
            lastWatched = System.currentTimeMillis()
        )

        viewModelScope.launch {
            watchProgressRepository.saveProgress(progress)
        }
    }

    fun scheduleHideControls() {
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(3000)
            if (_uiState.value.isPlaying && !_uiState.value.showAudioDialog &&
                !_uiState.value.showSubtitleDialog && !_uiState.value.showSpeedDialog &&
                !_uiState.value.showEpisodesPanel && !_uiState.value.showSourcesPanel) {
                _uiState.update { it.copy(showControls = false) }
            }
        }
    }

    private fun schedulePauseOverlay() {
        pauseOverlayJob?.cancel()

        if (!_uiState.value.pauseOverlayEnabled || !hasRenderedFirstFrame || !userPausedManually) {
            _uiState.update { it.copy(showPauseOverlay = false) }
            return
        }

        _uiState.update { it.copy(showPauseOverlay = false) }
        pauseOverlayJob = viewModelScope.launch {
            delay(pauseOverlayDelayMs)
            if (!_uiState.value.isPlaying && _uiState.value.pauseOverlayEnabled && _uiState.value.error == null) {
                _uiState.update { it.copy(showPauseOverlay = true, showControls = false) }
            }
        }
    }

    private fun cancelPauseOverlay() {
        pauseOverlayJob?.cancel()
        pauseOverlayJob = null
        _uiState.update { it.copy(showPauseOverlay = false) }
    }

    fun onUserInteraction() {
        
        if (_uiState.value.showPauseOverlay || pauseOverlayJob != null) {
            cancelPauseOverlay()
        }
    }

    fun hideControls() {
        hideControlsJob?.cancel()
        _uiState.update { it.copy(showControls = false, showSeekOverlay = false) }
    }

    fun onEvent(event: PlayerEvent) {
        onUserInteraction()
        when (event) {
            PlayerEvent.OnPlayPause -> {
                _exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        userPausedManually = true
                        player.pause()
                        schedulePauseOverlay()
                    } else {
                        userPausedManually = false
                        cancelPauseOverlay()
                        player.play()
                    }
                }
                showControlsTemporarily()
            }
            PlayerEvent.OnSeekForward -> {
                _exoPlayer?.let { player ->
                    val target = (player.currentPosition + 10000).coerceAtMost(player.duration)
                    player.seekTo(target)
                    _uiState.update { it.copy(currentPosition = target) }
                }
                if (_uiState.value.showControls) {
                    showControlsTemporarily()
                } else {
                    showSeekOverlayTemporarily()
                }
            }
            PlayerEvent.OnSeekBackward -> {
                _exoPlayer?.let { player ->
                    val target = (player.currentPosition - 10000).coerceAtLeast(0)
                    player.seekTo(target)
                    _uiState.update { it.copy(currentPosition = target) }
                }
                if (_uiState.value.showControls) {
                    showControlsTemporarily()
                } else {
                    showSeekOverlayTemporarily()
                }
            }
            is PlayerEvent.OnSeekTo -> {
                _exoPlayer?.seekTo(event.position)
                _uiState.update { it.copy(currentPosition = event.position) }
                if (_uiState.value.showControls) {
                    showControlsTemporarily()
                } else {
                    showSeekOverlayTemporarily()
                }
            }
            is PlayerEvent.OnSelectAudioTrack -> {
                selectAudioTrack(event.index)
                _uiState.update { it.copy(showAudioDialog = false) }
            }
            is PlayerEvent.OnSelectSubtitleTrack -> {
                selectSubtitleTrack(event.index)
                _uiState.update { 
                    it.copy(
                        showSubtitleDialog = false,
                        selectedAddonSubtitle = null 
                    ) 
                }
            }
            PlayerEvent.OnDisableSubtitles -> {
                disableSubtitles()
                _uiState.update { 
                    it.copy(
                        showSubtitleDialog = false,
                        selectedAddonSubtitle = null,
                        selectedSubtitleTrackIndex = -1
                    ) 
                }
            }
            is PlayerEvent.OnSelectAddonSubtitle -> {
                selectAddonSubtitle(event.subtitle)
                _uiState.update { it.copy(showSubtitleDialog = false) }
            }
            is PlayerEvent.OnSetPlaybackSpeed -> {
                _exoPlayer?.setPlaybackSpeed(event.speed)
                _uiState.update { 
                    it.copy(playbackSpeed = event.speed, showSpeedDialog = false) 
                }
            }
            PlayerEvent.OnToggleControls -> {
                _uiState.update { it.copy(showControls = !it.showControls) }
                if (_uiState.value.showControls) {
                    scheduleHideControls()
                }
            }
            PlayerEvent.OnShowAudioDialog -> {
                _uiState.update { it.copy(showAudioDialog = true, showControls = true) }
            }
            PlayerEvent.OnShowSubtitleDialog -> {
                _uiState.update { it.copy(showSubtitleDialog = true, showControls = true) }
            }
            PlayerEvent.OnShowSpeedDialog -> {
                _uiState.update { it.copy(showSpeedDialog = true, showControls = true) }
            }
            PlayerEvent.OnShowEpisodesPanel -> {
                showEpisodesPanel()
            }
            PlayerEvent.OnDismissEpisodesPanel -> {
                dismissEpisodesPanel()
            }
            PlayerEvent.OnBackFromEpisodeStreams -> {
                _uiState.update {
                    it.copy(
                        showEpisodeStreams = false,
                        isLoadingEpisodeStreams = false,
                        episodeStreamsError = null,
                        episodeAllStreams = emptyList(),
                        episodeSelectedAddonFilter = null,
                        episodeFilteredStreams = emptyList(),
                        episodeAvailableAddons = emptyList(),
                        episodeStreamsForVideoId = null,
                        episodeStreamsSeason = null,
                        episodeStreamsEpisode = null,
                        episodeStreamsTitle = null
                    )
                }
            }
            is PlayerEvent.OnEpisodeSeasonSelected -> {
                selectEpisodesSeason(event.season)
            }
            is PlayerEvent.OnEpisodeSelected -> {
                loadStreamsForEpisode(event.video)
            }
            is PlayerEvent.OnEpisodeAddonFilterSelected -> {
                filterEpisodeStreamsByAddon(event.addonName)
            }
            is PlayerEvent.OnEpisodeStreamSelected -> {
                switchToEpisodeStream(event.stream)
            }
            PlayerEvent.OnShowSourcesPanel -> {
                showSourcesPanel()
            }
            PlayerEvent.OnDismissSourcesPanel -> {
                dismissSourcesPanel()
            }
            is PlayerEvent.OnSourceAddonFilterSelected -> {
                filterSourceStreamsByAddon(event.addonName)
            }
            is PlayerEvent.OnSourceStreamSelected -> {
                switchToSourceStream(event.stream)
            }
            PlayerEvent.OnDismissDialog -> {
                _uiState.update { 
                    it.copy(
                        showAudioDialog = false, 
                        showSubtitleDialog = false, 
                        showSpeedDialog = false
                    ) 
                }
                scheduleHideControls()
            }
            PlayerEvent.OnRetry -> {
                hasRenderedFirstFrame = false
                _uiState.update { state ->
                    state.copy(
                        error = null,
                        showLoadingOverlay = state.loadingOverlayEnabled
                    )
                }
                releasePlayer()
                initializePlayer(currentStreamUrl, currentHeaders)
            }
            PlayerEvent.OnParentalGuideHide -> {
                _uiState.update { it.copy(showParentalGuide = false) }
            }
            PlayerEvent.OnDismissPauseOverlay -> {
                cancelPauseOverlay()
            }
            PlayerEvent.OnSkipIntro -> {
                _uiState.value.activeSkipInterval?.let { interval ->
                    _exoPlayer?.seekTo((interval.endTime * 1000).toLong())
                    _uiState.update { it.copy(activeSkipInterval = null, skipIntervalDismissed = true) }
                }
            }
            PlayerEvent.OnDismissSkipIntro -> {
                _uiState.update { it.copy(skipIntervalDismissed = true) }
            }
            is PlayerEvent.OnSetSubtitleSize -> {
                viewModelScope.launch { playerSettingsDataStore.setSubtitleSize(event.size) }
            }
            is PlayerEvent.OnSetSubtitleTextColor -> {
                viewModelScope.launch { playerSettingsDataStore.setSubtitleTextColor(event.color) }
            }
            is PlayerEvent.OnSetSubtitleBold -> {
                viewModelScope.launch { playerSettingsDataStore.setSubtitleBold(event.bold) }
            }
            is PlayerEvent.OnSetSubtitleOutlineEnabled -> {
                viewModelScope.launch { playerSettingsDataStore.setSubtitleOutlineEnabled(event.enabled) }
            }
            is PlayerEvent.OnSetSubtitleOutlineColor -> {
                viewModelScope.launch { playerSettingsDataStore.setSubtitleOutlineColor(event.color) }
            }
            is PlayerEvent.OnSetSubtitleVerticalOffset -> {
                viewModelScope.launch { playerSettingsDataStore.setSubtitleVerticalOffset(event.offset) }
            }
            PlayerEvent.OnResetSubtitleDefaults -> {
                viewModelScope.launch {
                    val defaults = SubtitleStyleSettings()
                    playerSettingsDataStore.setSubtitleSize(defaults.size)
                    playerSettingsDataStore.setSubtitleTextColor(defaults.textColor)
                    playerSettingsDataStore.setSubtitleBold(defaults.bold)
                    playerSettingsDataStore.setSubtitleOutlineEnabled(defaults.outlineEnabled)
                    playerSettingsDataStore.setSubtitleOutlineColor(defaults.outlineColor)
                    playerSettingsDataStore.setSubtitleOutlineWidth(defaults.outlineWidth)
                    playerSettingsDataStore.setSubtitleVerticalOffset(defaults.verticalOffset)
                    playerSettingsDataStore.setSubtitleBackgroundColor(defaults.backgroundColor)
                }
            }
            PlayerEvent.OnToggleAspectRatio -> {
                val currentMode = _uiState.value.resizeMode
                val newMode = if (currentMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                val modeText = if (newMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                    "Fit"
                } else {
                    "Zoom"
                }
                Log.d("PlayerViewModel", "Aspect ratio toggled: $currentMode -> $newMode")
                _uiState.update { 
                    it.copy(
                        resizeMode = newMode,
                        showAspectRatioIndicator = true,
                        aspectRatioIndicatorText = modeText
                    ) 
                }
                // Auto-hide indicator after 1.5 seconds
                hideAspectRatioIndicatorJob?.cancel()
                hideAspectRatioIndicatorJob = viewModelScope.launch {
                    delay(1500)
                    _uiState.update { it.copy(showAspectRatioIndicator = false) }
                }
            }
        }
    }

    private fun filterEpisodeStreamsByAddon(addonName: String?) {
        val allStreams = _uiState.value.episodeAllStreams
        val filteredStreams = if (addonName == null) {
            allStreams
        } else {
            allStreams.filter { it.addonName == addonName }
        }

        _uiState.update {
            it.copy(
                episodeSelectedAddonFilter = addonName,
                episodeFilteredStreams = filteredStreams
            )
        }
    }

    private fun showControlsTemporarily() {
        hideSeekOverlayJob?.cancel()
        _uiState.update { it.copy(showControls = true, showSeekOverlay = false) }
        scheduleHideControls()
    }

    private fun showSeekOverlayTemporarily() {
        hideSeekOverlayJob?.cancel()
        _uiState.update { it.copy(showSeekOverlay = true) }
        hideSeekOverlayJob = viewModelScope.launch {
            delay(1500)
            _uiState.update { it.copy(showSeekOverlay = false) }
        }
    }

    private fun selectAudioTrack(trackIndex: Int) {
        _exoPlayer?.let { player ->
            val tracks = player.currentTracks
            var currentAudioIndex = 0
            
            tracks.groups.forEach { trackGroup ->
                if (trackGroup.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until trackGroup.length) {
                        if (currentAudioIndex == trackIndex) {
                            val override = TrackSelectionOverride(trackGroup.mediaTrackGroup, i)
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(override)
                                .build()
                            return
                        }
                        currentAudioIndex++
                    }
                }
            }
        }
    }

    private fun selectSubtitleTrack(trackIndex: Int) {
        _exoPlayer?.let { player ->
            val tracks = player.currentTracks
            var currentSubIndex = 0
            
            tracks.groups.forEach { trackGroup ->
                if (trackGroup.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until trackGroup.length) {
                        if (currentSubIndex == trackIndex) {
                            val override = TrackSelectionOverride(trackGroup.mediaTrackGroup, i)
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(override)
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .build()
                            return
                        }
                        currentSubIndex++
                    }
                }
            }
        }
    }

    private fun disableSubtitles() {
        _exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    }
    
    private fun selectAddonSubtitle(subtitle: com.nuvio.tv.domain.model.Subtitle) {
        _exoPlayer?.let { player ->
            if (_uiState.value.selectedAddonSubtitle?.id == subtitle.id) {
                return@let
            }

            val normalizedLang = normalizeLanguageCode(subtitle.lang)
            pendingAddonSubtitleLanguage = normalizedLang

            
            val currentItem = player.currentMediaItem ?: return@let
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(
                android.net.Uri.parse(subtitle.url)
            )
                .setMimeType(getMimeTypeFromUrl(subtitle.url))
                .setLanguage(subtitle.lang)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            
            val newMediaItem = currentItem.buildUpon()
                .setSubtitleConfigurations(listOf(subtitleConfig))
                .build()
            
            val currentPosition = player.currentPosition
            val playWhenReady = player.playWhenReady

            player.setMediaItem(newMediaItem, currentPosition)
            player.prepare()
            player.playWhenReady = playWhenReady

            
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setPreferredTextLanguage(normalizedLang)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            
            _uiState.update { 
                it.copy(
                    selectedAddonSubtitle = subtitle,
                    selectedSubtitleTrackIndex = -1 
                )
            }
        }
    }

    private fun normalizeLanguageCode(lang: String): String {
        val code = lang.lowercase()
        return when (code) {
            "eng" -> "en"
            "spa" -> "es"
            "fre", "fra" -> "fr"
            "ger", "deu" -> "de"
            "ita" -> "it"
            "por" -> "pt"
            "rus" -> "ru"
            "jpn" -> "ja"
            "kor" -> "ko"
            "chi", "zho" -> "zh"
            "ara" -> "ar"
            "hin" -> "hi"
            "nld", "dut" -> "nl"
            "pol" -> "pl"
            "swe" -> "sv"
            "nor" -> "no"
            "dan" -> "da"
            "fin" -> "fi"
            "tur" -> "tr"
            "ell", "gre" -> "el"
            "heb" -> "he"
            "tha" -> "th"
            "vie" -> "vi"
            "ind" -> "id"
            "msa", "may" -> "ms"
            "ces", "cze" -> "cs"
            "hun" -> "hu"
            "ron", "rum" -> "ro"
            "ukr" -> "uk"
            "bul" -> "bg"
            "hrv" -> "hr"
            "srp" -> "sr"
            "slk", "slo" -> "sk"
            "slv" -> "sl"
            else -> code
        }
    }
    
    private fun getMimeTypeFromUrl(url: String): String {
        val lowerUrl = url.lowercase()
        return when {
            lowerUrl.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            lowerUrl.endsWith(".vtt") || lowerUrl.endsWith(".webvtt") -> MimeTypes.TEXT_VTT
            lowerUrl.endsWith(".ass") || lowerUrl.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lowerUrl.endsWith(".ttml") || lowerUrl.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP 
        }
    }

    private fun releasePlayer() {
        
        saveWatchProgress()

        
        notifyAudioSessionUpdate(false)

        
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        
        try {
            currentMediaSession?.release()
            currentMediaSession = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        progressJob?.cancel()
        hideControlsJob?.cancel()
        watchProgressSaveJob?.cancel()
        _exoPlayer?.release()
        _exoPlayer = null
    }

    


 
    private fun notifyAudioSessionUpdate(active: Boolean) {
        _exoPlayer?.let { player ->
            try {
                val intent = Intent(
                    if (active) AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
                    else AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION
                )
                intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                if (active) {
                    intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MOVIE)
                }
                context.sendBroadcast(intent)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
        
        okHttpClient?.let { client ->
            Thread {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }.start()
            okHttpClient = null
        }
    }
}
