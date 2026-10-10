package com.gurio.gusic.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.gurio.gusic.data.database.MusicDao
import com.gurio.gusic.data.database.toSong
import com.gurio.gusic.data.model.Song
import com.gurio.gusic.data.model.SortOption
import com.gurio.gusic.data.model.StorageFilter
import com.gurio.gusic.data.preferences.UserPreferencesRepository
import com.gurio.gusic.utils.DirectoryFilterUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreSongRepository @Inject constructor(
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : SongRepository {

    private fun normalizePath(path: String): String = File(path).absolutePath

    /**
     * Computes allowed parent directories by filtering out blocked directories.
     * Returns Pair(allowedDirs, applyFilter).
     */
    private suspend fun computeAllowedDirs(
        allowedDirs: Set<String>,
        blockedDirs: Set<String>
    ): Pair<List<String>, Boolean> {
        return DirectoryFilterUtils.computeAllowedParentDirs(
            allowedDirs = allowedDirs,
            blockedDirs = blockedDirs,
            getAllParentDirs = { musicDao.getDistinctParentDirectories() },
            normalizePath = ::normalizePath
        )
    }

    private val defaultPagingConfig = PagingConfig(
        pageSize = 50,
        enablePlaceholders = true,
        maxSize = 250
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedSongs(
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): Flow<PagingData<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    Pager(
                        config = defaultPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getSongsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                sortOrder = sortOption.storageKey,
                                filterMode = storageFilter.value
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toSong() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedFavoriteSongs(
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): Flow<PagingData<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    Pager(
                        config = defaultPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getFavoriteSongsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                sortOrder = sortOption.storageKey,
                                filterMode = storageFilter.value
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toSong().copy(isFavorite = true) }
        }
    }

    override suspend fun getFavoriteSongsOnce(
        storageFilter: StorageFilter
    ): List<Song> = withContext(Dispatchers.IO) {
        val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
        val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
        val (allowedParentDirs, applyDirectoryFilter) =
            computeAllowedDirs(allowedDirs, blockedDirs)
        musicDao.getFavoriteSongsList(
            allowedParentDirs = allowedParentDirs,
            applyDirectoryFilter = applyDirectoryFilter,
            filterMode = storageFilter.value
        ).map { entity -> entity.toSong().copy(isFavorite = true) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getFavoriteSongCountFlow(
        storageFilter: StorageFilter
    ): Flow<Int> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(allowedParentDirs to applyDirectoryFilter)
            }.flatMapLatest { (allowedParentDirs, applyDirectoryFilter) ->
                musicDao.getFavoriteSongCount(
                    allowedParentDirs = allowedParentDirs,
                    applyDirectoryFilter = applyDirectoryFilter,
                    filterMode = storageFilter.value
                )
            }
        }
    }
}
