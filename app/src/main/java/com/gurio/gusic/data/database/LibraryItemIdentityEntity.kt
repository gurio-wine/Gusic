package com.gurio.gusic.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stable source identity and mutable access state for a library song, album, or artist.
 *
 * `entityId` remains the existing Long key used by the library tables and their references. The
 * composite source identity is the natural key; locator/grant state is deliberately mutable and
 * does not participate in identity.
 */
@Entity(
    tableName = "library_item_identities",
    indices = [
        Index(
            value = ["item_type", "source_namespace", "native_id"],
            unique = true,
        ),
        Index(value = ["item_type", "entity_id"], unique = true),
    ],
)
data class LibraryItemIdentityEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    val rowId: Long = 0L,
    @ColumnInfo(name = "item_type")
    val itemType: String,
    @ColumnInfo(name = "source_namespace")
    val sourceNamespace: String,
    @ColumnInfo(name = "native_id")
    val nativeId: String,
    @ColumnInfo(name = "entity_id")
    val entityId: Long,
    @ColumnInfo(name = "locator_uri")
    val locatorUri: String? = null,
    @ColumnInfo(name = "has_persisted_read_grant", defaultValue = "0")
    val hasPersistedReadGrant: Boolean = false,
    @ColumnInfo(name = "is_missing", defaultValue = "0")
    val isMissing: Boolean = false,
    @ColumnInfo(name = "last_seen_at", defaultValue = "0")
    val lastSeenAt: Long = 0L,
) {
    companion object {
        const val TYPE_SONG = "song"
        const val TYPE_ALBUM = "album"
        const val TYPE_ARTIST = "artist"
        const val LEGACY_NAMESPACE = "legacy"
        const val SAF_NAMESPACE_PREFIX = "saf:"
    }
}
