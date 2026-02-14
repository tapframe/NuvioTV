package com.nuvio.tv.data.mapper

import com.nuvio.tv.data.remote.dto.AddonManifestDto
import com.nuvio.tv.data.remote.dto.CatalogDescriptorDto
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.CatalogExtra
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.ContentType

fun AddonManifestDto.toDomain(baseUrl: String): Addon {
    val manifestTypes = types.map { it.trim() }.filter { it.isNotEmpty() }
    return Addon(
        id = id,
        name = name,
        version = version,
        description = description,
        logo = logo,
        baseUrl = baseUrl,
        catalogs = catalogs.map { it.toDomain() },
        types = manifestTypes.map { ContentType.fromString(it) },
        rawTypes = manifestTypes,
        resources = parseResources(resources, manifestTypes)
    )
}

fun CatalogDescriptorDto.toDomain(): CatalogDescriptor {
    val manifestType = type.trim()
    return CatalogDescriptor(
        type = ContentType.fromString(manifestType),
        rawType = manifestType,
        id = id,
        name = name,
        extra = extra.orEmpty().map { dto ->
            CatalogExtra(
                name = dto.name,
                isRequired = dto.isRequired ?: false,
                options = dto.options
            )
        }
    )
}

private fun parseResources(resources: List<Any>, defaultTypes: List<String>): List<AddonResource> {
    return resources.mapNotNull { resource ->
        when (resource) {
            is String -> {
                // Simple resource format: "meta", "stream", etc.
                AddonResource(
                    name = resource,
                    types = defaultTypes,
                    idPrefixes = null
                )
            }
            is Map<*, *> -> {
                // Complex resource format with types and idPrefixes
                val name = resource["name"] as? String ?: return@mapNotNull null
                val types = (resource["types"] as? List<*>)?.filterIsInstance<String>() ?: defaultTypes
                val idPrefixes = (resource["idPrefixes"] as? List<*>)?.filterIsInstance<String>()
                AddonResource(
                    name = name,
                    types = types,
                    idPrefixes = idPrefixes
                )
            }
            else -> null
        }
    }
}
