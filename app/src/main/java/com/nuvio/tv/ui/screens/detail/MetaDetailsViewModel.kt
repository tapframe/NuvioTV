package com.nuvio.tv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.repository.parseContentIds
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.ListMembershipChanges
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.NextToWatch
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import com.nuvio.tv.data.local.TrailerSettingsDataStore
import com.nuvio.tv.data.trailer.TrailerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MetaDetailsViewModel @Inject constructor(
    private val metaRepository: MetaRepository,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    private val libraryRepository: LibraryRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val trailerService: TrailerService,
    private val trailerSettingsDataStore: TrailerSettingsDataStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val itemId: String = savedStateHandle["itemId"] ?: ""
    private val itemType: String = savedStateHandle["itemType"] ?: ""
    private val preferredAddonBaseUrl: String? = savedStateHandle["addonBaseUrl"]

    private val _uiState = MutableStateFlow(MetaDetailsUiState())
    val uiState: StateFlow<MetaDetailsUiState> = _uiState.asStateFlow()

    private var idleTimerJob: Job? = null
    private var trailerFetchJob: Job? = null

    private var trailerDelayMs = 7000L

    private var isPlayButtonFocused = false

    init {
        observeLibraryState()
        observeWatchProgress()
        observeMovieWatched()
        loadMeta()
    }

    fun onEvent(event: MetaDetailsEvent) {
        when (event) {
            is MetaDetailsEvent.OnSeasonSelected -> selectSeason(event.season)
            is MetaDetailsEvent.OnEpisodeClick -> { /* Navigate to stream */ }
            MetaDetailsEvent.OnPlayClick -> { /* Start playback */ }
            MetaDetailsEvent.OnToggleLibrary -> toggleLibrary()
            MetaDetailsEvent.OnRetry -> loadMeta()
            MetaDetailsEvent.OnBackPress -> { /* Handle in screen */ }
            MetaDetailsEvent.OnUserInteraction -> handleUserInteraction()
            MetaDetailsEvent.OnPlayButtonFocused -> handlePlayButtonFocused()
            MetaDetailsEvent.OnTrailerEnded -> handleTrailerEnded()
            MetaDetailsEvent.OnToggleMovieWatched -> toggleMovieWatched()
            is MetaDetailsEvent.OnToggleEpisodeWatched -> toggleEpisodeWatched(event.video)
            MetaDetailsEvent.OnLibraryLongPress -> openListPicker()
            is MetaDetailsEvent.OnPickerMembershipToggled -> togglePickerMembership(event.listKey)
            MetaDetailsEvent.OnPickerSave -> savePickerMembership()
            MetaDetailsEvent.OnPickerDismiss -> dismissListPicker()
            MetaDetailsEvent.OnClearMessage -> clearMessage()
        }
    }

    private fun observeLibraryState() {
        viewModelScope.launch {
            libraryRepository.sourceMode
                .collectLatest { sourceMode ->
                    _uiState.update { it.copy(librarySourceMode = sourceMode) }
                }
        }

        viewModelScope.launch {
            libraryRepository.listTabs.collectLatest { tabs ->
                _uiState.update { state ->
                    val selectedMembership = state.pickerMembership
                    val filteredMembership = if (selectedMembership.isEmpty()) {
                        selectedMembership
                    } else {
                        tabs.associate { tab -> tab.key to (selectedMembership[tab.key] == true) }
                    }
                    state.copy(
                        libraryListTabs = tabs,
                        pickerMembership = filteredMembership
                    )
                }
            }
        }

        viewModelScope.launch {
            libraryRepository.isInLibrary(itemId = itemId, itemType = itemType)
                .collectLatest { inLibrary ->
                    _uiState.update { it.copy(isInLibrary = inLibrary) }
                }
        }

        viewModelScope.launch {
            libraryRepository.isInWatchlist(itemId = itemId, itemType = itemType)
                .collectLatest { inWatchlist ->
                    _uiState.update { it.copy(isInWatchlist = inWatchlist) }
                }
        }
    }

    private fun observeWatchProgress() {
        if (itemType.lowercase() == "movie") return
        viewModelScope.launch {
            watchProgressRepository.getAllEpisodeProgress(itemId).collectLatest { progressMap ->
                _uiState.update { it.copy(episodeProgressMap = progressMap) }
                // Recalculate next to watch when progress changes
                calculateNextToWatch()
            }
        }
    }

    private fun observeMovieWatched() {
        if (itemType.lowercase() != "movie") return
        viewModelScope.launch {
            watchProgressRepository.isWatched(itemId).collectLatest { watched ->
                _uiState.update { it.copy(isMovieWatched = watched) }
            }
        }
    }

    private fun loadMeta() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // 1) Prefer meta from the originating addon (same catalog source)
            val preferred = preferredAddonBaseUrl?.takeIf { it.isNotBlank() }
            val preferredMeta: Meta? = preferred?.let { baseUrl ->
                when (val result = metaRepository.getMeta(addonBaseUrl = baseUrl, type = itemType, id = itemId)
                    .first { it !is NetworkResult.Loading }) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Error -> null
                    NetworkResult.Loading -> null
                }
            }

            if (preferredMeta != null) {
                applyMetaWithEnrichment(preferredMeta)
                return@launch
            }

            // 2) Fallback: first addon that can provide meta (often Cinemeta)
            metaRepository.getMetaFromAllAddons(type = itemType, id = itemId).collect { result ->
                when (result) {
                    is NetworkResult.Success -> applyMetaWithEnrichment(result.data)
                    is NetworkResult.Error -> {
                        _uiState.update { it.copy(isLoading = false, error = result.message) }
                    }
                    NetworkResult.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
        }
    }

    private fun applyMeta(meta: Meta) {
        val seasons = meta.videos
            .mapNotNull { it.season }
            .distinct()
            .sorted()

        // Prefer first regular season (> 0), fallback to season 0 (specials)
        val selectedSeason = seasons.firstOrNull { it > 0 } ?: seasons.firstOrNull() ?: 1
        val episodesForSeason = getEpisodesForSeason(meta.videos, selectedSeason)

        _uiState.update {
            it.copy(
                isLoading = false,
                meta = meta,
                seasons = seasons,
                selectedSeason = selectedSeason,
                episodesForSeason = episodesForSeason,
                error = null
            )
        }
        
        // Calculate next to watch after meta is loaded
        calculateNextToWatch()

        // Start fetching trailer after meta is loaded
        fetchTrailerUrl()
    }

    private suspend fun applyMetaWithEnrichment(meta: Meta) {
        val enriched = enrichMeta(meta)
        applyMeta(enriched)
    }

    private suspend fun enrichMeta(meta: Meta): Meta {
        val settings = tmdbSettingsDataStore.settings.first()
        if (!settings.enabled) return meta

        val tmdbId = tmdbService.ensureTmdbId(meta.id, meta.type.toApiString())
            ?: tmdbService.ensureTmdbId(itemId, itemType)
            ?: return meta

        val enrichment = tmdbMetadataService.fetchEnrichment(
            tmdbId = tmdbId,
            contentType = meta.type,
            language = settings.language
        )

        var updated = meta

        // Group: Artwork (logo, backdrop)
        if (enrichment != null && settings.useArtwork) {
            updated = updated.copy(
                background = enrichment.backdrop ?: updated.background,
                logo = enrichment.logo ?: updated.logo
            )
        }

        // Group: Basic Info (description, genres, rating)
        if (enrichment != null && settings.useBasicInfo) {
            updated = updated.copy(
                name = enrichment.localizedTitle ?: updated.name,
                description = enrichment.description ?: updated.description
            )
            if (enrichment.genres.isNotEmpty()) {
                updated = updated.copy(genres = enrichment.genres)
            }
            updated = updated.copy(imdbRating = enrichment.rating?.toFloat() ?: updated.imdbRating)
        }

        // Group: Details (runtime, release info, country, language)
        if (enrichment != null && settings.useDetails) {
            updated = updated.copy(
                runtime = enrichment.runtimeMinutes?.toString() ?: updated.runtime,
                releaseInfo = enrichment.releaseInfo ?: updated.releaseInfo,
                country = enrichment.countries?.joinToString(", ") ?: updated.country,
                language = enrichment.language ?: updated.language
            )
        }

        // Group: Credits (cast with photos, director, writer)
        if (enrichment != null && settings.useCredits) {
            if (enrichment.castMembers.isNotEmpty()) {
                updated = updated.copy(
                    castMembers = enrichment.castMembers,
                    cast = enrichment.castMembers.map { it.name }
                )
            }
            updated = updated.copy(
                director = if (enrichment.director.isNotEmpty()) enrichment.director else updated.director,
                writer = if (enrichment.writer.isNotEmpty()) enrichment.writer else updated.writer
            )
        }

        // Group: Productions
        if (enrichment != null && settings.useProductions && enrichment.productionCompanies.isNotEmpty()) {
            updated = updated.copy(productionCompanies = enrichment.productionCompanies)
        }

        // Group: Networks
        if (enrichment != null && settings.useNetworks && enrichment.networks.isNotEmpty()) {
            updated = updated.copy(networks = enrichment.networks)
        }

        // Group: Episodes (titles, overviews, thumbnails, runtime)
        if (settings.useEpisodes && meta.type.toApiString() in listOf("series", "tv")) {
            val seasonNumbers = meta.videos.mapNotNull { it.season }.distinct()
            val episodeMap = tmdbMetadataService.fetchEpisodeEnrichment(
                tmdbId = tmdbId,
                seasonNumbers = seasonNumbers,
                language = settings.language
            )
            if (episodeMap.isNotEmpty()) {
                updated = updated.copy(
                    videos = meta.videos.map { video ->
                        val season = video.season
                        val episode = video.episode
                        val key = if (season != null && episode != null) season to episode else null
                        val ep = key?.let { episodeMap[it] }

                        video.copy(
                            title = ep?.title ?: video.title,
                            overview = ep?.overview ?: video.overview,
                            released = ep?.airDate ?: video.released,
                            thumbnail = ep?.thumbnail ?: video.thumbnail,
                            runtime = ep?.runtimeMinutes
                        )
                    }
                )
            }
        }

        return updated
    }

    private fun selectSeason(season: Int) {
        val episodes = _uiState.value.meta?.videos?.let { getEpisodesForSeason(it, season) } ?: emptyList()
        _uiState.update {
            it.copy(
                selectedSeason = season,
                episodesForSeason = episodes
            )
        }
    }

    private fun getEpisodesForSeason(videos: List<Video>, season: Int): List<Video> {
        return videos
            .filter { it.season == season }
            .sortedBy { it.episode }
    }

    private fun calculateNextToWatch() {
        val meta = _uiState.value.meta ?: return
        val progressMap = _uiState.value.episodeProgressMap
        val isSeries = meta.type.toApiString() in listOf("series", "tv")

        if (!isSeries) {
            // For movies, check if there's an in-progress watch
            viewModelScope.launch {
                val progress = watchProgressRepository.getProgress(itemId).first()
                val nextToWatch = if (progress != null && shouldResumeProgress(progress)) {
                    NextToWatch(
                        watchProgress = progress,
                        isResume = true,
                        nextVideoId = meta.id,
                        nextSeason = null,
                        nextEpisode = null,
                        displayText = "Resume"
                    )
                } else {
                    NextToWatch(
                        watchProgress = null,
                        isResume = false,
                        nextVideoId = meta.id,
                        nextSeason = null,
                        nextEpisode = null,
                        displayText = "Play"
                    )
                }
                _uiState.update { it.copy(nextToWatch = nextToWatch) }
            }
            return
        }

        viewModelScope.launch {
            val allEpisodes = meta.videos
                .filter { it.season != null && it.episode != null }
                .sortedWith(compareBy({ it.season }, { it.episode }))

            if (allEpisodes.isEmpty()) {
                _uiState.update {
                    it.copy(nextToWatch = NextToWatch(
                        watchProgress = null,
                        isResume = false,
                        nextVideoId = meta.id,
                        nextSeason = null,
                        nextEpisode = null,
                        displayText = "Play"
                    ))
                }
                return@launch
            }

            val nonSpecialEpisodes = allEpisodes.filter { (it.season ?: 0) > 0 }
            val episodePool = if (nonSpecialEpisodes.isNotEmpty()) nonSpecialEpisodes else allEpisodes
            val latestSeriesProgress = watchProgressRepository.getProgress(itemId).first()

            val nextToWatch = buildNextToWatchFromLatestProgress(
                latestProgress = latestSeriesProgress,
                episodes = episodePool,
                fallbackProgressMap = progressMap,
                metaId = meta.id
            )

            _uiState.update { it.copy(nextToWatch = nextToWatch) }
        }
    }

    private fun buildNextToWatchFromLatestProgress(
        latestProgress: WatchProgress?,
        episodes: List<Video>,
        fallbackProgressMap: Map<Pair<Int, Int>, WatchProgress>,
        metaId: String
    ): NextToWatch {
        if (episodes.isEmpty()) {
            return NextToWatch(
                watchProgress = null,
                isResume = false,
                nextVideoId = metaId,
                nextSeason = null,
                nextEpisode = null,
                displayText = "Play"
            )
        }

        if (latestProgress?.season != null && latestProgress.episode != null) {
            val season = latestProgress.season
            val episode = latestProgress.episode
            val matchedIndex = episodes.indexOfFirst { it.season == season && it.episode == episode }

            if (shouldResumeProgress(latestProgress)) {
                val matchedEpisode = if (matchedIndex >= 0) episodes[matchedIndex] else null
                return NextToWatch(
                    watchProgress = latestProgress,
                    isResume = true,
                    nextVideoId = matchedEpisode?.id ?: latestProgress.videoId,
                    nextSeason = season,
                    nextEpisode = episode,
                    displayText = "Resume S${season}E${episode}"
                )
            }

            if (latestProgress.isCompleted() && matchedIndex >= 0) {
                val next = episodes.getOrNull(matchedIndex + 1)
                if (next != null) {
                    return NextToWatch(
                        watchProgress = null,
                        isResume = false,
                        nextVideoId = next.id,
                        nextSeason = next.season,
                        nextEpisode = next.episode,
                        displayText = "Next S${next.season}E${next.episode}"
                    )
                }
            }
        }

        var resumeEpisode: Video? = null
        var resumeProgress: WatchProgress? = null
        var nextUnwatchedEpisode: Video? = null

        for (episode in episodes) {
            val season = episode.season ?: continue
            val ep = episode.episode ?: continue
            val progress = fallbackProgressMap[season to ep]

            if (progress != null) {
                if (shouldResumeProgress(progress)) {
                    resumeEpisode = episode
                    resumeProgress = progress
                    break
                } else if (progress.isCompleted()) {
                    continue
                }
            } else {
                if (nextUnwatchedEpisode == null) {
                    nextUnwatchedEpisode = episode
                }
                if (resumeEpisode == null) {
                    break
                }
            }
        }

        return when {
            resumeEpisode != null && resumeProgress != null -> {
                NextToWatch(
                    watchProgress = resumeProgress,
                    isResume = true,
                    nextVideoId = resumeEpisode.id,
                    nextSeason = resumeEpisode.season,
                    nextEpisode = resumeEpisode.episode,
                    displayText = "Resume S${resumeEpisode.season}E${resumeEpisode.episode}"
                )
            }
            nextUnwatchedEpisode != null -> {
                val hasWatchedSomething = fallbackProgressMap.isNotEmpty()
                val displayPrefix = if (hasWatchedSomething) "Next" else "Play"
                NextToWatch(
                    watchProgress = null,
                    isResume = false,
                    nextVideoId = nextUnwatchedEpisode.id,
                    nextSeason = nextUnwatchedEpisode.season,
                    nextEpisode = nextUnwatchedEpisode.episode,
                    displayText = "$displayPrefix S${nextUnwatchedEpisode.season}E${nextUnwatchedEpisode.episode}"
                )
            }
            else -> {
                val firstEpisode = episodes.firstOrNull()
                NextToWatch(
                    watchProgress = null,
                    isResume = false,
                    nextVideoId = firstEpisode?.id ?: metaId,
                    nextSeason = firstEpisode?.season,
                    nextEpisode = firstEpisode?.episode,
                    displayText = if (firstEpisode != null) {
                        "Play S${firstEpisode.season}E${firstEpisode.episode}"
                    } else {
                        "Play"
                    }
                )
            }
        }
    }

    private fun shouldResumeProgress(progress: WatchProgress): Boolean {
        return progress.progressPercentage >= 0.02f && !progress.isCompleted()
    }

    private fun toggleLibrary() {
        val meta = _uiState.value.meta ?: return
        viewModelScope.launch {
            val input = meta.toLibraryEntryInput()
            val wasInWatchlist = _uiState.value.isInWatchlist
            val wasInLibrary = _uiState.value.isInLibrary
            runCatching {
                libraryRepository.toggleDefault(input)
                val message = if (_uiState.value.librarySourceMode == LibrarySourceMode.TRAKT) {
                    if (wasInWatchlist) "Removed from watchlist" else "Added to watchlist"
                } else {
                    if (wasInLibrary) "Removed from library" else "Added to library"
                }
                showMessage(message)
            }.onFailure { error ->
                showMessage(
                    message = error.message ?: "Failed to update library",
                    isError = true
                )
            }
        }
    }

    private fun openListPicker() {
        if (_uiState.value.librarySourceMode != LibrarySourceMode.TRAKT) return
        val meta = _uiState.value.meta ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(pickerPending = true, pickerError = null) }
            runCatching {
                val snapshot = libraryRepository.getMembershipSnapshot(meta.toLibraryEntryInput())
                _uiState.update {
                    it.copy(
                        showListPicker = true,
                        pickerMembership = snapshot.listMembership,
                        pickerPending = false,
                        pickerError = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        pickerPending = false,
                        pickerError = error.message ?: "Failed to load lists",
                        showListPicker = false
                    )
                }
                showMessage(error.message ?: "Failed to load lists", isError = true)
            }
        }
    }

    private fun togglePickerMembership(listKey: String) {
        val current = _uiState.value.pickerMembership[listKey] == true
        _uiState.update {
            it.copy(
                pickerMembership = it.pickerMembership.toMutableMap().apply {
                    this[listKey] = !current
                },
                pickerError = null
            )
        }
    }

    private fun savePickerMembership() {
        if (_uiState.value.pickerPending) return
        if (_uiState.value.librarySourceMode != LibrarySourceMode.TRAKT) return
        val meta = _uiState.value.meta ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(pickerPending = true, pickerError = null) }
            runCatching {
                libraryRepository.applyMembershipChanges(
                    item = meta.toLibraryEntryInput(),
                    changes = ListMembershipChanges(
                        desiredMembership = _uiState.value.pickerMembership
                    )
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        pickerPending = false,
                        showListPicker = false,
                        pickerError = null
                    )
                }
                showMessage("Lists updated")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        pickerPending = false,
                        pickerError = error.message ?: "Failed to update lists"
                    )
                }
                showMessage(error.message ?: "Failed to update lists", isError = true)
            }
        }
    }

    private fun dismissListPicker() {
        _uiState.update {
            it.copy(
                showListPicker = false,
                pickerPending = false,
                pickerError = null
            )
        }
    }

    private fun toggleMovieWatched() {
        val meta = _uiState.value.meta ?: return
        if (meta.type.toApiString() != "movie") return
        if (_uiState.value.isMovieWatchedPending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isMovieWatchedPending = true) }
            runCatching {
                if (_uiState.value.isMovieWatched) {
                    watchProgressRepository.removeFromHistory(itemId)
                    showMessage("Marked as unwatched")
                } else {
                    watchProgressRepository.markAsCompleted(buildCompletedMovieProgress(meta))
                    showMessage("Marked as watched")
                }
            }.onFailure { error ->
                showMessage(
                    message = error.message ?: "Failed to update watched status",
                    isError = true
                )
            }
            _uiState.update { it.copy(isMovieWatchedPending = false) }
        }
    }

    private fun toggleEpisodeWatched(video: Video) {
        val meta = _uiState.value.meta ?: return
        val season = video.season ?: return
        val episode = video.episode ?: return
        val pendingKey = episodePendingKey(video)
        if (_uiState.value.episodeWatchedPendingKeys.contains(pendingKey)) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys + pendingKey)
            }

            val isWatched = _uiState.value.episodeProgressMap[season to episode]?.isCompleted() == true
            runCatching {
                if (isWatched) {
                    watchProgressRepository.removeFromHistory(itemId, season, episode)
                    showMessage("Episode marked as unwatched")
                } else {
                    watchProgressRepository.markAsCompleted(buildCompletedEpisodeProgress(meta, video))
                    showMessage("Episode marked as watched")
                }
            }.onFailure { error ->
                showMessage(
                    message = error.message ?: "Failed to update episode watched status",
                    isError = true
                )
            }

            _uiState.update {
                it.copy(episodeWatchedPendingKeys = it.episodeWatchedPendingKeys - pendingKey)
            }
        }
    }

    private fun buildCompletedMovieProgress(meta: Meta): WatchProgress {
        return WatchProgress(
            contentId = itemId,
            contentType = meta.type.toApiString(),
            name = meta.name,
            poster = meta.poster,
            backdrop = meta.background,
            logo = meta.logo,
            videoId = meta.id,
            season = null,
            episode = null,
            episodeTitle = null,
            position = 1L,
            duration = 1L,
            lastWatched = System.currentTimeMillis(),
            progressPercent = 100f
        )
    }

    private fun buildCompletedEpisodeProgress(meta: Meta, video: Video): WatchProgress {
        val runtimeMs = video.runtime?.toLong()?.times(60_000L) ?: 1L
        return WatchProgress(
            contentId = itemId,
            contentType = meta.type.toApiString(),
            name = meta.name,
            poster = meta.poster,
            backdrop = video.thumbnail ?: meta.background,
            logo = meta.logo,
            videoId = video.id,
            season = video.season,
            episode = video.episode,
            episodeTitle = video.title,
            position = runtimeMs,
            duration = runtimeMs,
            lastWatched = System.currentTimeMillis(),
            progressPercent = 100f
        )
    }

    private fun episodePendingKey(video: Video): String {
        return "${video.id}:${video.season ?: -1}:${video.episode ?: -1}"
    }

    private fun showMessage(message: String, isError: Boolean = false) {
        _uiState.update {
            it.copy(
                userMessage = message,
                userMessageIsError = isError
            )
        }
    }

    private fun clearMessage() {
        _uiState.update { it.copy(userMessage = null, userMessageIsError = false) }
    }

    private fun Meta.toLibraryEntryInput(): LibraryEntryInput {
        val year = Regex("(\\d{4})").find(releaseInfo ?: "")
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val parsedIds = parseContentIds(id)
        return LibraryEntryInput(
            itemId = id,
            itemType = type.toApiString(),
            title = name,
            year = year,
            traktId = parsedIds.trakt,
            imdbId = parsedIds.imdb,
            tmdbId = parsedIds.tmdb,
            poster = poster,
            posterShape = posterShape,
            background = background,
            logo = logo,
            description = description,
            releaseInfo = releaseInfo,
            imdbRating = imdbRating,
            genres = genres,
            addonBaseUrl = preferredAddonBaseUrl
        )
    }

    fun getNextEpisodeInfo(): String? {
        val nextToWatch = _uiState.value.nextToWatch
        return nextToWatch?.displayText
    }

    // --- Trailer ---

    private fun fetchTrailerUrl() {
        val meta = _uiState.value.meta ?: return

        trailerFetchJob?.cancel()
        trailerFetchJob = viewModelScope.launch {
            // Check if trailers are enabled in settings
            val settings = trailerSettingsDataStore.settings.first()
            if (!settings.enabled) return@launch

            trailerDelayMs = settings.delaySeconds * 1000L

            _uiState.update { it.copy(isTrailerLoading = true) }

            val year = meta.releaseInfo?.split("-")?.firstOrNull()

            val tmdbId = try {
                tmdbService.ensureTmdbId(meta.id, meta.type.toApiString())
            } catch (_: Exception) {
                null
            }

            val type = when (meta.type) {
                com.nuvio.tv.domain.model.ContentType.MOVIE -> "movie"
                com.nuvio.tv.domain.model.ContentType.SERIES,
                com.nuvio.tv.domain.model.ContentType.TV -> "tv"
                else -> null
            }

            val url = trailerService.getTrailerUrl(
                title = meta.name,
                year = year,
                tmdbId = tmdbId,
                type = type
            )

            _uiState.update { it.copy(trailerUrl = url, isTrailerLoading = false) }

            if (url != null && isPlayButtonFocused) {
                startIdleTimer()
            }
        }
    }

    private fun startIdleTimer() {
        idleTimerJob?.cancel()

        val state = _uiState.value
        if (state.trailerUrl == null || state.isTrailerPlaying) return
        if (!isPlayButtonFocused) return

        idleTimerJob = viewModelScope.launch {
            delay(trailerDelayMs)
            _uiState.update { it.copy(isTrailerPlaying = true) }
        }
    }

    private fun handlePlayButtonFocused() {
        isPlayButtonFocused = true
        startIdleTimer()
    }

    private fun handleUserInteraction() {
        idleTimerJob?.cancel()
        isPlayButtonFocused = false

        if (_uiState.value.isTrailerPlaying) {
            _uiState.update { it.copy(isTrailerPlaying = false) }
        }
    }

    private fun handleTrailerEnded() {
        isPlayButtonFocused = false
        _uiState.update { it.copy(isTrailerPlaying = false) }
    }

    override fun onCleared() {
        super.onCleared()
        idleTimerJob?.cancel()
        trailerFetchJob?.cancel()
    }
}
