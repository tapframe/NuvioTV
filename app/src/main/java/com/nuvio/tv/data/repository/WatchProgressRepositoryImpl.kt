package com.nuvio.tv.data.repository

import android.util.Log
import com.nuvio.tv.data.local.TraktAuthDataStore
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.domain.repository.WatchProgressRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class WatchProgressRepositoryImpl @Inject constructor(
    private val watchProgressPreferences: WatchProgressPreferences,
    private val traktAuthDataStore: TraktAuthDataStore,
    private val traktProgressService: TraktProgressService
) : WatchProgressRepository {
    companion object {
        private const val TAG = "WatchProgressRepo"
    }

    override val allProgress: Flow<List<WatchProgress>>
        get() = traktAuthDataStore.isAuthenticated
            .distinctUntilChanged()
            .flatMapLatest { isAuthenticated ->
                if (isAuthenticated) {
                    traktProgressService.observeAllProgress()
                } else {
                    watchProgressPreferences.allProgress
                }
            }

    override val continueWatching: Flow<List<WatchProgress>>
        get() = allProgress.map { list -> list.filter { it.isInProgress() } }

    override fun getProgress(contentId: String): Flow<WatchProgress?> {
        return traktAuthDataStore.isAuthenticated
            .distinctUntilChanged()
            .flatMapLatest { isAuthenticated ->
                if (isAuthenticated) {
                    allProgress.map { items ->
                        items
                            .filter { it.contentId == contentId }
                            .maxByOrNull { it.lastWatched }
                    }
                } else {
                    watchProgressPreferences.getProgress(contentId)
                }
            }
    }

    override fun getEpisodeProgress(contentId: String, season: Int, episode: Int): Flow<WatchProgress?> {
        return traktAuthDataStore.isAuthenticated
            .distinctUntilChanged()
            .flatMapLatest { isAuthenticated ->
                if (isAuthenticated) {
                    allProgress.map { items ->
                        items.firstOrNull {
                            it.contentId == contentId && it.season == season && it.episode == episode
                        }
                    }
                } else {
                    watchProgressPreferences.getEpisodeProgress(contentId, season, episode)
                }
            }
    }

    override fun getAllEpisodeProgress(contentId: String): Flow<Map<Pair<Int, Int>, WatchProgress>> {
        return traktAuthDataStore.isAuthenticated
            .distinctUntilChanged()
            .flatMapLatest { isAuthenticated ->
                if (isAuthenticated) {
                    combine(
                        traktProgressService.observeEpisodeProgress(contentId),
                        allProgress.map { items ->
                            items.filter { it.contentId == contentId && it.season != null && it.episode != null }
                        }
                    ) { remoteMap, liveEpisodes ->
                        val merged = remoteMap.toMutableMap()
                        liveEpisodes.forEach { episodeProgress ->
                            val season = episodeProgress.season ?: return@forEach
                            val episode = episodeProgress.episode ?: return@forEach
                            merged[season to episode] = episodeProgress
                        }
                        merged
                    }.distinctUntilChanged()
                } else {
                    watchProgressPreferences.getAllEpisodeProgress(contentId)
                }
            }
    }

    override fun isWatched(contentId: String, season: Int?, episode: Int?): Flow<Boolean> {
        return traktAuthDataStore.isAuthenticated
            .distinctUntilChanged()
            .flatMapLatest { isAuthenticated ->
                if (!isAuthenticated) {
                    return@flatMapLatest if (season != null && episode != null) {
                        watchProgressPreferences.getEpisodeProgress(contentId, season, episode)
                            .map { it?.isCompleted() == true }
                    } else {
                        watchProgressPreferences.getProgress(contentId)
                            .map { it?.isCompleted() == true }
                    }
                }

                if (season != null && episode != null) {
                    traktProgressService.observeEpisodeProgress(contentId)
                        .map { progressMap ->
                            progressMap[season to episode]?.isCompleted() == true
                        }
                        .distinctUntilChanged()
                } else {
                    traktProgressService.observeMovieWatched(contentId)
                }
            }
    }

    override suspend fun saveProgress(progress: WatchProgress) {
        if (traktAuthDataStore.isAuthenticated.first()) {
            traktProgressService.applyOptimisticProgress(progress)
            return
        }
        watchProgressPreferences.saveProgress(progress)
    }

    override suspend fun removeProgress(contentId: String, season: Int?, episode: Int?) {
        val isAuthenticated = traktAuthDataStore.isAuthenticated.first()
        Log.d(
            TAG,
            "removeProgress called contentId=$contentId season=$season episode=$episode authenticated=$isAuthenticated"
        )
        if (isAuthenticated) {
            traktProgressService.applyOptimisticRemoval(contentId, season, episode)
            traktProgressService.removeProgress(contentId, season, episode)
            return
        }
        watchProgressPreferences.removeProgress(contentId, season, episode)
    }

    override suspend fun removeFromHistory(contentId: String, season: Int?, episode: Int?) {
        if (traktAuthDataStore.isAuthenticated.first()) {
            traktProgressService.removeFromHistory(contentId, season, episode)
            return
        }
        watchProgressPreferences.removeProgress(contentId, season, episode)
    }

    override suspend fun markAsCompleted(progress: WatchProgress) {
        if (traktAuthDataStore.isAuthenticated.first()) {
            val now = System.currentTimeMillis()
            val duration = progress.duration.takeIf { it > 0L } ?: 1L
            val completed = progress.copy(
                position = duration,
                duration = duration,
                progressPercent = 100f,
                lastWatched = now
            )
            traktProgressService.applyOptimisticProgress(completed)
            runCatching {
                traktProgressService.markAsWatched(
                    progress = completed,
                    title = completed.name.takeIf { it.isNotBlank() },
                    year = null
                )
            }.onFailure {
                traktProgressService.applyOptimisticRemoval(
                    contentId = completed.contentId,
                    season = completed.season,
                    episode = completed.episode
                )
                throw it
            }
            return
        }
        watchProgressPreferences.markAsCompleted(progress)
    }

    override suspend fun clearAll() {
        if (traktAuthDataStore.isAuthenticated.first()) {
            traktProgressService.clearOptimistic()
            return
        }
        watchProgressPreferences.clearAll()
    }
}
