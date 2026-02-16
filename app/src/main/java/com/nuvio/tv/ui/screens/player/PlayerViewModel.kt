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
import androidx.media3.ui.AspectRatioFrameLayout
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
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
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
import com.nuvio.tv.data.local.StreamLinkCacheDataStore
import com.nuvio.tv.data.local.SubtitleStyleSettings
import com.nuvio.tv.data.repository.TraktScrobbleItem
import com.nuvio.tv.data.repository.TraktScrobbleService
import com.nuvio.tv.data.repository.extractYear
import com.nuvio.tv.data.repository.parseContentIds
import com.nuvio.tv.data.repository.toTraktIds
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val traktScrobbleService: TraktScrobbleService,
    private val skipIntroRepository: SkipIntroRepository,
    private val playerSettingsDataStore: PlayerSettingsDataStore,
    private val streamLinkCacheDataStore: StreamLinkCacheDataStore,
    private val layoutPreferenceDataStore: com.nuvio.tv.data.local.LayoutPreferenceDataStore,
    private val watchedItemsPreferences: com.nuvio.tv.data.local.WatchedItemsPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val initialStreamUrl: String = savedStateHandle.get<String>("streamUrl") ?: ""
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

    
    // Navigation args are already decoded by NavController.
    // Decoding again breaks encoded IDs (e.g. addon IDs containing %3A/%2F segments).
    private val contentId: String? = savedStateHandle.get<String>("contentId")?.takeIf { it.isNotEmpty() }
    private val contentType: String? = savedStateHandle.get<String>("contentType")?.takeIf { it.isNotEmpty() }
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
    private val videoId: String? = savedStateHandle.get<String>("videoId")?.takeIf { it.isNotEmpty() }
    private val initialSeason: Int? = savedStateHandle.get<String>("season")?.toIntOrNull()
    private val initialEpisode: Int? = savedStateHandle.get<String>("episode")?.toIntOrNull()
    private val initialEpisodeTitle: String? = savedStateHandle.get<String>("episodeTitle")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }
    private val rememberedAudioLanguage: String? = savedStateHandle.get<String>("rememberedAudioLanguage")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }
    private val rememberedAudioName: String? = savedStateHandle.get<String>("rememberedAudioName")?.let {
        if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
    }

    private var currentStreamUrl: String = initialStreamUrl
    private var currentHeaders: Map<String, String> = parseHeaders(headersJson)

    fun getCurrentStreamUrl(): String = currentStreamUrl
    fun getCurrentHeaders(): Map<String, String> = currentHeaders

    fun stopAndRelease() {
        releasePlayer()
    }

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
    private var frameRateProbeJob: Job? = null
    private var frameRateProbeToken: Long = 0L
    private var hideAspectRatioIndicatorJob: Job? = null
    private var sourceStreamsJob: Job? = null
    private var sourceStreamsCacheRequestKey: String? = null
    
    
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
    private var lastSubtitlePreferredLanguage: String? = null
    private var lastSubtitleSecondaryLanguage: String? = null
    private var pendingAddonSubtitleLanguage: String? = null
    private var hasScannedTextTracksOnce: Boolean = false
    private var streamReuseLastLinkEnabled: Boolean = false
    private var hasAppliedRememberedAudioSelection: Boolean = false

    
    private var okHttpClient: OkHttpClient? = null
    private var lastBufferLogTimeMs: Long = 0L
    
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var currentMediaSession: MediaSession? = null
    private var pauseOverlayJob: Job? = null
    private val pauseOverlayDelayMs = 5000L
    private var pendingPreviewSeekPosition: Long? = null
    private var pendingResumeProgress: WatchProgress? = null
    private var currentScrobbleItem: TraktScrobbleItem? = null
    private var hasSentScrobbleStartForCurrentItem: Boolean = false
    private var hasSentCompletionScrobbleForCurrentItem: Boolean = false
    private var episodeStreamsJob: Job? = null
    private var episodeStreamsCacheRequestKey: String? = null
    private val streamCacheKey: String? by lazy {
        val type = contentType?.lowercase()
        val vid = currentVideoId
        if (type.isNullOrBlank() || vid.isNullOrBlank()) null else "$type|$vid"
    }

    init {
        refreshScrobbleItem()
        initializePlayer(currentStreamUrl, currentHeaders)
        loadSavedProgressFor(currentSeason, currentEpisode)
        fetchParentalGuide(contentId, contentType, currentSeason, currentEpisode)
        observeSubtitleSettings()
        fetchAddonSubtitles()
        fetchMetaDetails(contentId, contentType)
        observeBlurUnwatchedEpisodes()
        observeEpisodeWatchProgress()
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
                tryAutoSelectPreferredSubtitleFromAvailableTracks()
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

    private fun refreshSubtitlesForCurrentEpisode() {
        autoSubtitleSelected = false
        hasScannedTextTracksOnce = false
        pendingAddonSubtitleLanguage = null
        _uiState.update {
            it.copy(
                addonSubtitles = emptyList(),
                selectedAddonSubtitle = null,
                selectedSubtitleTrackIndex = -1,
                isLoadingAddonSubtitles = true,
                addonSubtitlesError = null
            )
        }
        fetchAddonSubtitles()
    }
    
    private fun observeBlurUnwatchedEpisodes() {
        viewModelScope.launch {
            layoutPreferenceDataStore.blurUnwatchedEpisodes.collectLatest { enabled ->
                _uiState.update { it.copy(blurUnwatchedEpisodes = enabled) }
            }
        }
    }

    private fun observeEpisodeWatchProgress() {
        val id = contentId ?: return
        val type = contentType ?: return
        if (type.lowercase() != "series") return
        val baseId = id.split(":").firstOrNull() ?: id
        viewModelScope.launch {
            watchProgressRepository.getAllEpisodeProgress(baseId).collectLatest { progressMap ->
                _uiState.update { it.copy(episodeWatchProgressMap = progressMap) }
            }
        }
        viewModelScope.launch {
            watchedItemsPreferences.getWatchedEpisodesForContent(baseId).collectLatest { watchedSet ->
                _uiState.update { it.copy(watchedEpisodeKeys = watchedSet) }
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
                        subtitleOrganizationMode = settings.subtitleOrganizationMode,
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
                streamReuseLastLinkEnabled = settings.streamReuseLastLinkEnabled

                applySubtitlePreferences(
                    settings.subtitleStyle.preferredLanguage,
                    settings.subtitleStyle.secondaryPreferredLanguage
                )
                val subtitlePreferenceChanged =
                    lastSubtitlePreferredLanguage != settings.subtitleStyle.preferredLanguage ||
                        lastSubtitleSecondaryLanguage != settings.subtitleStyle.secondaryPreferredLanguage
                if (subtitlePreferenceChanged) {
                    autoSubtitleSelected = false
                    lastSubtitlePreferredLanguage = settings.subtitleStyle.preferredLanguage
                    lastSubtitleSecondaryLanguage = settings.subtitleStyle.secondaryPreferredLanguage
                    tryAutoSelectPreferredSubtitleFromAvailableTracks()
                }

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
            pendingResumeProgress = null
            val progress = if (season != null && episode != null) {
                watchProgressRepository.getEpisodeProgress(contentId, season, episode).firstOrNull()
            } else {
                watchProgressRepository.getProgress(contentId).firstOrNull()
            }
            
            progress?.let { saved ->
                
                if (saved.isInProgress()) {
                    pendingResumeProgress = saved
                    _exoPlayer?.let { player ->
                        if (player.playbackState == Player.STATE_READY) {
                            tryApplyPendingResumeProgress(player)
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

    private fun tryApplyPendingResumeProgress(player: Player) {
        val saved = pendingResumeProgress ?: return
        val duration = player.duration
        val target = when {
            duration > 0L -> saved.resolveResumePosition(duration)
            saved.position > 0L -> saved.position
            else -> 0L
        }

        if (target > 0L) {
            player.seekTo(target)
            _uiState.update { it.copy(pendingSeekPosition = null) }
            pendingResumeProgress = null
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
                hasScannedTextTracksOnce = false
                resetLoadingOverlayForNewStream()
                val playerSettings = playerSettingsDataStore.playerSettings.first()
                val useLibass = false // Temporarily disabled for maintenance
                val libassRenderType = playerSettings.libassRenderType.toAssRenderType()
                val loadControl = DefaultLoadControl.Builder().build()

                
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
                    startFrameRateProbe(url, headers, playerSettings.frameRateMatching)

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
                                tryApplyPendingResumeProgress(this@apply)
                                _uiState.value.pendingSeekPosition?.let { position ->
                                    seekTo(position)
                                    _uiState.update { it.copy(pendingSeekPosition = null) }
                                }
                                // Re-evaluate subtitle auto-selection once player is ready.
                                tryAutoSelectPreferredSubtitleFromAvailableTracks()
                            }
                        
                            
                            if (playbackState == Player.STATE_ENDED) {
                                emitCompletionScrobbleStop(progressPercent = 99.5f)
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
                                emitScrobbleStart()
                            } else {
                                if (userPausedManually) {
                                    schedulePauseOverlay()
                                } else {
                                    cancelPauseOverlay()
                                }
                                stopProgressUpdates()
                                stopWatchProgressSaving()
                                if (playbackState != Player.STATE_BUFFERING) {
                                    emitStopScrobbleForCurrentProgress()
                                }
                                
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
                            val detailedError = buildString {
                                append(error.message ?: "Playback error")
                                val cause = error.cause
                                if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                                    append(" (HTTP ${cause.responseCode})")
                                } else if (cause != null) {
                                    append(": ${cause.message}")
                                }
                                append(" [${error.errorCode}]")
                            }
                            _uiState.update {
                                it.copy(
                                    error = detailedError,
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
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
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
                DefaultMediaSourceFactory(okHttpFactory)
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
                showSubtitleStylePanel = false,
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
                showSubtitleStylePanel = false,
                showSpeedDialog = false,
                showEpisodesPanel = false,
                showEpisodeStreams = false
            )
        }
        loadSourceStreams(forceRefresh = false)
    }

    private fun buildSourceRequestKey(type: String, videoId: String, season: Int?, episode: Int?): String {
        return "$type|$videoId|${season ?: -1}|${episode ?: -1}"
    }

    private fun loadSourceStreams(forceRefresh: Boolean) {
        val type: String
        val vid: String
        val seasonArg: Int?
        val episodeArg: Int?

        if (contentType in listOf("series", "tv") && currentSeason != null && currentEpisode != null) {
            type = contentType ?: return
            vid = currentVideoId ?: contentId ?: return
            seasonArg = currentSeason
            episodeArg = currentEpisode
        } else {
            type = contentType ?: "movie"
            vid = contentId ?: return
            seasonArg = null
            episodeArg = null
        }

        val requestKey = buildSourceRequestKey(type = type, videoId = vid, season = seasonArg, episode = episodeArg)
        val state = _uiState.value
        val hasCachedPayload = state.sourceAllStreams.isNotEmpty() || state.sourceStreamsError != null
        if (!forceRefresh && requestKey == sourceStreamsCacheRequestKey && hasCachedPayload) {
            return
        }
        if (!forceRefresh && state.isLoadingSourceStreams && requestKey == sourceStreamsCacheRequestKey) {
            return
        }

        val targetChanged = requestKey != sourceStreamsCacheRequestKey
        sourceStreamsJob?.cancel()
        sourceStreamsJob = viewModelScope.launch {
            sourceStreamsCacheRequestKey = requestKey
            _uiState.update {
                it.copy(
                    isLoadingSourceStreams = true,
                    sourceStreamsError = null,
                    sourceAllStreams = if (forceRefresh || targetChanged) emptyList() else it.sourceAllStreams,
                    sourceSelectedAddonFilter = if (forceRefresh || targetChanged) null else it.sourceSelectedAddonFilter,
                    sourceFilteredStreams = if (forceRefresh || targetChanged) emptyList() else it.sourceFilteredStreams,
                    sourceAvailableAddons = if (forceRefresh || targetChanged) emptyList() else it.sourceAvailableAddons
                )
            }

            streamRepository.getStreamsFromAllAddons(
                type = type,
                videoId = vid,
                season = seasonArg,
                episode = episodeArg
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
                isLoadingSourceStreams = false
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

        emitStopScrobbleForCurrentProgress()
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
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                selectedAudioTrackIndex = -1,
                selectedSubtitleTrackIndex = -1,
                showSourcesPanel = false,
                isLoadingSourceStreams = false,
                sourceStreamsError = null
            )
        }

        _exoPlayer?.let { player ->
            try {
                player.setMediaSource(createMediaSource(url, newHeaders))
                player.prepare()
                player.playWhenReady = true
                startFrameRateProbe(url, newHeaders, _uiState.value.frameRateMatchingEnabled)
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
                isLoadingEpisodeStreams = false
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
        loadStreamsForEpisode(video = video, forceRefresh = false)
    }

    private fun buildEpisodeRequestKey(type: String, video: Video): String {
        return "$type|${video.id}|${video.season ?: -1}|${video.episode ?: -1}"
    }

    private fun loadStreamsForEpisode(video: Video, forceRefresh: Boolean) {
        val type = contentType
        if (type.isNullOrBlank()) {
            _uiState.update { it.copy(episodeStreamsError = "Missing content type") }
            return
        }

        val requestKey = buildEpisodeRequestKey(type = type, video = video)
        val state = _uiState.value
        val hasCachedPayload = state.episodeAllStreams.isNotEmpty() || state.episodeStreamsError != null
        if (!forceRefresh && requestKey == episodeStreamsCacheRequestKey && hasCachedPayload) {
            _uiState.update {
                it.copy(
                    showEpisodeStreams = true,
                    isLoadingEpisodeStreams = false,
                    episodeStreamsForVideoId = video.id,
                    episodeStreamsSeason = video.season,
                    episodeStreamsEpisode = video.episode,
                    episodeStreamsTitle = video.title
                )
            }
            return
        }

        val targetChanged = requestKey != episodeStreamsCacheRequestKey
        episodeStreamsJob?.cancel()
        episodeStreamsJob = viewModelScope.launch {
            episodeStreamsCacheRequestKey = requestKey
            val previousAddonFilter = _uiState.value.episodeSelectedAddonFilter
            _uiState.update {
                it.copy(
                    showEpisodeStreams = true,
                    isLoadingEpisodeStreams = true,
                    episodeStreamsError = null,
                    episodeAllStreams = if (forceRefresh || targetChanged) emptyList() else it.episodeAllStreams,
                    episodeSelectedAddonFilter = if (forceRefresh || targetChanged) null else it.episodeSelectedAddonFilter,
                    episodeFilteredStreams = if (forceRefresh || targetChanged) emptyList() else it.episodeFilteredStreams,
                    episodeAvailableAddons = if (forceRefresh || targetChanged) emptyList() else it.episodeAvailableAddons,
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
                        val selectedAddon = previousAddonFilter?.takeIf { it in availableAddons }
                        val filteredStreams = if (selectedAddon == null) {
                            allStreams
                        } else {
                            allStreams.filter { it.addonName == selectedAddon }
                        }
                        _uiState.update {
                            it.copy(
                                isLoadingEpisodeStreams = false,
                                episodeAllStreams = allStreams,
                                episodeSelectedAddonFilter = selectedAddon,
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

    private fun reloadEpisodeStreams() {
        val state = _uiState.value
        val targetVideoId = state.episodeStreamsForVideoId
        val targetVideo = sequenceOf(
            state.episodes.firstOrNull { it.id == targetVideoId },
            state.episodesAll.firstOrNull { it.id == targetVideoId },
            state.episodes.firstOrNull {
                it.season == state.episodeStreamsSeason && it.episode == state.episodeStreamsEpisode
            },
            state.episodesAll.firstOrNull {
                it.season == state.episodeStreamsSeason && it.episode == state.episodeStreamsEpisode
            }
        ).firstOrNull { it != null }

        if (targetVideo != null) {
            loadStreamsForEpisode(video = targetVideo, forceRefresh = true)
        }
    }

    private fun switchToEpisodeStream(stream: Stream) {
        val url = stream.getStreamUrl()
        if (url.isNullOrBlank()) {
            _uiState.update { it.copy(episodeStreamsError = "Invalid stream URL") }
            return
        }

        emitStopScrobbleForCurrentProgress()
        saveWatchProgress()

        val newHeaders = stream.behaviorHints?.proxyHeaders?.request ?: emptyMap()
        val targetVideo = _uiState.value.episodes.firstOrNull { it.id == _uiState.value.episodeStreamsForVideoId }

        currentStreamUrl = url
        currentHeaders = newHeaders
        currentVideoId = targetVideo?.id ?: _uiState.value.episodeStreamsForVideoId ?: currentVideoId
        currentSeason = targetVideo?.season ?: _uiState.value.episodeStreamsSeason ?: currentSeason
        currentEpisode = targetVideo?.episode ?: _uiState.value.episodeStreamsEpisode ?: currentEpisode
        currentEpisodeTitle = targetVideo?.title ?: _uiState.value.episodeStreamsTitle ?: currentEpisodeTitle
        refreshScrobbleItem()

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
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                selectedAudioTrackIndex = -1,
                selectedSubtitleTrackIndex = -1,
                showEpisodesPanel = false,
                showEpisodeStreams = false,
                isLoadingEpisodeStreams = false,
                episodeStreamsError = null,
                
                parentalWarnings = emptyList(),
                showParentalGuide = false,
                parentalGuideHasShown = false,
                
                activeSkipInterval = null,
                skipIntervalDismissed = false
            )
        }

        updateEpisodeDescription()
        refreshSubtitlesForCurrentEpisode()

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
                startFrameRateProbe(url, newHeaders, _uiState.value.frameRateMatchingEnabled)
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
                                frameRateProbeJob?.cancel()
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

        hasScannedTextTracksOnce = true
        Log.d(
            TAG,
            "TRACKS updated: internalSubs=${subtitleTracks.size}, selectedInternalIndex=$selectedSubtitleIndex, " +
                "selectedAddon=${_uiState.value.selectedAddonSubtitle?.lang}, pendingAddonLang=$pendingAddonSubtitleLanguage"
        )

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
        }

        maybeApplyRememberedAudioSelection(audioTracks)

        _uiState.update {
            it.copy(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                selectedAudioTrackIndex = selectedAudioIndex,
                selectedSubtitleTrackIndex = selectedSubtitleIndex
            )
        }
        tryAutoSelectPreferredSubtitleFromAvailableTracks()
    }

    private fun maybeApplyRememberedAudioSelection(audioTracks: List<TrackInfo>) {
        if (hasAppliedRememberedAudioSelection) return
        if (!streamReuseLastLinkEnabled) return
        if (audioTracks.isEmpty()) return
        if (rememberedAudioLanguage.isNullOrBlank() && rememberedAudioName.isNullOrBlank()) return

        fun normalize(value: String?): String? = value
            ?.lowercase()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val targetLang = normalize(rememberedAudioLanguage)
        val targetName = normalize(rememberedAudioName)

        val index = audioTracks.indexOfFirst { track ->
            val trackLang = normalize(track.language)
            val trackName = normalize(track.name)
            val langMatch = !targetLang.isNullOrBlank() &&
                !trackLang.isNullOrBlank() &&
                (trackLang == targetLang || trackLang.startsWith("$targetLang-"))
            val nameMatch = !targetName.isNullOrBlank() &&
                !trackName.isNullOrBlank() &&
                (trackName == targetName || trackName.contains(targetName))
            langMatch || nameMatch
        }
        if (index < 0) {
            hasAppliedRememberedAudioSelection = true
            return
        }

        selectAudioTrack(index)
        hasAppliedRememberedAudioSelection = true
    }

    private fun subtitleLanguageTargets(): List<String> {
        val preferred = _uiState.value.subtitleStyle.preferredLanguage.lowercase()
        if (preferred == "none") return emptyList()
        val secondary = _uiState.value.subtitleStyle.secondaryPreferredLanguage?.lowercase()
        return listOfNotNull(preferred, secondary)
    }

    private fun matchesLanguageCode(language: String?, target: String): Boolean {
        if (language.isNullOrBlank()) return false
        val normalizedLanguage = normalizeLanguageCode(language)
        val normalizedTarget = normalizeLanguageCode(target)
        return normalizedLanguage == normalizedTarget ||
            normalizedLanguage.startsWith("$normalizedTarget-") ||
            normalizedLanguage.startsWith("${normalizedTarget}_")
    }

    private fun tryAutoSelectPreferredSubtitleFromAvailableTracks() {
        if (autoSubtitleSelected) return

        val state = _uiState.value
        val targets = subtitleLanguageTargets()
        Log.d(
            TAG,
            "AUTO_SUB eval: targets=$targets, scannedText=$hasScannedTextTracksOnce, " +
                "internalCount=${state.subtitleTracks.size}, selectedInternal=${state.selectedSubtitleTrackIndex}, " +
                "addonCount=${state.addonSubtitles.size}, selectedAddon=${state.selectedAddonSubtitle?.lang}"
        )
        if (targets.isEmpty()) {
            autoSubtitleSelected = true
            Log.d(TAG, "AUTO_SUB stop: preferred=none")
            return
        }

        val internalIndex = state.subtitleTracks.indexOfFirst { track ->
            targets.any { target -> matchesLanguageCode(track.language, target) }
        }
        if (internalIndex >= 0) {
            autoSubtitleSelected = true
            val currentInternal = state.selectedSubtitleTrackIndex
            val currentAddon = state.selectedAddonSubtitle
            if (currentInternal != internalIndex || currentAddon != null) {
                Log.d(TAG, "AUTO_SUB pick internal index=$internalIndex lang=${state.subtitleTracks[internalIndex].language}")
                selectSubtitleTrack(internalIndex)
                _uiState.update { it.copy(selectedSubtitleTrackIndex = internalIndex, selectedAddonSubtitle = null) }
            } else {
                Log.d(TAG, "AUTO_SUB stop: preferred internal already selected")
            }
            return
        }

        val selectedAddonMatchesTarget = state.selectedAddonSubtitle != null &&
            targets.any { target -> matchesLanguageCode(state.selectedAddonSubtitle.lang, target) }
        if (selectedAddonMatchesTarget) {
            autoSubtitleSelected = true
            Log.d(TAG, "AUTO_SUB stop: matching addon already selected (no internal match)")
            return
        }

        // Wait until we have at least one full text-track scan to avoid choosing addon too early.
        if (!hasScannedTextTracksOnce) {
            Log.d(TAG, "AUTO_SUB defer addon fallback: text tracks not scanned yet")
            return
        }

        val playerReady = _exoPlayer?.playbackState == Player.STATE_READY
        if (!playerReady) {
            Log.d(TAG, "AUTO_SUB defer addon fallback: player not ready")
            return
        }

        val addonMatch = state.addonSubtitles.firstOrNull { subtitle ->
            targets.any { target -> matchesLanguageCode(subtitle.lang, target) }
        }
        if (addonMatch != null) {
            autoSubtitleSelected = true
            Log.d(TAG, "AUTO_SUB pick addon lang=${addonMatch.lang} id=${addonMatch.id}")
            selectAddonSubtitle(addonMatch)
        } else {
            Log.d(TAG, "AUTO_SUB no addon match for targets=$targets")
        }
    }

    private fun startFrameRateProbe(
        url: String,
        headers: Map<String, String>,
        frameRateMatchingEnabled: Boolean
    ) {
        frameRateProbeJob?.cancel()
        _uiState.update { it.copy(detectedFrameRate = 0f) }
        if (!frameRateMatchingEnabled) return

        val token = ++frameRateProbeToken
        frameRateProbeJob = viewModelScope.launch(Dispatchers.IO) {
            val detected = FrameRateUtils.detectFrameRateFromSource(context, url, headers)
            if (!isActive || detected <= 0f) return@launch
            withContext(Dispatchers.Main) {
                if (token == frameRateProbeToken && _uiState.value.detectedFrameRate <= 0f) {
                    _uiState.update { it.copy(detectedFrameRate = detected) }
                }
            }
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
                    val displayPosition = pendingPreviewSeekPosition ?: pos
                    _uiState.update {
                        it.copy(
                            currentPosition = displayPosition,
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
        
        
        if (kotlin.math.abs(currentPosition - lastSavedPosition) >= saveThresholdMs) {
            lastSavedPosition = currentPosition
            saveWatchProgressInternal(currentPosition, duration)
        }
    }

    private fun saveWatchProgress() {
        val currentPosition = _exoPlayer?.currentPosition ?: return
        val duration = getEffectiveDuration(currentPosition)
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
        
        if (position < 1000) return

        val fallbackPercent = if (duration <= 0L) 5f else null

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
            lastWatched = System.currentTimeMillis(),
            progressPercent = fallbackPercent
        )

        viewModelScope.launch {
            watchProgressRepository.saveProgress(progress)
        }
    }

    private fun currentPlaybackProgressPercent(): Float {
        val player = _exoPlayer ?: return 0f
        val duration = player.duration.takeIf { it > 0 } ?: lastKnownDuration
        if (duration <= 0L) return 0f
        return ((player.currentPosition.toFloat() / duration.toFloat()) * 100f).coerceIn(0f, 100f)
    }

    private fun refreshScrobbleItem() {
        currentScrobbleItem = buildScrobbleItem()
        hasSentScrobbleStartForCurrentItem = false
        hasSentCompletionScrobbleForCurrentItem = false
    }

    private fun buildScrobbleItem(): TraktScrobbleItem? {
        val rawContentId = contentId ?: return null
        val parsedIds = parseContentIds(rawContentId)
        val ids = toTraktIds(parsedIds)
        val parsedYear = extractYear(year)
        val normalizedType = contentType?.lowercase()

        val isEpisode = normalizedType in listOf("series", "tv") &&
            currentSeason != null && currentEpisode != null

        return if (isEpisode) {
            TraktScrobbleItem.Episode(
                showTitle = contentName ?: title,
                showYear = parsedYear,
                showIds = ids,
                season = currentSeason ?: return null,
                number = currentEpisode ?: return null,
                episodeTitle = currentEpisodeTitle
            )
        } else {
            TraktScrobbleItem.Movie(
                title = contentName ?: title,
                year = parsedYear,
                ids = ids
            )
        }
    }

    private fun emitScrobbleStart() {
        val item = currentScrobbleItem ?: buildScrobbleItem().also { currentScrobbleItem = it } ?: return
        if (hasSentScrobbleStartForCurrentItem) return

        viewModelScope.launch {
            traktScrobbleService.scrobbleStart(
                item = item,
                progressPercent = currentPlaybackProgressPercent()
            )
            hasSentScrobbleStartForCurrentItem = true
        }
    }

    private fun emitScrobbleStop(progressPercent: Float? = null) {
        val item = currentScrobbleItem ?: return
        if (!hasSentScrobbleStartForCurrentItem && (progressPercent ?: 0f) < 80f) return

        val percent = progressPercent ?: currentPlaybackProgressPercent()
        viewModelScope.launch {
            traktScrobbleService.scrobbleStop(
                item = item,
                progressPercent = percent
            )
        }
        hasSentScrobbleStartForCurrentItem = false
    }

    private fun emitPauseScrobbleStop(progressPercent: Float) {
        if (progressPercent < 1f || progressPercent >= 80f) return
        val item = currentScrobbleItem ?: return
        if (!hasSentScrobbleStartForCurrentItem) return

        viewModelScope.launch {
            traktScrobbleService.scrobblePause(
                item = item,
                progressPercent = progressPercent
            )
        }
        hasSentScrobbleStartForCurrentItem = false
    }

    private fun emitCompletionScrobbleStop(progressPercent: Float) {
        if (progressPercent < 80f || hasSentCompletionScrobbleForCurrentItem) return
        hasSentCompletionScrobbleForCurrentItem = true
        emitScrobbleStop(progressPercent = progressPercent)
    }

    private fun emitStopScrobbleForCurrentProgress() {
        val progressPercent = currentPlaybackProgressPercent()
        emitPauseScrobbleStop(progressPercent = progressPercent)
        emitCompletionScrobbleStop(progressPercent = progressPercent)
    }

    fun scheduleHideControls() {
        hideControlsJob?.cancel()
        hideControlsJob = viewModelScope.launch {
            delay(3000)
            if (_uiState.value.isPlaying && !_uiState.value.showAudioDialog &&
                !_uiState.value.showSubtitleDialog && !_uiState.value.showSubtitleStylePanel &&
                !_uiState.value.showSpeedDialog &&
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
                onEvent(PlayerEvent.OnSeekBy(deltaMs = 10_000L))
            }
            PlayerEvent.OnSeekBackward -> {
                onEvent(PlayerEvent.OnSeekBy(deltaMs = -10_000L))
            }
            is PlayerEvent.OnSeekBy -> {
                pendingPreviewSeekPosition = null
                _exoPlayer?.let { player ->
                    val maxDuration = player.duration.takeIf { it >= 0 } ?: Long.MAX_VALUE
                    val target = (player.currentPosition + event.deltaMs)
                        .coerceAtLeast(0L)
                        .coerceAtMost(maxDuration)
                    player.seekTo(target)
                    _uiState.update { it.copy(currentPosition = target) }
                }
                if (_uiState.value.showControls) {
                    showControlsTemporarily()
                } else {
                    showSeekOverlayTemporarily()
                }
            }
            is PlayerEvent.OnPreviewSeekBy -> {
                _exoPlayer?.let { player ->
                    val maxDuration = player.duration.takeIf { it >= 0 } ?: Long.MAX_VALUE
                    val basePosition = pendingPreviewSeekPosition ?: player.currentPosition.coerceAtLeast(0L)
                    val target = (basePosition + event.deltaMs)
                        .coerceAtLeast(0L)
                        .coerceAtMost(maxDuration)
                    pendingPreviewSeekPosition = target
                    _uiState.update { it.copy(currentPosition = target) }
                }
                if (_uiState.value.showControls) {
                    showControlsTemporarily()
                } else {
                    showSeekOverlayTemporarily()
                }
            }
            PlayerEvent.OnCommitPreviewSeek -> {
                val target = pendingPreviewSeekPosition
                if (target != null) {
                    _exoPlayer?.seekTo(target)
                    _uiState.update { it.copy(currentPosition = target) }
                    pendingPreviewSeekPosition = null
                    if (_uiState.value.showControls) {
                        showControlsTemporarily()
                    } else {
                        showSeekOverlayTemporarily()
                    }
                }
            }
            is PlayerEvent.OnSeekTo -> {
                pendingPreviewSeekPosition = null
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
                        showSubtitleStylePanel = false,
                        selectedAddonSubtitle = null 
                    ) 
                }
            }
            PlayerEvent.OnDisableSubtitles -> {
                disableSubtitles()
                _uiState.update { 
                    it.copy(
                        showSubtitleDialog = false,
                        showSubtitleStylePanel = false,
                        selectedAddonSubtitle = null,
                        selectedSubtitleTrackIndex = -1
                    ) 
                }
            }
            is PlayerEvent.OnSelectAddonSubtitle -> {
                selectAddonSubtitle(event.subtitle)
                _uiState.update { it.copy(showSubtitleDialog = false, showSubtitleStylePanel = false) }
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
                _uiState.update { it.copy(showAudioDialog = true, showSubtitleStylePanel = false, showControls = true) }
            }
            PlayerEvent.OnShowSubtitleDialog -> {
                _uiState.update { it.copy(showSubtitleDialog = true, showSubtitleStylePanel = false, showControls = true) }
            }
            PlayerEvent.OnOpenSubtitleStylePanel -> {
                _uiState.update {
                    it.copy(
                        showSubtitleDialog = false,
                        showSubtitleStylePanel = true,
                        showControls = true
                    )
                }
            }
            PlayerEvent.OnDismissSubtitleStylePanel -> {
                _uiState.update { it.copy(showSubtitleStylePanel = false) }
                scheduleHideControls()
            }
            PlayerEvent.OnShowSpeedDialog -> {
                _uiState.update { it.copy(showSpeedDialog = true, showSubtitleStylePanel = false, showControls = true) }
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
                        isLoadingEpisodeStreams = false
                    )
                }
            }
            is PlayerEvent.OnEpisodeSeasonSelected -> {
                selectEpisodesSeason(event.season)
            }
            is PlayerEvent.OnEpisodeSelected -> {
                loadStreamsForEpisode(event.video)
            }
            PlayerEvent.OnReloadEpisodeStreams -> {
                reloadEpisodeStreams()
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
            PlayerEvent.OnReloadSourceStreams -> {
                loadSourceStreams(forceRefresh = true)
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
                        showSubtitleStylePanel = false,
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
                val newMode = nextResizeMode(currentMode)
                val modeText = resizeModeLabel(newMode)
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

    private fun nextResizeMode(currentMode: Int): Int {
        return when (currentMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun resizeModeLabel(mode: Int): String {
        return when (mode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit (Original)"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> "Fit Width"
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT -> "Fit Height"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop"
            else -> "Fit (Original)"
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
                            persistRememberedLinkAudioSelection(trackIndex)
                            return
                        }
                        currentAudioIndex++
                    }
                }
            }
        }
    }

    private fun persistRememberedLinkAudioSelection(trackIndex: Int) {
        if (!streamReuseLastLinkEnabled) return

        val key = streamCacheKey ?: return
        val url = currentStreamUrl.takeIf { it.isNotBlank() } ?: return
        val streamName = _uiState.value.currentStreamName?.takeIf { it.isNotBlank() } ?: title
        val selectedTrack = _uiState.value.audioTracks.getOrNull(trackIndex)

        viewModelScope.launch {
            streamLinkCacheDataStore.save(
                contentKey = key,
                url = url,
                streamName = streamName,
                headers = currentHeaders,
                rememberedAudioLanguage = selectedTrack?.language,
                rememberedAudioName = selectedTrack?.name
            )
        }
    }

    private fun selectSubtitleTrack(trackIndex: Int) {
        _exoPlayer?.let { player ->
            Log.d(TAG, "Selecting INTERNAL subtitle trackIndex=$trackIndex")
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
            Log.d(TAG, "Selecting ADDON subtitle lang=${subtitle.lang} id=${subtitle.id}")

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
            "pt-br", "pt_br", "br", "pob" -> "pt"
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
        
        val progressPercent = currentPlaybackProgressPercent()
        emitPauseScrobbleStop(progressPercent = progressPercent)
        emitCompletionScrobbleStop(progressPercent = progressPercent)
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
        frameRateProbeJob?.cancel()
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
