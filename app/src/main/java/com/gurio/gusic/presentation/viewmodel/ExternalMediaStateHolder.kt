package com.gurio.gusic.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.gurio.gusic.data.media.AudioMetadataReader
import com.gurio.gusic.data.media.guessImageMimeType
import com.gurio.gusic.data.media.imageExtensionFromMimeType
import com.gurio.gusic.data.media.isValidImageData
import com.gurio.gusic.data.media.resolveAudioFileExtension
import com.gurio.gusic.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

data class ExternalSongLoadResult(
    val song: Song
)

class ExternalMediaStateHolder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun buildExternalQueue(
        result: ExternalSongLoadResult,
        @Suppress("UNUSED_PARAMETER") originalUri: Uri
    ): List<Song> = listOf(result.song)


    suspend fun buildExternalSongFromUri(
        uri: Uri,
        @Suppress("UNUSED_PARAMETER") captureFolderInfo: Boolean = true
    ): ExternalSongLoadResult? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        var displayName: String? = null
        var storeTitle: String? = null
        var storeArtist: String? = null
        var storeAlbum: String? = null
        var storeDuration: Long? = null
        var storeTrack: Int? = null
        var storeYear: Int? = null
        var storeDateAddedSeconds: Long? = null
        var storeDataPath: String? = null
        var storeMediaId: Long? = null

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            OpenableColumns.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )

        try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val mediaIdIndex = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    if (mediaIdIndex != -1) storeMediaId = cursor.getLong(mediaIdIndex)

                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) displayName = cursor.getString(displayNameIndex)

                    val durationIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    if (durationIndex != -1) storeDuration = cursor.getLong(durationIndex)

                    val titleIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    if (titleIndex != -1) storeTitle = cursor.getString(titleIndex)

                    val artistIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    if (artistIndex != -1) storeArtist = cursor.getString(artistIndex)

                    val albumIndex = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    if (albumIndex != -1) storeAlbum = cursor.getString(albumIndex)
                    
                    val trackIndex = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)
                    if (trackIndex != -1) storeTrack = cursor.getInt(trackIndex)
                    
                    val yearIndex = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                    if (yearIndex != -1) storeYear = cursor.getInt(yearIndex)
                    
                    val dateAddedIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                    if (dateAddedIndex != -1) storeDateAddedSeconds = cursor.getLong(dateAddedIndex)

                    val dataPathIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (dataPathIndex != -1) storeDataPath = cursor.getString(dataPathIndex)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error querying MediaStore for uri: $uri")
        }

        val metadata = AudioMetadataReader.read(context, uri) ?: return@withContext null

        val albumArtUriString = metadata.artwork?.let { artwork ->
             if (isValidImageData(artwork.bytes)) {
                 persistExternalAlbumArt(uri, artwork.bytes, artwork.mimeType)
             } else null
        }

        val finalTitle = storeTitle ?: metadata.title ?: displayName ?: "Unknown Title"
        val finalArtist = storeArtist ?: metadata.artist ?: "Unknown Artist"
        val finalAlbum = storeAlbum ?: metadata.album ?: "Unknown Album"
        val finalDuration = storeDuration?.takeIf { it > 0 } ?: metadata.durationMs ?: 0L

        val mimeType = context.contentResolver.getType(uri) ?: "audio/*"
        val directFilePath = resolveDirectFilePath(uri, storeDataPath)
        val cachedPlaybackFile = if (directFilePath == null && uri.scheme == "content") {
            persistExternalAudioForPlayback(uri)
        } else {
            null
        }
        val playbackContentUriString = when {
            cachedPlaybackFile != null -> Uri.fromFile(cachedPlaybackFile).toString()
            directFilePath != null -> Uri.fromFile(File(directFilePath)).toString()
            else -> uri.toString()
        }
        val playbackPath = cachedPlaybackFile?.absolutePath
            ?: directFilePath
            ?: uri.toString()

        val mediaStoreSongId = storeMediaId ?: uri.mediaStoreAudioId()
        val songId = mediaStoreSongId?.toString() ?: "external:${uri}"
        
        val song = Song(
            id = songId, 
            title = finalTitle,
            artist = finalArtist,
            artistId = -1,
            album = finalAlbum,
            albumId = -1,
            albumArtist = metadata.albumArtist,
            path = playbackPath,
            contentUriString = playbackContentUriString,
            albumArtUriString = albumArtUriString,
            duration = finalDuration,
            genre = metadata.genre,
            trackNumber = storeTrack ?: metadata.trackNumber ?: 0,
            year = storeYear ?: metadata.year ?: 0,
            dateAdded = storeDateAddedSeconds ?: (System.currentTimeMillis() / 1000),
            mimeType = mimeType,
            bitrate = metadata.bitrate,
            sampleRate = metadata.sampleRate
        )
        
        ExternalSongLoadResult(song = song)
    }

    private fun Uri.mediaStoreAudioId(): Long? {
        val normalizedUri = toString().substringBefore('?').substringBefore('#')
        if (
            !normalizedUri.startsWith("content://media/", ignoreCase = true) ||
            !normalizedUri.contains("/audio/media/", ignoreCase = true)
        ) {
            return null
        }

        return normalizedUri.substringAfterLast('/').toLongOrNull()?.takeIf { it > 0L }
    }

    private fun resolveDirectFilePath(uri: Uri, storeDataPath: String?): String? {
        storeDataPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.canRead() }
            ?.absolutePath
            ?.let { return it }

        if (uri.scheme == "file") {
            return uri.path
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.takeIf { it.exists() && it.canRead() }
                ?.absolutePath
        }

        return null
    }

    private fun persistExternalAudioForPlayback(uri: Uri): File? {
        return runCatching {
            val directory = File(context.cacheDir, "external_audio")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val fileNamePrefix = "audio_${uri.toString().hashCode()}."
            directory.listFiles { file ->
                file.name.startsWith(fileNamePrefix)
            }?.forEach { it.delete() }

            val extension = resolveAudioFileExtension(context, uri).trimStart('.').ifBlank { "mp3" }
            val file = File(directory, "$fileNamePrefix$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null

            file.takeIf { it.length() > 0L }
        }.onFailure { throwable ->
            Timber.w(throwable, "Unable to persist external audio for playback uri: $uri")
        }.getOrNull()
    }

    private fun persistExternalAlbumArt(uri: Uri, data: ByteArray, mimeType: String? = null): String? {
        return runCatching {
            if (!isValidImageData(data)) {
                throw IllegalArgumentException("Invalid embedded album art for uri: $uri")
            }
            val directory = File(context.cacheDir, "external_artwork")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val resolvedMimeType = mimeType ?: guessImageMimeType(data)
            val extension = imageExtensionFromMimeType(resolvedMimeType) ?: "jpg"
            val fileNamePrefix = "art_${uri.toString().hashCode()}."
            directory.listFiles { file ->
                file.name.startsWith(fileNamePrefix)
            }?.forEach { it.delete() }
            val fileName = "$fileNamePrefix$extension"
            val file = File(directory, fileName)
            file.outputStream().use { output ->
                output.write(data)
            }
            Uri.fromFile(file).toString()
        }.onFailure { throwable ->
            Timber.w(throwable, "Unable to persist album art for external uri: $uri")
        }.getOrNull()
    }
}
