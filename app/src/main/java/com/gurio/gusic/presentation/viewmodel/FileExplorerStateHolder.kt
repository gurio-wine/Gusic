package com.gurio.gusic.presentation.viewmodel

import android.content.Context
import android.os.Environment
import com.gurio.gusic.data.preferences.UserPreferencesRepository
import com.gurio.gusic.utils.DirectoryRuleResolver
import com.gurio.gusic.utils.StorageInfo
import com.gurio.gusic.utils.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

data class DirectoryEntry(
    val file: File,
    val directAudioCount: Int,
    val totalAudioCount: Int,
    val canonicalPath: String,
    val displayName: String? = null,
    val isBlocked: Boolean = false
)

internal data class RawDirectoryEntry(
    val file: File,
    val directAudioCount: Int,
    val totalAudioCount: Int,
    val canonicalPath: String,
    val displayName: String? = null
)

internal fun mergeDirectoryEntryLists(
    filesystemEntries: List<RawDirectoryEntry>,
    additionalFilesystemEntries: List<RawDirectoryEntry>
): List<RawDirectoryEntry> {
    val merged = linkedMapOf<String, RawDirectoryEntry>()

    filesystemEntries.forEach { entry ->
        merged[entry.canonicalPath] = entry
    }

    additionalFilesystemEntries.forEach { additionalEntry ->
        val existing = merged[additionalEntry.canonicalPath]
        merged[additionalEntry.canonicalPath] =
            if (existing == null) {
                additionalEntry
            } else {
                additionalEntry.copy(displayName = additionalEntry.displayName ?: existing.displayName)
            }
    }

    return merged.values.sortedWith(compareBy({ it.file.name.lowercase() }))
}

