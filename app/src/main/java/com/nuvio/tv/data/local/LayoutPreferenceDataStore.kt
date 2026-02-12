package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.domain.model.HomeLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.layoutDataStore: DataStore<Preferences> by preferencesDataStore(name = "layout_settings")

@Singleton
class LayoutPreferenceDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.layoutDataStore
    private val gson = Gson()

    private val layoutKey = stringPreferencesKey("selected_layout")
    private val hasChosenKey = booleanPreferencesKey("has_chosen_layout")
    private val heroCatalogKey = stringPreferencesKey("hero_catalog_key")
    private val homeCatalogOrderKeysKey = stringPreferencesKey("home_catalog_order_keys")
    private val disabledHomeCatalogKeysKey = stringPreferencesKey("disabled_home_catalog_keys")
    private val sidebarCollapsedKey = booleanPreferencesKey("sidebar_collapsed_by_default")
    private val heroSectionEnabledKey = booleanPreferencesKey("hero_section_enabled")
    private val searchDiscoverEnabledKey = booleanPreferencesKey("search_discover_enabled")
    private val posterLabelsEnabledKey = booleanPreferencesKey("poster_labels_enabled")
    private val catalogAddonNameEnabledKey = booleanPreferencesKey("catalog_addon_name_enabled")
    private val focusedPosterBackdropExpandEnabledKey = booleanPreferencesKey("focused_poster_backdrop_expand_enabled")
    private val posterCardWidthDpKey = intPreferencesKey("poster_card_width_dp")
    private val posterCardHeightDpKey = intPreferencesKey("poster_card_height_dp")
    private val posterCardCornerRadiusDpKey = intPreferencesKey("poster_card_corner_radius_dp")

    private companion object {
        const val DEFAULT_POSTER_CARD_WIDTH_DP = 126
        const val DEFAULT_POSTER_CARD_HEIGHT_DP = 189
        const val DEFAULT_POSTER_CARD_CORNER_RADIUS_DP = 12
    }

    val selectedLayout: Flow<HomeLayout> = dataStore.data.map { prefs ->
        val layoutName = prefs[layoutKey] ?: HomeLayout.CLASSIC.name
        try {
            HomeLayout.valueOf(layoutName)
        } catch (e: IllegalArgumentException) {
            HomeLayout.CLASSIC
        }
    }

    val hasChosenLayout: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[hasChosenKey] ?: false
    }

    val heroCatalogSelection: Flow<String?> = dataStore.data.map { prefs ->
        prefs[heroCatalogKey]
    }

    val homeCatalogOrderKeys: Flow<List<String>> = dataStore.data.map { prefs ->
        parseHomeCatalogOrderKeys(prefs[homeCatalogOrderKeysKey])
    }

    val disabledHomeCatalogKeys: Flow<List<String>> = dataStore.data.map { prefs ->
        parseHomeCatalogOrderKeys(prefs[disabledHomeCatalogKeysKey])
    }

    val sidebarCollapsedByDefault: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[sidebarCollapsedKey] ?: false
    }

    val heroSectionEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[heroSectionEnabledKey] ?: true
    }

    val searchDiscoverEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[searchDiscoverEnabledKey] ?: true
    }

    val posterLabelsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[posterLabelsEnabledKey] ?: true
    }

    val catalogAddonNameEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[catalogAddonNameEnabledKey] ?: true
    }

    val focusedPosterBackdropExpandEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[focusedPosterBackdropExpandEnabledKey] ?: false
    }

    val posterCardWidthDp: Flow<Int> = dataStore.data.map { prefs ->
        prefs[posterCardWidthDpKey] ?: DEFAULT_POSTER_CARD_WIDTH_DP
    }

    val posterCardHeightDp: Flow<Int> = dataStore.data.map { prefs ->
        prefs[posterCardHeightDpKey] ?: DEFAULT_POSTER_CARD_HEIGHT_DP
    }

    val posterCardCornerRadiusDp: Flow<Int> = dataStore.data.map { prefs ->
        prefs[posterCardCornerRadiusDpKey] ?: DEFAULT_POSTER_CARD_CORNER_RADIUS_DP
    }

    suspend fun setLayout(layout: HomeLayout) {
        dataStore.edit { prefs ->
            prefs[layoutKey] = layout.name
            prefs[hasChosenKey] = true
        }
    }

    suspend fun setHeroCatalogKey(catalogKey: String) {
        dataStore.edit { prefs ->
            prefs[heroCatalogKey] = catalogKey
        }
    }

    suspend fun setHomeCatalogOrderKeys(keys: List<String>) {
        val normalizedKeys = normalizeCatalogOrderKeys(keys)
        dataStore.edit { prefs ->
            if (normalizedKeys.isEmpty()) {
                prefs.remove(homeCatalogOrderKeysKey)
            } else {
                prefs[homeCatalogOrderKeysKey] = gson.toJson(normalizedKeys)
            }
        }
    }

    suspend fun setDisabledHomeCatalogKeys(keys: List<String>) {
        val normalizedKeys = normalizeCatalogOrderKeys(keys)
        dataStore.edit { prefs ->
            if (normalizedKeys.isEmpty()) {
                prefs.remove(disabledHomeCatalogKeysKey)
            } else {
                prefs[disabledHomeCatalogKeysKey] = gson.toJson(normalizedKeys)
            }
        }
    }

    suspend fun setSidebarCollapsedByDefault(collapsed: Boolean) {
        dataStore.edit { prefs ->
            prefs[sidebarCollapsedKey] = collapsed
        }
    }

    suspend fun setHeroSectionEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[heroSectionEnabledKey] = enabled
        }
    }

    suspend fun setSearchDiscoverEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[searchDiscoverEnabledKey] = enabled
        }
    }

    suspend fun setPosterLabelsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[posterLabelsEnabledKey] = enabled
        }
    }

    suspend fun setCatalogAddonNameEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[catalogAddonNameEnabledKey] = enabled
        }
    }

    suspend fun setFocusedPosterBackdropExpandEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[focusedPosterBackdropExpandEnabledKey] = enabled
        }
    }

    suspend fun setPosterCardWidthDp(widthDp: Int) {
        dataStore.edit { prefs ->
            prefs[posterCardWidthDpKey] = widthDp
        }
    }

    suspend fun setPosterCardHeightDp(heightDp: Int) {
        dataStore.edit { prefs ->
            prefs[posterCardHeightDpKey] = heightDp
        }
    }

    suspend fun setPosterCardCornerRadiusDp(cornerRadiusDp: Int) {
        dataStore.edit { prefs ->
            prefs[posterCardCornerRadiusDpKey] = cornerRadiusDp
        }
    }

    private fun parseHomeCatalogOrderKeys(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            val parsed = gson.fromJson<List<String>>(json, type).orEmpty()
            normalizeCatalogOrderKeys(parsed)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalizeCatalogOrderKeys(keys: List<String>): List<String> {
        return keys.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }
}
