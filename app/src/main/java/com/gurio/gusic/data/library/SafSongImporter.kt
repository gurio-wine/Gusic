package com.gurio.gusic.data.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.gurio.gusic.data.database.AlbumEntity
import com.gurio.gusic.data.database.ArtistEntity
import com.gurio.gusic.data.database.LibraryItemIdentityEntity
import com.gurio.gusic.data.database.PixelPlayerDatabase
import com.gurio.gusic.data.database.SongArtistCrossRef
import com.gurio.gusic.data.database.SongEntity
import com.gurio.gusic.data.database.SourceType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Audio tags read from a selected document through ContentResolver, never from a filesystem path. */
data class SafAudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val durationMillis: Long?,
    val trackNumber: Int?,
    val year: Int?,
    val genre: String?,
    val mimeType: String?,
    val displayName: String?,
)

fun interface SafAudioMetadataReader {
    fun read(uri: Uri): SafAudioMetadata
}

fun interface SafDocumentIdentityReader {
    fun read(uri: Uri): SafDocumentIdentity
}

fun interface SafSongPersistence {
    suspend fun persist(
        uri: Uri,
        identity: SafDocumentIdentity,
        metadata: SafAudioMetadata,
    ): SafImportResult
}

/** ContentResolver-backed audio metadata reader for SAF documents. */
class ContentResolverSafAudioMetadataReader @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafAudioMetadataReader {
    override fun read(uri: Uri): SafAudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            SafAudioMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                durationMillis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.toIntOrNull(),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull(),
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
                mimeType = context.contentResolver.getType(uri),
                displayName = queryDisplayName(uri),
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else null
            }
    }.getOrNull()
}

/** Extracts exact provider authority and opaque DocumentsContract document ID. */
class AndroidSafDocumentIdentityReader @Inject constructor() : SafDocumentIdentityReader {
    override fun read(uri: Uri): SafDocumentIdentity = SafDocumentIdentity.fromDocumentUri(uri.toString())
}

