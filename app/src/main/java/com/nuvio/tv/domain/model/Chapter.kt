package com.nuvio.tv.domain.model

data class Chapter(
    val title: String?,
    val startTimeMs: Long,
    val endTimeMs: Long = 0L
)
