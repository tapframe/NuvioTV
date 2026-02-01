package com.nuvio.tv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.LibraryPreferences
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.SavedLibraryItem
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.repository.MetaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val libraryPreferences: LibraryPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: String = savedStateHandle["itemId"] ?: ""
    private val itemType: String = savedStateHandle["itemType"] ?: ""
    private val preferredAddonBaseUrl: String? = savedStateHandle["addonBaseUrl"]

    private val _uiState = MutableStateFlow(MetaDetailsUiState())
    val uiState: StateFlow<MetaDetailsUiState> = _uiState.asStateFlow()

    init {
        observeLibraryState()
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
        }
    }

    private fun observeLibraryState() {
        viewModelScope.launch {
            libraryPreferences.isInLibrary(itemId = itemId, itemType = itemType)
                .collectLatest { inLibrary ->
                    _uiState.update { it.copy(isInLibrary = inLibrary) }
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

        val enrichment = tmdbMetadataService.fetchEnrichment(tmdbId, meta.type)

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
            updated = updated.copy(description = enrichment.description ?: updated.description)
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
            val episodeMap = tmdbMetadataService.fetchEpisodeEnrichment(tmdbId, seasonNumbers)
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

    private fun toggleLibrary() {
        val meta = _uiState.value.meta ?: return
        viewModelScope.launch {
            if (_uiState.value.isInLibrary) {
                libraryPreferences.removeItem(itemId = meta.id, itemType = meta.type.toApiString())
            } else {
                libraryPreferences.addItem(meta.toSavedLibraryItem(preferredAddonBaseUrl))
            }
        }
    }

    private fun Meta.toSavedLibraryItem(addonBaseUrl: String?): SavedLibraryItem {
        return SavedLibraryItem(
            id = id,
            type = type.toApiString(),
            name = name,
            poster = poster,
            posterShape = posterShape,
            background = background,
            description = description,
            releaseInfo = releaseInfo,
            imdbRating = imdbRating,
            genres = genres,
            addonBaseUrl = addonBaseUrl
        )
    }

    fun getNextEpisodeInfo(): String? {
        val meta = _uiState.value.meta ?: return null
        val episodes = _uiState.value.episodesForSeason
        // For now, return the first episode info
        return episodes.firstOrNull()?.let { video ->
            "S${video.season}, E${video.episode}"
        }
    }
}