/** Room persistence adapter; all identity rows and library rows are committed atomically. */
@Singleton
class RoomSafSongPersistence @Inject constructor(
    private val database: PixelPlayerDatabase,
) : SafSongPersistence {
    override suspend fun persist(
        uri: Uri,
        identity: SafDocumentIdentity,
        metadata: SafAudioMetadata,
    ): SafImportResult {
        val now = System.currentTimeMillis()
        val locator = uri.toString()
        require(SafDocumentIdentity.fromDocumentUri(locator) == identity) {
            "SAF URI does not match its stable document identity"
        }
        val title = metadata.title?.trim()?.takeIf(String::isNotEmpty)
            ?: metadata.displayName?.substringBeforeLast('.')?.trim()?.takeIf(String::isNotEmpty)
            ?: identity.documentId
        val rawArtistName = metadata.artist?.trim()?.takeIf(String::isNotEmpty)
        val rawAlbumName = metadata.album?.trim()?.takeIf(String::isNotEmpty)
        val rawAlbumArtistName = metadata.albumArtist?.trim()?.takeIf(String::isNotEmpty)
        val effectiveAlbumArtistName = rawAlbumArtistName ?: rawArtistName
        val artistName = rawArtistName ?: "Unknown Artist"
        val albumName = rawAlbumName ?: "Unknown Album"
        val albumArtistName = effectiveAlbumArtistName ?: "Unknown Artist"
        val namespace = LibraryItemIdentityEntity.SAF_NAMESPACE_PREFIX + identity.authority
        val songNativeId = identity.documentId
        val artistNativeId = SafGroupIdentity.artistNativeId(rawArtistName, identity.documentId)
        val albumArtistNativeId = SafGroupIdentity.artistNativeId(effectiveAlbumArtistName, identity.documentId)
        val albumNativeId = SafGroupIdentity.albumNativeId(
            rawAlbumName,
            effectiveAlbumArtistName,
            identity.documentId,
        )

        return database.withTransaction {
            val identityDao = database.libraryItemIdentityDao()
            val musicDao = database.musicDao()
            val songReservation = checkNotNull(
                identityDao.reserveId(
                    itemType = LibraryItemIdentityEntity.TYPE_SONG,
                    namespace = namespace,
                    nativeId = songNativeId,
                    locatorUri = locator,
                    hasReadGrant = true,
                    lastSeenAt = now,
                ),
            ) { "Unable to reserve SAF song identity" }
            val existingSong = musicDao.getSongByIdOnce(songReservation.entityId)
            if (existingSong != null) {
                check(existingSong.sourceType == SourceType.SAF) {
                    "SAF identity collides with an existing non-SAF song"
                }
                val existingIdentity = runCatching {
                    SafDocumentIdentity.fromDocumentUri(existingSong.contentUriString)
                }.getOrNull()
                check(existingIdentity == identity) {
                    "SAF song row does not match the requested authority and document ID"
                }
            }
            val artistReservation = checkNotNull(
                identityDao.reserveId(
                    itemType = LibraryItemIdentityEntity.TYPE_ARTIST,
                    namespace = namespace,
                    nativeId = artistNativeId,
                    locatorUri = locator,
                    hasReadGrant = true,
                    lastSeenAt = now,
                ),
            ) { "Unable to reserve SAF artist identity" }
            val albumArtistReservation = if (albumArtistNativeId == artistNativeId) {
                artistReservation
            } else {
                checkNotNull(
                    identityDao.reserveId(
                        itemType = LibraryItemIdentityEntity.TYPE_ARTIST,
                        namespace = namespace,
                        nativeId = albumArtistNativeId,
                        locatorUri = locator,
                        hasReadGrant = true,
                        lastSeenAt = now,
                    ),
                ) { "Unable to reserve SAF album-artist identity" }
            }
            val albumReservation = checkNotNull(
                identityDao.reserveId(
                    itemType = LibraryItemIdentityEntity.TYPE_ALBUM,
                    namespace = namespace,
                    nativeId = albumNativeId,
                    locatorUri = locator,
                    hasReadGrant = true,
                    lastSeenAt = now,
                ),
            ) { "Unable to reserve SAF album identity" }

            val artist = ArtistEntity(artistReservation.entityId, artistName, trackCount = 0)
            val albumArtist = if (albumArtistReservation.entityId == artist.id) artist else {
                ArtistEntity(albumArtistReservation.entityId, albumArtistName, trackCount = 0)
            }
            val album = AlbumEntity(
                id = albumReservation.entityId,
                title = albumName,
                artistName = albumArtistName,
                artistId = albumArtist.id,
                albumArtUriString = null,
                songCount = 0,
                dateAdded = now,
                year = metadata.year ?: 0,
                albumArtist = albumArtistName,
            )
            val song = SongEntity(
                id = songReservation.entityId,
                title = title,
                artistName = artistName,
                artistId = artist.id,
                albumArtist = albumArtistName,
                albumArtistId = albumArtist.id,
                albumName = albumName,
                albumId = album.id,
                contentUriString = locator,
                albumArtUriString = null,
                duration = metadata.durationMillis?.coerceAtLeast(0L) ?: 0L,
                genre = metadata.genre,
                filePath = "",
                parentDirectoryPath = "",
                trackNumber = metadata.trackNumber ?: 0,
                year = metadata.year ?: 0,
                dateAdded = now,
                mimeType = metadata.mimeType,
                sourceType = SourceType.SAF,
            )
            val alreadyPresent = musicDao.persistSafDocument(
                song = song,
                album = album,
                artists = listOf(artist, albumArtist).distinctBy(ArtistEntity::id),
                crossRefs = listOf(SongArtistCrossRef(song.id, artist.id, isPrimary = true)),
                namespace = namespace,
                nativeId = songNativeId,
            )
            SafImportResult(
                uri = uri,
                status = if (alreadyPresent) {
                    SafImportStatus.ALREADY_PRESENT
                } else {
                    SafImportStatus.IMPORTED
                },
                songId = song.id,
            )
        }
    }
}

/** Imports explicitly selected SAF audio documents into the Room library. */
@Singleton
class SafSongImporter @Inject constructor(
    private val grantVerifier: SafPersistedReadGrantVerifier,
    private val metadataReader: SafAudioMetadataReader,
    private val identityReader: SafDocumentIdentityReader,
    private val persistence: SafSongPersistence,
) {
    suspend fun importSelected(uris: List<Uri>): SafImportReport = SafImportReport(
        results = uris.map { uri -> importOne(uri) },
    )

    private suspend fun importOne(uri: Uri): SafImportResult {
        // Acquisition and exact persisted READ verification must precede metadata or DB access.
        if (!grantVerifier.ensureReadGrant(uri)) {
            return SafImportResult(uri, SafImportStatus.FAILED, error = "Persisted READ permission unavailable")
        }
        val identity = runCatching { identityReader.read(uri) }.getOrElse { error ->
            return SafImportResult(uri, SafImportStatus.FAILED, error = error.message ?: "Invalid SAF document URI")
        }
        val metadata = runCatching { metadataReader.read(uri) }.getOrElse { error ->
            return SafImportResult(uri, SafImportStatus.FAILED, error = error.message ?: "Unable to read audio document")
        }
        if (!metadata.mimeType.orEmpty().startsWith("audio/", ignoreCase = true)) {
            return SafImportResult(uri, SafImportStatus.FAILED, error = "Selected document is not audio")
        }
        return runCatching { persistence.persist(uri, identity, metadata) }.getOrElse { error ->
            SafImportResult(uri, SafImportStatus.FAILED, error = error.message ?: "Unable to persist SAF song")
        }
    }
}
