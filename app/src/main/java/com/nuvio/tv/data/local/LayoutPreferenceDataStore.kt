package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    private val layoutKey = stringPreferencesKey("selected_layout")
    private val hasChosenKey = booleanPreferencesKey("has_chosen_layout")
    private val heroCatalogKey = stringPreferencesKey("hero_catalog_key")
    private val sidebarCollapsedKey = booleanPreferencesKey("sidebar_collapsed_by_default")

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

    val sidebarCollapsedByDefault: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[sidebarCollapsedKey] ?: true
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

    suspend fun setSidebarCollapsedByDefault(collapsed: Boolean) {
        dataStore.edit { prefs ->
            prefs[sidebarCollapsedKey] = collapsed
        }
    }
}
