package com.nuvio.tv.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Addon(
    val id: String,
    val name: String,
    val version: String,
    val description: String?,
    val logo: String?,
    val baseUrl: String,
    val catalogs: List<CatalogDescriptor>,
    val types: List<ContentType>,
    val rawTypes: List<String> = types.map { it.toApiString() },
    val resources: List<AddonResource>
)

@Immutable
data class CatalogDescriptor(
    val type: ContentType,
    val rawType: String = type.toApiString(),
    val id: String,
    val name: String,
    val extra: List<CatalogExtra> = emptyList()
) {
    val apiType: String
        get() = type.toApiString(rawType)
}

@Immutable
data class CatalogExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String>? = null
)

@Immutable
data class AddonResource(
    val name: String,
    val types: List<String>,
    val idPrefixes: List<String>?
)
