package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LayoutSettingsUiState(
    val selectedLayout: HomeLayout = HomeLayout.CLASSIC,
    val hasChosen: Boolean = false,
    val availableCatalogs: List<CatalogInfo> = emptyList(),
    val heroCatalogKey: String? = null,
    val sidebarCollapsedByDefault: Boolean = false,
    val heroSectionEnabled: Boolean = true,
    val searchDiscoverEnabled: Boolean = true,
    val posterLabelsEnabled: Boolean = true,
    val catalogAddonNameEnabled: Boolean = true,
    val focusedPosterBackdropExpandEnabled: Boolean = false,
    val posterCardWidthDp: Int = 126,
    val posterCardHeightDp: Int = 189,
    val posterCardCornerRadiusDp: Int = 12
)

data class CatalogInfo(
    val key: String,
    val name: String,
    val addonName: String
)

sealed class LayoutSettingsEvent {
    data class SelectLayout(val layout: HomeLayout) : LayoutSettingsEvent()
    data class SelectHeroCatalog(val catalogKey: String) : LayoutSettingsEvent()
    data class SetSidebarCollapsed(val collapsed: Boolean) : LayoutSettingsEvent()
    data class SetHeroSectionEnabled(val enabled: Boolean) : LayoutSettingsEvent()
    data class SetSearchDiscoverEnabled(val enabled: Boolean) : LayoutSettingsEvent()
    data class SetPosterLabelsEnabled(val enabled: Boolean) : LayoutSettingsEvent()
    data class SetCatalogAddonNameEnabled(val enabled: Boolean) : LayoutSettingsEvent()
    data class SetFocusedPosterBackdropExpandEnabled(val enabled: Boolean) : LayoutSettingsEvent()
    data class SetPosterCardWidth(val widthDp: Int) : LayoutSettingsEvent()
    data class SetPosterCardCornerRadius(val cornerRadiusDp: Int) : LayoutSettingsEvent()
    data object ResetPosterCardStyle : LayoutSettingsEvent()
}

@HiltViewModel
class LayoutSettingsViewModel @Inject constructor(
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val addonRepository: AddonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LayoutSettingsUiState())
    val uiState: StateFlow<LayoutSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            layoutPreferenceDataStore.selectedLayout.collectLatest { layout ->
                _uiState.update { it.copy(selectedLayout = layout) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.hasChosenLayout.collectLatest { hasChosen ->
                _uiState.update { it.copy(hasChosen = hasChosen) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.heroCatalogSelection.collectLatest { key ->
                _uiState.update { it.copy(heroCatalogKey = key) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.sidebarCollapsedByDefault.collectLatest { collapsed ->
                _uiState.update { it.copy(sidebarCollapsedByDefault = collapsed) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.heroSectionEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(heroSectionEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.searchDiscoverEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(searchDiscoverEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.posterLabelsEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(posterLabelsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.catalogAddonNameEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(catalogAddonNameEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            layoutPreferenceDataStore.focusedPosterBackdropExpandEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(focusedPosterBackdropExpandEnabled = enabled) }
            }
        }
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
        loadAvailableCatalogs()
    }

    fun onEvent(event: LayoutSettingsEvent) {
        when (event) {
            is LayoutSettingsEvent.SelectLayout -> selectLayout(event.layout)
            is LayoutSettingsEvent.SelectHeroCatalog -> selectHeroCatalog(event.catalogKey)
            is LayoutSettingsEvent.SetSidebarCollapsed -> setSidebarCollapsed(event.collapsed)
            is LayoutSettingsEvent.SetHeroSectionEnabled -> setHeroSectionEnabled(event.enabled)
            is LayoutSettingsEvent.SetSearchDiscoverEnabled -> setSearchDiscoverEnabled(event.enabled)
            is LayoutSettingsEvent.SetPosterLabelsEnabled -> setPosterLabelsEnabled(event.enabled)
            is LayoutSettingsEvent.SetCatalogAddonNameEnabled -> setCatalogAddonNameEnabled(event.enabled)
            is LayoutSettingsEvent.SetFocusedPosterBackdropExpandEnabled -> setFocusedPosterBackdropExpandEnabled(event.enabled)
            is LayoutSettingsEvent.SetPosterCardWidth -> setPosterCardWidth(event.widthDp)
            is LayoutSettingsEvent.SetPosterCardCornerRadius -> setPosterCardCornerRadius(event.cornerRadiusDp)
            LayoutSettingsEvent.ResetPosterCardStyle -> resetPosterCardStyle()
        }
    }

    private fun selectLayout(layout: HomeLayout) {
        viewModelScope.launch {
            layoutPreferenceDataStore.setLayout(layout)
        }
    }

    private fun selectHeroCatalog(catalogKey: String) {
        viewModelScope.launch {
            layoutPreferenceDataStore.setHeroCatalogKey(catalogKey)
        }
    }

    private fun setSidebarCollapsed(collapsed: Boolean) {
        viewModelScope.launch {
            layoutPreferenceDataStore.setSidebarCollapsedByDefault(collapsed)
        }
    }

    private fun setHeroSectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            layoutPreferenceDataStore.setHeroSectionEnabled(enabled)
        }
    }

    private fun setSearchDiscoverEnabled(enabled: Boolean) {
        viewModelScope.launch {
            layoutPreferenceDataStore.setSearchDiscoverEnabled(enabled)
        }
    }

    private fun setPosterLabelsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            layoutPreferenceDataStore.setPosterLabelsEnabled(enabled)
        }
    }

    private fun setCatalogAddonNameEnabled(enabled: Boolean) {
        viewModelScope.launch {
            layoutPreferenceDataStore.setCatalogAddonNameEnabled(enabled)
        }
    }

    private fun setFocusedPosterBackdropExpandEnabled(enabled: Boolean) {
        viewModelScope.launch {
            layoutPreferenceDataStore.setFocusedPosterBackdropExpandEnabled(enabled)
        }
    }

    private fun setPosterCardWidth(widthDp: Int) {
        viewModelScope.launch {
            layoutPreferenceDataStore.setPosterCardWidthDp(widthDp)
            layoutPreferenceDataStore.setPosterCardHeightDp((widthDp * 3) / 2)
        }
    }

    private fun setPosterCardCornerRadius(cornerRadiusDp: Int) {
        viewModelScope.launch {
            layoutPreferenceDataStore.setPosterCardCornerRadiusDp(cornerRadiusDp)
        }
    }

    private fun resetPosterCardStyle() {
        viewModelScope.launch {
            layoutPreferenceDataStore.setPosterCardWidthDp(126)
            layoutPreferenceDataStore.setPosterCardHeightDp(189)
            layoutPreferenceDataStore.setPosterCardCornerRadiusDp(12)
        }
    }

    private fun loadAvailableCatalogs() {
        viewModelScope.launch {
            addonRepository.getInstalledAddons().collectLatest { addons ->
                val catalogs = addons.flatMap { addon ->
                    addon.catalogs
                        .filter { catalog ->
                            !catalog.extra.any { it.name == "search" && it.isRequired }
                        }
                        .map { catalog ->
                            CatalogInfo(
                                key = "${addon.id}_${catalog.type.toApiString()}_${catalog.id}",
                                name = catalog.name,
                                addonName = addon.name
                            )
                        }
                }
                _uiState.update { it.copy(availableCatalogs = catalogs) }
            }
        }
    }
}
