package com.nuvio.tv.ui.screens.immersive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.repository.AddonRepository
import com.nuvio.tv.domain.repository.CatalogRepository
import com.nuvio.tv.domain.repository.MetaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

@HiltViewModel
class ImmersiveViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val catalogRepository: CatalogRepository,
    private val metaRepository: MetaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImmersiveUiState())
    val uiState: StateFlow<ImmersiveUiState> = _uiState.asStateFlow()

    private val _metadataState = MutableStateFlow(MetadataPopupState())
    val metadataState: StateFlow<MetadataPopupState> = _metadataState.asStateFlow()

    private val catalogsMap = linkedMapOf<String, CatalogRow>()
    private val catalogOrder = mutableListOf<String>()
    private var addonsCache: List<Addon> = emptyList()
    private val catalogLoadSemaphore = Semaphore(6)

    private var metadataTimerJob: Job? = null
    private var currentFocusedItem: MetaPreview? = null

    init {
        observeInstalledAddons()
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
        _uiState.update { it.copy(isLoading = true, error = null) }
        catalogOrder.clear()
        catalogsMap.clear()

        if (addons.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, error = "No addons installed") }
            return
        }

        addons.forEach { addon ->
            addon.catalogs
                .filterNot { it.isSearchOnlyCatalog() }
                .forEach { catalog ->
                    val key = catalogKey(addon.id, catalog.type.toApiString(), catalog.id)
                    if (key !in catalogOrder) {
                        catalogOrder.add(key)
                    }
                }
        }

        if (catalogOrder.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, error = "No catalog addons installed") }
            return
        }

        val jobs = mutableListOf<Job>()
        addons.forEach { addon ->
            addon.catalogs
                .filterNot { it.isSearchOnlyCatalog() }
                .forEach { catalog ->
                    val job = viewModelScope.launch {
                        catalogLoadSemaphore.withPermit {
                            loadCatalog(addon, catalog)
                        }
                    }
                    jobs.add(job)
                }
        }

        // Wait for at least the first catalog to arrive, then mark not loading
        jobs.firstOrNull()?.join()
        _uiState.update { it.copy(isLoading = false) }
    }

    private suspend fun loadCatalog(addon: Addon, catalog: CatalogDescriptor) {
        catalogRepository.getCatalog(
            addonBaseUrl = addon.baseUrl,
            addonId = addon.id,
            addonName = addon.name,
            catalogId = catalog.id,
            catalogName = catalog.name,
            type = catalog.type.toApiString(),
            supportsSkip = catalog.extra.any { it.name == "skip" }
        ).collect { result ->
            when (result) {
                is NetworkResult.Success -> {
                    val key = catalogKey(addon.id, catalog.type.toApiString(), catalog.id)
                    catalogsMap[key] = result.data
                    updateCatalogRows()
                }
                is NetworkResult.Error -> {
                    // Skip failed catalogs silently
                }
                is NetworkResult.Loading -> {
                    // Ignore loading state
                }
            }
        }
    }

    private fun updateCatalogRows() {
        val rows = catalogOrder.mapNotNull { key ->
            catalogsMap[key]?.takeIf { it.items.isNotEmpty() }
        }
        _uiState.update { it.copy(catalogRows = rows, isLoading = false) }
    }

    fun onFocusChanged(item: MetaPreview?) {
        if (item == null || item.id == currentFocusedItem?.id) return
        currentFocusedItem = item

        // Dismiss metadata immediately on focus change
        _metadataState.update { MetadataPopupState() }

        // Start timer for metadata
        metadataTimerJob?.cancel()
        metadataTimerJob = viewModelScope.launch {
            delay(2500)

            // Show immediately available info
            _metadataState.update {
                MetadataPopupState(
                    visible = true,
                    title = item.name,
                    description = item.description?.takeIf { it.isNotBlank() },
                    isLoadingDescription = item.description.isNullOrBlank()
                )
            }

            // If description is missing, fetch full Meta
            if (item.description.isNullOrBlank()) {
                val result = metaRepository.getMetaFromAllAddons(
                    type = item.type.toApiString(),
                    id = item.id
                ).first { it !is NetworkResult.Loading }

                val meta = (result as? NetworkResult.Success)?.data
                // Only update if still focused on same item
                if (currentFocusedItem?.id == item.id) {
                    _metadataState.update {
                        it.copy(
                            description = meta?.description ?: "No description available.",
                            isLoadingDescription = false
                        )
                    }
                }
            }
        }
    }

    fun onItemClicked(item: MetaPreview): Triple<String, String, String> {
        val catalogRow = _uiState.value.catalogRows.firstOrNull { row ->
            row.items.any { it.id == item.id }
        }
        return Triple(item.id, item.type.toApiString(), catalogRow?.addonBaseUrl ?: "")
    }

    fun retry() {
        viewModelScope.launch {
            loadAllCatalogs(addonsCache)
        }
    }

    private fun catalogKey(addonId: String, type: String, catalogId: String): String {
        return "${addonId}_${type}_${catalogId}"
    }

    private fun CatalogDescriptor.isSearchOnlyCatalog(): Boolean {
        return extra.any { extra -> extra.name == "search" && extra.isRequired }
    }
}
