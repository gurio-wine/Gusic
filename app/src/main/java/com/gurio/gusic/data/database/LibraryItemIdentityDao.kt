package com.gurio.gusic.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Result of reserving a stable library row ID for a qualified source identity. */
data class LibraryItemIdentityReservation(
    val entityId: Long,
    val alreadyExisted: Boolean,
)

@Dao
interface LibraryItemIdentityDao {

    @Query(
        "SELECT * FROM library_item_identities " +
            "WHERE item_type = :itemType AND source_namespace = :namespace AND native_id = :nativeId"
    )
    suspend fun findIdentity(
        itemType: String,
        namespace: String,
        nativeId: String,
    ): LibraryItemIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIdentity(identity: LibraryItemIdentityEntity): Long

    @Query("""
        UPDATE library_item_identities SET locator_uri = :locatorUri,
            has_persisted_read_grant = 1,
            is_missing = 0,
            last_seen_at = :lastSeenAt
        WHERE item_type = :itemType AND source_namespace = :namespace AND native_id = :nativeId
    """)
    suspend fun updateAccessState(
        itemType: String,
        namespace: String,
        nativeId: String,
        locatorUri: String,
        lastSeenAt: Long,
    )

    @Query("""
        SELECT MAX(id) FROM (
            SELECT id FROM songs
            UNION ALL SELECT id FROM albums
            UNION ALL SELECT id FROM artists
            UNION ALL SELECT entity_id AS id FROM library_item_identities
        )
    """)
    suspend fun maxGlobalLibraryId(): Long?

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM songs WHERE id = :id
            UNION ALL SELECT 1 FROM albums WHERE id = :id
            UNION ALL SELECT 1 FROM artists WHERE id = :id
            UNION ALL SELECT 1 FROM library_item_identities WHERE entity_id = :id
        )
    """)
    suspend fun libraryIdExists(id: Long): Boolean

    @Transaction
    suspend fun reserveId(
        itemType: String,
        namespace: String,
        nativeId: String,
        locatorUri: String,
        hasReadGrant: Boolean,
        lastSeenAt: Long,
    ): LibraryItemIdentityReservation? {
        require(namespace.isNotBlank()) { "Identity namespace must not be blank" }
        require(nativeId.isNotEmpty()) { "Native identity must not be empty" }
        if (!hasReadGrant) return null

        val existing = findIdentity(itemType, namespace, nativeId)
        if (existing != null) {
            updateAccessState(itemType, namespace, nativeId, locatorUri, lastSeenAt)
            return LibraryItemIdentityReservation(existing.entityId, alreadyExisted = true)
        }

        check(itemType in setOf(
            LibraryItemIdentityEntity.TYPE_SONG,
            LibraryItemIdentityEntity.TYPE_ALBUM,
            LibraryItemIdentityEntity.TYPE_ARTIST,
        )) { "Unsupported library identity type: $itemType" }

        var candidate = LibraryItemIdAllocator.nextAvailableId(
            currentHighWater = maxGlobalLibraryId() ?: 0L,
            occupiedIds = emptySet(),
        )
        while (libraryIdExists(candidate)) {
            check(candidate < Long.MAX_VALUE) { "Library identity ID space exhausted" }
            candidate += 1L
        }

        val rowId = insertIdentity(
            LibraryItemIdentityEntity(
                itemType = itemType,
                sourceNamespace = namespace,
                nativeId = nativeId,
                entityId = candidate,
                locatorUri = locatorUri,
                hasPersistedReadGrant = true,
                isMissing = false,
                lastSeenAt = lastSeenAt,
            ),
        )
        if (rowId == -1L) {
            val winner = checkNotNull(findIdentity(itemType, namespace, nativeId))
            return LibraryItemIdentityReservation(winner.entityId, alreadyExisted = true)
        }
        return LibraryItemIdentityReservation(candidate, alreadyExisted = false)
    }
}
