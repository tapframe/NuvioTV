package com.nuvio.tv.ui.screens.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbMetadataService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.TmdbSettingsDataStore
import com.nuvio.tv.data.trailer.TrailerService
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.domain.repository.MetaRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val watchProgressRepository: WatchProgressRepository,
    private val metaRepository: MetaRepository,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val tmdbSettingsDataStore: TmdbSettingsDataStore,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    private val trailerService: TrailerService
) : ViewModel() {
    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _focusState = MutableStateFlow(HomeScreenFocusState())
    val focusState: StateFlow<HomeScreenFocusState> = _focusState.asStateFlow()

    private val _gridFocusState = MutableStateFlow(HomeScreenFocusState())
    val gridFocusState: StateFlow<HomeScreenFocusState> = _gridFocusState.asStateFlow()

    private val _loadingCatalogs = MutableStateFlow<Set<String>>(emptySet())
    val loadingCatalogs: StateFlow<Set<String>> = _loadingCatalogs.asStateFlow()

    private val catalogsMap = linkedMapOf<String, CatalogRow>()
    private val catalogOrder = mutableListOf<String>()
    private var addonsCache: List<Addon> = emptyList()
    private var homeCatalogOrderKeys: List<String> = emptyList()
    private var disabledHomeCatalogKeys: Set<String> = emptySet()
    private var currentHeroCatalogKey: String? = null
    private var catalogUpdateJob: Job? = null
    private val catalogLoadSemaphore = Semaphore(6)
    private val trailerPreviewLoadingIds = mutableSetOf<String>()
    private val trailerPreviewNegativeCache = mutableSetOf<String>()
    private var activeTrailerPreviewItemId: String? = null
    private var trailerPreviewRequestVersion: Long = 0L

    init {
        loadLayoutPreference()
        loadHeroCatalogPreference()
        loadHeroSectionPreference()
        loadPosterLabelPreference()
        loadCatalogAddonNamePreference()
        loadFocusedPosterBackdropExpandPreference()
        loadFocusedPosterBackdropExpandDelayPreference()
        loadFocusedPosterBackdropTrailerPreference()
        loadFocusedPosterBackdropTrailerMutedPreference()
        loadHomeCatalogOrderPreference()
        loadDisabledHomeCatalogPreference()
        loadPosterCardStylePreferences()
        observeTmdbSettings()
        loadContinueWatching()
        observeInstalledAddons()
    }

    private fun loadLayoutPreference() {
        viewModelScope.launch {
            layoutPreferenceDataStore.selectedLayout.collectLatest { layout ->
                _uiState.update { it.copy(homeLayout = layout) }
            }
        }
    }

    private fun loadHeroCatalogPreference() {
        viewModelScope.launch {
            layoutPreferenceDataStore.heroCatalogSelection.collectLatest { key ->
                currentHeroCatalogKey = key
                _uiState.update { it.copy(heroCatalogKey = key) }
                scheduleUpdateCatalogRows()
            }
        }
    }

    private fun loadHeroSectionPreference() {
        viewModelScope.launch {
            layoutPreferenceDataStore.heroSectionEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(heroSectionEnabled = enabled) }
                scheduleUpdateCatalogRows()
            }
        }
    }

    private fun loadPosterLabelPreference() {
        viewModelScope.launch {
            layoutPreferenceDataStore.posterLabelsEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(posterLabelsEnabled = enabled) }
            }
        }
    }

    private fun loadCatalogAddonNamePreference() {
        viewModelScope.launch {
            layoutPreferenceDataStore.catalogAddonNameEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(catalogAddonNameEnabled = enabled) }
            }
        }
    }

    private fun loadFocusedPosterBackdropExpandPreference() {
        viewModelScope.launch {
            layoutPreferenceDataStore.focusedPosterBackdropExpandEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(focusedPosterBackdropExpandEnabled = enabled) }
            }
        }
    }

    private fun loadFocusedPosterBackdropExpandDelayPreference() {
        viewModelScope.launch {
            layoutPreferenceDataStore.focusedPosterBackdropExpandDelaySeconds.collectLatest { seconds ->
                _uiState.update { it.copy(focusedPosterBackdropExpandDelaySeconds = seconds) }
            }
        }
    }

    private fun loadFocusedPosterBackdropTrailerPreference() {
        viewModelScope.launch {
            layoutPreferenceDataStore.focusedPosterBackdropTrailerEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(focusedPosterBackdropTrailerEnabled = enabled) }
            }
        }
    }

    private fun loadFocusedPosterBackdropTrailerMutedPreference() {
        viewModelScope.launch {
            layoutPreferenceDataStore.focusedPosterBackdropTrailerMuted.collectLatest { muted ->
                _uiState.update { it.copy(focusedPosterBackdropTrailerMuted = muted) }
            }
        }
    }

    fun requestTrailerPreview(item: MetaPreview) {
        val itemId = item.id
        if (activeTrailerPreviewItemId != itemId) {
            activeTrailerPreviewItemId = itemId
            trailerPreviewRequestVersion++
        }

        if (trailerPreviewNegativeCache.contains(itemId)) return
        if (_uiState.value.trailerPreviewUrls.containsKey(itemId)) return
        if (!trailerPreviewLoadingIds.add(itemId)) return

        val requestVersion = trailerPreviewRequestVersion

        viewModelScope.launch {
            val trailerUrl = trailerService.getTrailerUrl(
                title = item.name,
                year = extractYear(item.releaseInfo),
                tmdbId = null,
                type = item.apiType
            )

            val isLatestFocusedItem =
                activeTrailerPreviewItemId == itemId && trailerPreviewRequestVersion == requestVersion
            if (!isLatestFocusedItem) {
                trailerPreviewLoadingIds.remove(itemId)
                return@launch
            }

            if (trailerUrl.isNullOrBlank()) {
                trailerPreviewNegativeCache.add(itemId)
            } else {
                _uiState.update { state ->
                    if (state.trailerPreviewUrls[itemId] == trailerUrl) state
                    else state.copy(
                        trailerPreviewUrls = state.trailerPreviewUrls + (itemId to trailerUrl)
                    )
                }
            }

            trailerPreviewLoadingIds.remove(itemId)
        }
    }

    private fun loadHomeCatalogOrderPreference() {
        viewModelScope.launch {
            layoutPreferenceDataStore.homeCatalogOrderKeys.collectLatest { keys ->
                homeCatalogOrderKeys = keys
                rebuildCatalogOrder(addonsCache)
                scheduleUpdateCatalogRows()
            }
        }
    }

    private fun loadDisabledHomeCatalogPreference() {
        viewModelScope.launch {
            layoutPreferenceDataStore.disabledHomeCatalogKeys.collectLatest { keys ->
                disabledHomeCatalogKeys = keys.toSet()
                rebuildCatalogOrder(addonsCache)
                if (addonsCache.isNotEmpty()) {
                    loadAllCatalogs(addonsCache)
                } else {
                    scheduleUpdateCatalogRows()
                }
            }
        }
    }

    private fun loadPosterCardStylePreferences() {
        viewModelScope.launch {
            layoutPreferenceDataStore.posterCardWidthDp.collectLatest { widthDp ->
                _uiState.update { it.copy(posterCardWidthDp = widthDp) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.posterCardHeightDp.collectLatest { heightDp ->
                _uiState.update { it.copy(posterCardHeightDp = heightDp) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.posterCardCornerRadiusDp.collectLatest { cornerRadiusDp ->
                _uiState.update { it.copy(posterCardCornerRadiusDp = cornerRadiusDp) }
            }
        }
    }

    private fun observeTmdbSettings() {
        viewModelScope.launch {
            tmdbSettingsDataStore.settings
                .distinctUntilChanged()
                .collectLatest {
                    scheduleUpdateCatalogRows()
                }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.OnItemClick -> navigateToDetail(event.itemId, event.itemType)
            is HomeEvent.OnLoadMoreCatalog -> loadMoreCatalogItems(event.catalogId, event.addonId, event.type)
            is HomeEvent.OnRemoveContinueWatching -> removeContinueWatching(event.contentId, event.season, event.episode)
            HomeEvent.OnRetry -> viewModelScope.launch { loadAllCatalogs(addonsCache) }
        }
    }

    private fun loadContinueWatching() {
        viewModelScope.launch {
            watchProgressRepository.allProgress.collectLatest { items ->
                val inProgressOnly = deduplicateInProgress(
                    items.filter { it.isInProgress() }
                ).map { ContinueWatchingItem.InProgress(it) }

                // Optimistic immediate render: show in-progress entries instantly.
                _uiState.update { it.copy(continueWatchingItems = inProgressOnly) }

                // Then compute NextUp enrichment (may need remote meta lookups).
                val entries = buildContinueWatchingItems(items)
                _uiState.update { it.copy(continueWatchingItems = entries) }
            }
        }
    }

    private fun deduplicateInProgress(items: List<WatchProgress>): List<WatchProgress> {
        val (series, nonSeries) = items.partition { isSeriesType(it.contentType) }
        val latestPerShow = series
            .sortedByDescending { it.lastWatched }
            .distinctBy { it.contentId }
        return (nonSeries + latestPerShow).sortedByDescending { it.lastWatched }
    }

    private suspend fun buildContinueWatchingItems(
        allProgress: List<WatchProgress>
    ): List<ContinueWatchingItem> = withContext(Dispatchers.IO) {
        val inProgressItems = deduplicateInProgress(
            allProgress.filter { it.isInProgress() }
        ).map { ContinueWatchingItem.InProgress(it) }

        val inProgressIds = inProgressItems.map { it.progress.contentId }.toSet()

        val latestCompletedBySeries = allProgress
            .filter { progress ->
                isSeriesType(progress.contentType) &&
                    progress.season != null &&
                    progress.episode != null &&
                    progress.season != 0 &&
                    progress.isCompleted()
            }
            .groupBy { it.contentId }
            .mapNotNull { (_, items) -> items.maxByOrNull { it.lastWatched } }
            .filter { it.contentId !in inProgressIds }

        val nextUpItems = latestCompletedBySeries.mapNotNull { progress ->
            val nextEpisode = findNextEpisode(progress) ?: return@mapNotNull null
            val meta = nextEpisode.first
            val video = nextEpisode.second
            val info = NextUpInfo(
                contentId = progress.contentId,
                contentType = progress.contentType,
                name = meta.name,
                poster = meta.poster,
                backdrop = meta.background,
                logo = meta.logo,
                videoId = video.id,
                season = video.season ?: return@mapNotNull null,
                episode = video.episode ?: return@mapNotNull null,
                episodeTitle = video.title,
                thumbnail = video.thumbnail,
                lastWatched = progress.lastWatched
            )
            ContinueWatchingItem.NextUp(info)
        }

        val combined = mutableListOf<Pair<Long, ContinueWatchingItem>>()
        inProgressItems.forEach { combined.add(it.progress.lastWatched to it) }
        nextUpItems.forEach { combined.add(it.info.lastWatched to it) }

        combined
            .sortedByDescending { it.first }
            .map { it.second }
    }

    private suspend fun findNextEpisode(progress: WatchProgress): Pair<Meta, Video>? {
        if (!isSeriesType(progress.contentType)) return null

        val idCandidates = buildList {
            add(progress.contentId)
            if (progress.contentId.startsWith("tmdb:")) add(progress.contentId.substringAfter(':'))
            if (progress.contentId.startsWith("trakt:")) add(progress.contentId.substringAfter(':'))
        }.distinct()

        val meta = run {
            var resolved: Meta? = null
            val typeCandidates = listOf(progress.contentType, "series", "tv").distinct()
            for (type in typeCandidates) {
                for (candidateId in idCandidates) {
                    val result = withTimeoutOrNull(2500) {
                        metaRepository.getMetaFromAllAddons(
                            type = type,
                            id = candidateId
                        ).first { it !is NetworkResult.Loading }
                    } ?: continue
                    resolved = (result as? NetworkResult.Success)?.data
                    if (resolved != null) break
                }
                if (resolved != null) break
            }
            resolved
        } ?: return null

        val episodes = meta.videos
            .filter { it.season != null && it.episode != null && it.season != 0 }
            .sortedWith(compareBy<Video> { it.season }.thenBy { it.episode })

        val currentIndex = episodes.indexOfFirst {
            it.season == progress.season && it.episode == progress.episode
        }

        if (currentIndex == -1 || currentIndex + 1 >= episodes.size) return null

        return meta to episodes[currentIndex + 1]
    }

    private fun isSeriesType(type: String?): Boolean {
        return type == "series" || type == "tv"
    }

    private fun removeContinueWatching(contentId: String, season: Int? = null, episode: Int? = null) {
        viewModelScope.launch {
            Log.d(
                TAG,
                "removeContinueWatching requested contentId=$contentId season=$season episode=$episode; removing all progress for content"
            )
            watchProgressRepository.removeProgress(contentId = contentId, season = null, episode = null)
        }
    }

    private fun observeInstalledAddons() {
        viewModelScope.launch {
            addonRepository.getInstalledAddons()
                .distinctUntilChanged { old, new ->
                    old.map { it.id } == new.map { it.id }
                }
                .collectLatest { addons ->
                    addonsCache = addons
                    loadAllCatalogs(addons)
                }
        }
    }

    private suspend fun loadAllCatalogs(addons: List<Addon>) {
        _uiState.update { it.copy(isLoading = true, error = null, installedAddonsCount = addons.size) }
        catalogOrder.clear()
        catalogsMap.clear()

        try {
            if (addons.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, error = "No addons installed") }
                return
            }

            rebuildCatalogOrder(addons)

            if (catalogOrder.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, error = "No catalog addons installed") }
                return
            }

            // Load catalogs
            addons.forEach { addon ->
                addon.catalogs
                    .filterNot {
                        it.isSearchOnlyCatalog() || isCatalogDisabled(
                            addonBaseUrl = addon.baseUrl,
                            addonId = addon.id,
                            type = it.apiType,
                            catalogId = it.id,
                            catalogName = it.name
                        )
                    }
                    .forEach { catalog ->
                        loadCatalog(addon, catalog)
                    }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    private fun loadCatalog(addon: Addon, catalog: CatalogDescriptor) {
        viewModelScope.launch {
            catalogLoadSemaphore.withPermit {
                val supportsSkip = catalog.extra.any { it.name == "skip" }
                catalogRepository.getCatalog(
                    addonBaseUrl = addon.baseUrl,
                    addonId = addon.id,
                    addonName = addon.name,
                    catalogId = catalog.id,
                    catalogName = catalog.name,
                    type = catalog.apiType,
                    skip = 0,
                    supportsSkip = supportsSkip
                ).collect { result ->
                    when (result) {
                        is NetworkResult.Success -> {
                            val key = catalogKey(
                                addonId = addon.id,
                                type = catalog.apiType,
                                catalogId = catalog.id
                            )
                            catalogsMap[key] = result.data
                            scheduleUpdateCatalogRows()
                        }
                        is NetworkResult.Error -> {
                            // Log error but don't fail entire screen
                        }
                        NetworkResult.Loading -> { /* Handled by individual row */ }
                    }
                }
            }
        }
    }

    private fun loadMoreCatalogItems(catalogId: String, addonId: String, type: String) {
        val key = catalogKey(addonId = addonId, type = type, catalogId = catalogId)
        val currentRow = catalogsMap[key] ?: return

        if (currentRow.isLoading || !currentRow.hasMore) return
        if (key in _loadingCatalogs.value) return

        // Mark loading via lightweight separate flow — avoids full state cascade
        catalogsMap[key] = currentRow.copy(isLoading = true)
        _loadingCatalogs.update { it + key }

        viewModelScope.launch {
            val addon = addonsCache.find { it.id == addonId } ?: return@launch

            // Use actual loaded item count for skip, not fixed 100-page size
            val nextSkip = currentRow.items.size
            catalogRepository.getCatalog(
                addonBaseUrl = addon.baseUrl,
                addonId = addon.id,
                addonName = addon.name,
                catalogId = catalogId,
                catalogName = currentRow.catalogName,
                type = currentRow.apiType,
                skip = nextSkip,
                supportsSkip = currentRow.supportsSkip
            ).collect { result ->
                when (result) {
                    is NetworkResult.Success -> {
                        val mergedItems = currentRow.items + result.data.items
                        catalogsMap[key] = result.data.copy(items = mergedItems)
                        _loadingCatalogs.update { it - key }
                        updateCatalogRows()
                    }
                    is NetworkResult.Error -> {
                        catalogsMap[key] = currentRow.copy(isLoading = false)
                        _loadingCatalogs.update { it - key }
                        updateCatalogRows()
                    }
                    NetworkResult.Loading -> { }
                }
            }
        }
    }

    private fun scheduleUpdateCatalogRows() {
        catalogUpdateJob?.cancel()
        catalogUpdateJob = viewModelScope.launch {
            delay(150)
            updateCatalogRows()
        }
    }

    private suspend fun updateCatalogRows() {
        // Snapshot mutable state before entering background context
        val orderedKeys = catalogOrder.toList()
        val catalogSnapshot = catalogsMap.toMap()
        val heroCatalogKey = currentHeroCatalogKey
        val currentLayout = _uiState.value.homeLayout
        val currentGridItems = _uiState.value.gridItems
        val heroSectionEnabled = _uiState.value.heroSectionEnabled

        val (displayRows, baseHeroItems, baseGridItems) = withContext(Dispatchers.Default) {
            val orderedRows = orderedKeys.mapNotNull { key -> catalogSnapshot[key] }

            val heroSourceRow = if (heroCatalogKey != null) {
                catalogSnapshot[heroCatalogKey]
            } else {
                orderedRows.firstOrNull { row -> row.items.any { it.hasHeroArtwork() } }
            }

            val heroItemsFromSelectedCatalog = heroSourceRow
                ?.items
                .orEmpty()
                .filter { it.hasHeroArtwork() }

            val fallbackHeroItemsWithArtwork = orderedRows
                .asSequence()
                .flatMap { it.items.asSequence() }
                .filter { it.hasHeroArtwork() }
                .take(7)
                .toList()

            val computedHeroItems = when {
                heroItemsFromSelectedCatalog.isNotEmpty() -> heroItemsFromSelectedCatalog.take(7)
                fallbackHeroItemsWithArtwork.isNotEmpty() -> fallbackHeroItemsWithArtwork
                else -> orderedRows.flatMap { it.items }.take(7)
            }

            val computedDisplayRows = orderedRows.map { row ->
                if (row.items.size > 25) row.copy(items = row.items.take(25)) else row
            }

            val computedGridItems = if (currentLayout == HomeLayout.GRID) {
                buildList {
                    if (heroSectionEnabled && computedHeroItems.isNotEmpty()) {
                        add(GridItem.Hero(computedHeroItems))
                    }
                    computedDisplayRows.filter { it.items.isNotEmpty() }.forEach { row ->
                        add(
                            GridItem.SectionDivider(
                                catalogName = row.catalogName,
                                catalogId = row.catalogId,
                                addonBaseUrl = row.addonBaseUrl,
                                addonId = row.addonId,
                                type = row.apiType
                            )
                        )
                        val hasEnoughForSeeAll = row.items.size >= 15
                        val displayItems = if (hasEnoughForSeeAll) row.items.take(14) else row.items.take(15)
                        displayItems.forEach { item ->
                            add(
                                GridItem.Content(
                                    item = item,
                                    addonBaseUrl = row.addonBaseUrl,
                                    catalogId = row.catalogId,
                                    catalogName = row.catalogName
                                )
                            )
                        }
                        if (hasEnoughForSeeAll) {
                            add(
                                GridItem.SeeAll(
                                    catalogId = row.catalogId,
                                    addonId = row.addonId,
                                    type = row.apiType
                                )
                            )
                        }
                    }
                }
            } else {
                currentGridItems
            }

            Triple(computedDisplayRows, computedHeroItems, computedGridItems)
        }

        val heroItems = enrichHeroItems(baseHeroItems)
        val gridItems = if (currentLayout == HomeLayout.GRID) {
            replaceGridHeroItems(baseGridItems, heroItems)
        } else {
            baseGridItems
        }

        // Full (untruncated) rows for CatalogSeeAllScreen
        val fullRows = orderedKeys.mapNotNull { key -> catalogSnapshot[key] }

        val currentState = _uiState.value
        if (
            currentState.catalogRows == displayRows &&
            currentState.fullCatalogRows == fullRows &&
            currentState.heroItems == heroItems &&
            currentState.gridItems == gridItems &&
            !currentState.isLoading
        ) {
            return
        }

        _uiState.value = currentState.copy(
            catalogRows = displayRows,
            fullCatalogRows = fullRows,
            heroItems = heroItems,
            gridItems = gridItems,
            isLoading = false
        )
    }

    private fun navigateToDetail(itemId: String, itemType: String) {
        _uiState.update { it.copy(selectedItemId = itemId) }
    }

    private suspend fun enrichHeroItems(items: List<MetaPreview>): List<MetaPreview> {
        if (items.isEmpty()) return items

        val settings = tmdbSettingsDataStore.settings.first()
        if (!settings.enabled) return items
        if (!settings.useArtwork && !settings.useBasicInfo && !settings.useDetails) return items

        return items.map { item ->
            val tmdbId = tmdbService.ensureTmdbId(item.id, item.apiType) ?: return@map item
            val enrichment = tmdbMetadataService.fetchEnrichment(
                tmdbId = tmdbId,
                contentType = item.type,
                language = settings.language
            ) ?: return@map item

            var enriched = item

            if (settings.useArtwork) {
                enriched = enriched.copy(
                    background = enrichment.backdrop ?: enriched.background,
                    logo = enrichment.logo ?: enriched.logo,
                    poster = enrichment.poster ?: enriched.poster
                )
            }

            if (settings.useBasicInfo) {
                enriched = enriched.copy(
                    name = enrichment.localizedTitle ?: enriched.name,
                    description = enrichment.description ?: enriched.description,
                    genres = if (enrichment.genres.isNotEmpty()) enrichment.genres else enriched.genres,
                    imdbRating = enrichment.rating?.toFloat() ?: enriched.imdbRating
                )
            }

            if (settings.useDetails) {
                enriched = enriched.copy(
                    releaseInfo = enrichment.releaseInfo ?: enriched.releaseInfo
                )
            }

            enriched
        }
    }

    private fun replaceGridHeroItems(
        gridItems: List<GridItem>,
        heroItems: List<MetaPreview>
    ): List<GridItem> {
        if (gridItems.isEmpty()) return gridItems
        return gridItems.map { item ->
            if (item is GridItem.Hero) {
                item.copy(items = heroItems)
            } else {
                item
            }
        }
    }

    private fun catalogKey(addonId: String, type: String, catalogId: String): String {
        return "${addonId}_${type}_${catalogId}"
    }

    private fun rebuildCatalogOrder(addons: List<Addon>) {
        val defaultOrder = buildDefaultCatalogOrder(addons)
        val availableSet = defaultOrder.toSet()

        val savedValid = homeCatalogOrderKeys
            .asSequence()
            .filter { it in availableSet }
            .distinct()
            .toList()

        val savedSet = savedValid.toSet()
        val mergedOrder = savedValid + defaultOrder.filterNot { it in savedSet }

        catalogOrder.clear()
        catalogOrder.addAll(mergedOrder)
    }

    private fun buildDefaultCatalogOrder(addons: List<Addon>): List<String> {
        val orderedKeys = mutableListOf<String>()
        addons.forEach { addon ->
            addon.catalogs
                .filterNot {
                    it.isSearchOnlyCatalog() || isCatalogDisabled(
                        addonBaseUrl = addon.baseUrl,
                        addonId = addon.id,
                        type = it.apiType,
                        catalogId = it.id,
                        catalogName = it.name
                    )
                }
                .forEach { catalog ->
                    val key = catalogKey(
                        addonId = addon.id,
                        type = catalog.apiType,
                        catalogId = catalog.id
                    )
                    if (key !in orderedKeys) {
                        orderedKeys.add(key)
                    }
                }
        }
        return orderedKeys
    }

    private fun isCatalogDisabled(
        addonBaseUrl: String,
        addonId: String,
        type: String,
        catalogId: String,
        catalogName: String
    ): Boolean {
        if (disableCatalogKey(addonBaseUrl, type, catalogId, catalogName) in disabledHomeCatalogKeys) {
            return true
        }
        // Backward compatibility with previously stored keys.
        return catalogKey(addonId, type, catalogId) in disabledHomeCatalogKeys
    }

    private fun disableCatalogKey(
        addonBaseUrl: String,
        type: String,
        catalogId: String,
        catalogName: String
    ): String {
        return "${addonBaseUrl}_${type}_${catalogId}_${catalogName}"
    }

    private fun CatalogDescriptor.isSearchOnlyCatalog(): Boolean {
        return extra.any { extra -> extra.name == "search" && extra.isRequired }
    }

    private fun MetaPreview.hasHeroArtwork(): Boolean {
        return !background.isNullOrBlank() || !poster.isNullOrBlank()
    }

    private fun extractYear(releaseInfo: String?): String? {
        if (releaseInfo.isNullOrBlank()) return null
        return Regex("\\b(19|20)\\d{2}\\b").find(releaseInfo)?.value
    }

    /**
     * Saves the current focus and scroll state for restoration when returning to this screen.
     */
    fun saveFocusState(
        verticalScrollIndex: Int,
        verticalScrollOffset: Int,
        focusedRowIndex: Int,
        focusedItemIndex: Int,
        catalogRowScrollStates: Map<String, Int>
    ) {
        _focusState.value = HomeScreenFocusState(
            verticalScrollIndex = verticalScrollIndex,
            verticalScrollOffset = verticalScrollOffset,
            focusedRowIndex = focusedRowIndex,
            focusedItemIndex = focusedItemIndex,
            catalogRowScrollStates = catalogRowScrollStates,
            hasSavedFocus = true
        )
    }

    /**
     * Clears the saved focus state.
     */
    fun clearFocusState() {
        _focusState.value = HomeScreenFocusState()
    }

    /**
     * Saves the grid layout focus and scroll state.
     */
    fun saveGridFocusState(
        verticalScrollIndex: Int,
        verticalScrollOffset: Int,
        focusedRowIndex: Int = 0,
        focusedItemIndex: Int = 0
    ) {
        _gridFocusState.value = HomeScreenFocusState(
            verticalScrollIndex = verticalScrollIndex,
            verticalScrollOffset = verticalScrollOffset,
            focusedRowIndex = focusedRowIndex,
            focusedItemIndex = focusedItemIndex
        )
    }
}
