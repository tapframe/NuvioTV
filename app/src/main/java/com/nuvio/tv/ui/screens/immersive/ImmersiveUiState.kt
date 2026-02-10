package com.nuvio.tv.ui.screens.immersive

import androidx.compose.runtime.Immutable
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Meta

@Immutable
data class ImmersiveUiState(
    val catalogRows: List<CatalogRow> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@Immutable
data class MetadataPopupState(
    val visible: Boolean = false,
    val title: String = "",
    val description: String? = null,
    val isLoadingDescription: Boolean = false
)
