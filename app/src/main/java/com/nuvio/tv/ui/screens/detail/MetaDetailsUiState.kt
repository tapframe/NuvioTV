package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.Video

data class MetaDetailsUiState(
    val isLoading: Boolean = true,
    val meta: Meta? = null,
    val error: String? = null,
    val selectedSeason: Int = 1,
    val seasons: List<Int> = emptyList(),
    val episodesForSeason: List<Video> = emptyList(),
    val isInLibrary: Boolean = false
)

sealed class MetaDetailsEvent {
    data class OnSeasonSelected(val season: Int) : MetaDetailsEvent()
    data class OnEpisodeClick(val video: Video) : MetaDetailsEvent()
    data object OnPlayClick : MetaDetailsEvent()
    data object OnToggleLibrary : MetaDetailsEvent()
    data object OnRetry : MetaDetailsEvent()
    data object OnBackPress : MetaDetailsEvent()
}