@OptIn(ExperimentalCoroutinesApi::class)
class FileExplorerStateHolder(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope,
    private val context: Context,
    initialRoot: File = Environment.getExternalStorageDirectory()
) {

    private var visibleRoot: File = initialRoot
    private var rootCanonicalPath: String = normalizePath(visibleRoot)

    private val _availableStorages = MutableStateFlow<ImmutableList<StorageInfo>>(persistentListOf())
    val availableStorages: StateFlow<ImmutableList<StorageInfo>> = _availableStorages.asStateFlow()

    private val _selectedStorageIndex = MutableStateFlow(0)
    val selectedStorageIndex: StateFlow<Int> = _selectedStorageIndex.asStateFlow()

    private val directoryChildrenCache = ConcurrentHashMap<String, List<RawDirectoryEntry>>()
    private val prefetchedDirectoryKeys = ConcurrentHashMap.newKeySet<String>()
    private val resolvedDirectoryKeys = ConcurrentHashMap.newKeySet<String>()

    private val _currentPath = MutableStateFlow(visibleRoot)
    val currentPath: StateFlow<File> = _currentPath.asStateFlow()

    private val _rawCurrentDirectoryChildren = MutableStateFlow<List<RawDirectoryEntry>>(emptyList())

    private val _allowedDirectories = MutableStateFlow<Set<String>>(emptySet())
    val allowedDirectories: StateFlow<Set<String>> = _allowedDirectories.asStateFlow()

    private val _blockedDirectories = MutableStateFlow<Set<String>>(emptySet())
    val blockedDirectories: StateFlow<Set<String>> = _blockedDirectories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isPrimingExplorer = MutableStateFlow(false)
    val isPrimingExplorer: StateFlow<Boolean> = _isPrimingExplorer.asStateFlow()

    private val _isExplorerReady = MutableStateFlow(false)
    val isExplorerReady: StateFlow<Boolean> = _isExplorerReady.asStateFlow()

    private val _isCurrentDirectoryResolved = MutableStateFlow(false)
    val isCurrentDirectoryResolved: StateFlow<Boolean> = _isCurrentDirectoryResolved.asStateFlow()

    private val _currentDirectoryChildren = MutableStateFlow<ImmutableList<DirectoryEntry>>(persistentListOf())
    val currentDirectoryChildren: StateFlow<ImmutableList<DirectoryEntry>> = _currentDirectoryChildren.asStateFlow()

    private val mapperDispatcher = Dispatchers.Default
    private val prefetchDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val loadMutex = Mutex()
    private var loadJob: Job? = null

    companion object {
        private const val PREFETCH_CHILDREN_LIMIT = 8
        private const val UNKNOWN_AUDIO_COUNT = -1
    }

    init {
        refreshAvailableStorages()

        combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowed, blocked ->
            Pair(allowed, blocked)
        }
            .onEach { (allowed, blocked) ->
                _allowedDirectories.value = allowed.map(::normalizePath).toSet()
                _blockedDirectories.value = blocked.map(::normalizePath).toSet()
            }
            .launchIn(scope)

        combine(
            _rawCurrentDirectoryChildren,
            _allowedDirectories,
            _blockedDirectories
        ) { rawEntries, allowed, blocked ->
            Triple(rawEntries, allowed, blocked)
        }
            .mapLatest { (rawEntries, allowed, blocked) ->
                val resolver = DirectoryRuleResolver(allowed, blocked)
                rawEntries.map { raw ->
                    DirectoryEntry(
                        file = raw.file,
                        directAudioCount = raw.directAudioCount,
                        totalAudioCount = raw.totalAudioCount,
                        canonicalPath = raw.canonicalPath,
                        displayName = raw.displayName,
                        isBlocked = resolver.isBlocked(raw.canonicalPath)
                    )
                }
            }
            .flowOn(mapperDispatcher)
            .onEach {
                _currentDirectoryChildren.value = it.toImmutableList()
            }.launchIn(scope)

    }

    fun refreshAvailableStorages() {
        _availableStorages.value = StorageUtils.getAvailableStorages(context).toImmutableList()
        if (_selectedStorageIndex.value >= _availableStorages.value.size) {
            _selectedStorageIndex.value = 0
        }
    }

    fun selectStorage(index: Int) {
        val storages = _availableStorages.value
        if (index < 0 || index >= storages.size) return

        _selectedStorageIndex.value = index
        val selectedStorage = storages[index]

        visibleRoot = selectedStorage.path
        rootCanonicalPath = normalizePath(visibleRoot)
        _currentPath.value = visibleRoot

        loadDirectory(visibleRoot, updatePath = true, forceRefresh = false)
    }

    fun refreshCurrentDirectory(): Job {
        return loadDirectory(_currentPath.value, updatePath = false, forceRefresh = true)
    }

    fun loadDirectory(file: File, updatePath: Boolean = true, forceRefresh: Boolean = false): Job {
        loadJob?.cancel()
        val target = if (file.isDirectory) file else visibleRoot
        val targetKey = normalizePath(target)
        if (updatePath) {
            _currentPath.value = target
        }
        if (forceRefresh || !directoryChildrenCache.containsKey(targetKey)) {
            _isLoading.value = true
            _isCurrentDirectoryResolved.value = false
            _rawCurrentDirectoryChildren.value = emptyList()
        }
        val job = scope.launch {
            loadMutex.withLock {
                loadDirectoryInternal(target, updatePath = false, forceRefresh = forceRefresh)
            }
        }
        loadJob = job
        return job
    }

    fun primeExplorerRoot(): Job? {
        val cachedRootEntries = directoryChildrenCache[rootCanonicalPath]
        if (_isExplorerReady.value && !cachedRootEntries.isNullOrEmpty()) return null
        if (_isPrimingExplorer.value) return null

        _isPrimingExplorer.value = true
        _isLoading.value = true
        _isCurrentDirectoryResolved.value = false
        _rawCurrentDirectoryChildren.value = emptyList()
        return scope.launch {
            try {
                loadMutex.withLock {
                    loadDirectoryInternal(visibleRoot, updatePath = true, forceRefresh = false)
                }
            } finally {
                _isPrimingExplorer.value = false
            }
        }
    }

    fun openExplorerRoot(): Job {
        val cachedRootEntries = directoryChildrenCache[rootCanonicalPath]
        val shouldForceRefresh = cachedRootEntries.isNullOrEmpty()
        return loadDirectory(visibleRoot, updatePath = true, forceRefresh = shouldForceRefresh)
    }

    fun navigateUp() {
        val current = _currentPath.value
        val parent = current.parentFile ?: return
        val parentCanonical = runCatching { parent.canonicalPath }.getOrNull()
        val isAboveRoot = parentCanonical?.startsWith(rootCanonicalPath) == false

        if (isAboveRoot || current.path == visibleRoot.path) {
            loadDirectory(visibleRoot)
        } else {
            loadDirectory(parent)
        }
    }

    suspend fun toggleDirectoryAllowed(file: File) {
        val currentAllowed = _allowedDirectories.value.toMutableSet()
        val currentBlocked = _blockedDirectories.value.toMutableSet()
        val path = normalizePath(file)

        val isEffectivelyBlocked = DirectoryRuleResolver(currentAllowed, currentBlocked).isBlocked(path)

        val isExplicitlyBlocked = currentBlocked.contains(path)

        if (isEffectivelyBlocked) {
            currentBlocked.remove(path)

            if (isExplicitlyBlocked) {
                currentAllowed.removeAll { it.startsWith("$path/") }
            }

            val resolver = DirectoryRuleResolver(currentAllowed, currentBlocked)
            if (resolver.isBlocked(path)) {
               currentAllowed.add(path)
            } else {
               currentAllowed.remove(path)
            }

            _allowedDirectories.value = currentAllowed
            _blockedDirectories.value = currentBlocked
            
            userPreferencesRepository.updateDirectorySelections(currentAllowed, currentBlocked)
            return
        }

        currentBlocked.removeAll { it.startsWith("$path/") }
        currentAllowed.removeAll { it == path || it.startsWith("$path/") }
        
        currentBlocked.add(path)

        _allowedDirectories.value = currentAllowed
        _blockedDirectories.value = currentBlocked

        userPreferencesRepository.updateDirectorySelections(currentAllowed, currentBlocked)
    }

    private suspend fun loadDirectoryInternal(file: File, updatePath: Boolean, forceRefresh: Boolean) {
        val target = if (file.isDirectory) file else visibleRoot
        val targetKey = normalizePath(target)

        if (forceRefresh) {
            directoryChildrenCache.clear()
            prefetchedDirectoryKeys.clear()
            resolvedDirectoryKeys.clear()
        }

        if (updatePath) {
            _currentPath.value = target
        }

        val cachedEntries = if (!forceRefresh) {
            directoryChildrenCache[targetKey]
        } else null

        if (cachedEntries != null && (cachedEntries.isNotEmpty() || resolvedDirectoryKeys.contains(targetKey))) {
            _rawCurrentDirectoryChildren.value = cachedEntries
            _isLoading.value = false
            _isExplorerReady.value = true
            _isCurrentDirectoryResolved.value = resolvedDirectoryKeys.contains(targetKey)
            prefetchChildDirectories(cachedEntries)
            return
        }

        _isLoading.value = true
        _isCurrentDirectoryResolved.value = false
        _rawCurrentDirectoryChildren.value = emptyList()

        val resultEntries = listImmediateDirectoryEntries(target)
        resolvedDirectoryKeys.add(targetKey)
        directoryChildrenCache[targetKey] = resultEntries

        _rawCurrentDirectoryChildren.value = resultEntries
        _isLoading.value = false
        _isExplorerReady.value = true
        _isCurrentDirectoryResolved.value = true
        prefetchChildDirectories(resultEntries)
    }

    private suspend fun listImmediateDirectoryEntries(target: File): List<RawDirectoryEntry> =
        withContext(Dispatchers.IO) {
            runCatching {
                target.listFiles()
                    ?.asSequence()
                    ?.filter { it.isDirectory && !it.isHidden }
                    ?.map { child ->
                        RawDirectoryEntry(
                            file = child,
                            directAudioCount = UNKNOWN_AUDIO_COUNT,
                            totalAudioCount = UNKNOWN_AUDIO_COUNT,
                            canonicalPath = normalizePath(child)
                        )
                    }
                    ?.sortedBy { it.file.name.lowercase() }
                    ?.toList()
                    ?: emptyList()
            }.getOrElse { emptyList() }
        }

    private fun prefetchChildDirectories(entries: List<RawDirectoryEntry>) {
        entries.asSequence()
            .take(PREFETCH_CHILDREN_LIMIT)
            .forEach { entry ->
                val targetKey = entry.canonicalPath
                if (directoryChildrenCache.containsKey(targetKey)) return@forEach
                if (!prefetchedDirectoryKeys.add(targetKey)) return@forEach

                scope.launch(prefetchDispatcher) {
                    try {
                        val prefetchedEntries = listImmediateDirectoryEntries(entry.file)
                        directoryChildrenCache[targetKey] = prefetchedEntries
                        resolvedDirectoryKeys.add(targetKey)
                    } catch (error: CancellationException) {
                        prefetchedDirectoryKeys.remove(targetKey)
                        throw error
                    } catch (_: Throwable) {
                        prefetchedDirectoryKeys.remove(targetKey)
                    }
                }
            }
    }

    fun isAtRoot(): Boolean = _currentPath.value.path == visibleRoot.path

    fun rootDirectory(): File = visibleRoot

    private fun normalizePath(file: File): String = file.absolutePath
    private fun normalizePath(path: String): String = File(path).absolutePath
}
