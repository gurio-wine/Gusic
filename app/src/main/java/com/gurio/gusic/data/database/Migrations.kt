package com.gurio.gusic.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Returns true if [table] already has a column named [column].
 *
 * Platform Auto Backup can restore a database that reports the right schema version but whose
 * columns have drifted, so migrations guard each `ALTER` with this check instead of assuming a
 * bare `ADD COLUMN` is safe.
 */
private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex < 0) return false
        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) return true
        }
    }
    return false
}

private fun SupportSQLiteDatabase.addColumnIfMissing(table: String, column: String, ddl: String) {
    if (!hasColumn(table, column)) {
        execSQL("ALTER TABLE `$table` ADD COLUMN $ddl")
    }
}

/**
 * v1 -> v2: album-artist support for the unified library (issue #8).
 *
 * - `songs.album_artist_id`: id of the *effective* album artist (the song's `album_artist` when
 *   present, otherwise its primary track artist). Source-independent, so the "Group by Album
 *   Artist" Artists tab can collapse on it at runtime without forcing a re-sync.
 * - `navidrome_songs.album_artist` / `jellyfin_songs.album_artist`: carry the server's
 *   album-artist tag through the cloud cache so the unified projection can populate the above.
 *
 * Additive and idempotent — each column is added only when missing. `album_artist_id` is then
 * backfilled to the existing primary artist so the collapsed Artists tab is populated before the
 * next library sync recomputes precise values (e.g. collapsing compilations under one artist).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.addColumnIfMissing("songs", "album_artist_id", "`album_artist_id` INTEGER NOT NULL DEFAULT 0")
        db.addColumnIfMissing("navidrome_songs", "album_artist", "`album_artist` TEXT")
        db.addColumnIfMissing("jellyfin_songs", "album_artist", "`album_artist` TEXT")

        db.execSQL("UPDATE songs SET album_artist_id = artist_id WHERE album_artist_id = 0")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_album_artist_id` ON `songs` (`album_artist_id`)")
    }
}

/**
 * v2 -> v3: opt-in ListenBrainz scrobbling + MusicBrainz identifier storage.
 *
 * - `listenbrainz_pending_listens`: offline scrobble queue. Rows snapshot track metadata at
 *   enqueue time and are deleted on successful submission or permanent rejection.
 * - `songs.mb_recording_id` / `mb_release_id` / `mb_artist_id`: MusicBrainz identifiers, read
 *   from embedded file tags or applied via tag lookup. Scrobbles carry the recording MBID when
 *   known for better server-side matching.
 *
 * Additive and idempotent, per the Auto Backup drift guard above.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `listenbrainz_pending_listens` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `listened_at_ms` INTEGER NOT NULL,
                    `track_name` TEXT NOT NULL,
                    `artist_name` TEXT NOT NULL,
                    `release_name` TEXT,
                    `duration_ms` INTEGER,
                    `recording_mbid` TEXT,
                    `source` TEXT NOT NULL,
                    `attempts` INTEGER NOT NULL DEFAULT 0,
                    `created_at_ms` INTEGER NOT NULL
                )
            """.trimIndent()
        )

        db.addColumnIfMissing("songs", "mb_recording_id", "`mb_recording_id` TEXT")
        db.addColumnIfMissing("songs", "mb_release_id", "`mb_release_id` TEXT")
        db.addColumnIfMissing("songs", "mb_artist_id", "`mb_artist_id` TEXT")
    }
}

/**
 * v3 -> v4: audio bookmarks — named position markers inside a track (audiobooks, DJ sets,
 * podcasts), grouped per song on the Bookmarks screen.
 *
 * `audio_bookmarks` snapshots song metadata at save time so a bookmark stays presentable even
 * if the underlying song leaves the library. Additive and idempotent, per the Auto Backup
 * drift guard above.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `audio_bookmarks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `song_id` TEXT NOT NULL,
                    `song_title` TEXT NOT NULL,
                    `artist_name` TEXT NOT NULL,
                    `album_art_uri` TEXT,
                    `title` TEXT NOT NULL,
                    `timestamp_ms` INTEGER NOT NULL,
                    `created_time` INTEGER NOT NULL
                )
            """.trimIndent()
        )
    }
}

/** v4 -> v5: app-private offline copies of Navidrome and Jellyfin tracks. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `offline_tracks` (
                    `download_id` TEXT NOT NULL,
                    `attempt_id` TEXT NOT NULL,
                    `song_id` TEXT NOT NULL,
                    `source_uri` TEXT NOT NULL,
                    `provider` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `mime_type` TEXT,
                    `local_path` TEXT,
                    `state` TEXT NOT NULL,
                    `bytes_downloaded` INTEGER NOT NULL,
                    `total_bytes` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `error_message` TEXT,
                    PRIMARY KEY(`download_id`)
                )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_offline_tracks_source_uri` " +
                "ON `offline_tracks` (`source_uri`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_offline_tracks_song_id` " +
                "ON `offline_tracks` (`song_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_offline_tracks_state` " +
                "ON `offline_tracks` (`state`)"
        )
    }
}

/**
 * v5 -> v6: playlist positions are unique, while song ids may repeat.
 *
 * M3U playlists can intentionally contain the same track more than once. The previous
 * `(playlist_id, song_id)` primary key silently collapsed those entries during persistence.
 * Existing rows are copied in their current order and assigned dense positions so even legacy
 * databases with duplicate sort values migrate without dropping a song.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `playlist_songs_v6`")
        db.execSQL(
            """
                CREATE TABLE `playlist_songs_v6` (
                    `playlist_id` TEXT NOT NULL,
                    `song_id` TEXT NOT NULL,
                    `sort_order` INTEGER NOT NULL,
                    PRIMARY KEY(`playlist_id`, `sort_order`)
                )
            """.trimIndent()
        )
        db.execSQL(
            """
                INSERT INTO `playlist_songs_v6` (`playlist_id`, `song_id`, `sort_order`)
                SELECT
                    `playlist_id`,
                    `song_id`,
                    ROW_NUMBER() OVER (
                        PARTITION BY `playlist_id`
                        ORDER BY `sort_order`, `rowid`
                    ) - 1
                FROM `playlist_songs`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `playlist_songs`")
        db.execSQL("ALTER TABLE `playlist_songs_v6` RENAME TO `playlist_songs`")
        db.execSQL(
            "CREATE INDEX `index_playlist_songs_playlist_id_sort_order` " +
                "ON `playlist_songs` (`playlist_id`, `sort_order`)"
        )
        db.execSQL(
            "CREATE INDEX `index_playlist_songs_song_id` ON `playlist_songs` (`song_id`)"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `lx_hash` TEXT")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val lo = -5347483647L
        val hi = -3000000000L
        db.execSQL("DELETE FROM `playlist_songs` WHERE `song_id` BETWEEN ? AND ?", arrayOf(lo, hi))
        db.execSQL("DELETE FROM `song_artist_cross_ref` WHERE `song_id` BETWEEN ? AND ?", arrayOf(lo, hi))
        db.execSQL("DELETE FROM `song_artist_cross_ref` WHERE `artist_id` BETWEEN ? AND ?", arrayOf(lo, hi))
        db.execSQL("DELETE FROM `songs` WHERE `id` BETWEEN ? AND ?", arrayOf(lo, hi))
        db.execSQL("DELETE FROM `albums` WHERE `id` BETWEEN ? AND ?", arrayOf(lo, hi))
        db.execSQL("DELETE FROM `artists` WHERE `id` BETWEEN ? AND ?", arrayOf(lo, hi))
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `offline_tracks` ADD COLUMN `quality` TEXT")
        db.execSQL("ALTER TABLE `offline_tracks` ADD COLUMN `script_path` TEXT")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `songs` ADD COLUMN `lx_extra_fields_json` TEXT")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
                CREATE TABLE IF NOT EXISTS `library_item_identities` (
                    `row_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `item_type` TEXT NOT NULL,
                    `source_namespace` TEXT NOT NULL,
                    `native_id` TEXT NOT NULL,
                    `entity_id` INTEGER NOT NULL,
                    `locator_uri` TEXT,
                    `has_persisted_read_grant` INTEGER NOT NULL DEFAULT 0,
                    `is_missing` INTEGER NOT NULL DEFAULT 0,
                    `last_seen_at` INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_library_item_identities_item_type_source_namespace_native_id` " +
                "ON `library_item_identities` (`item_type`, `source_namespace`, `native_id`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_library_item_identities_item_type_entity_id` " +
                "ON `library_item_identities` (`item_type`, `entity_id`)"
        )
        db.execSQL(
            "INSERT INTO `library_item_identities` " +
                "(`item_type`, `source_namespace`, `native_id`, `entity_id`) " +
                "SELECT 'song', 'legacy', CAST(`id` AS TEXT), `id` FROM `songs`"
        )
        db.execSQL(
            "INSERT INTO `library_item_identities` " +
                "(`item_type`, `source_namespace`, `native_id`, `entity_id`) " +
                "SELECT 'album', 'legacy', CAST(`id` AS TEXT), `id` FROM `albums`"
        )
        db.execSQL(
            "INSERT INTO `library_item_identities` " +
                "(`item_type`, `source_namespace`, `native_id`, `entity_id`) " +
                "SELECT 'artist', 'legacy', CAST(`id` AS TEXT), `id` FROM `artists`"
        )
    }
}

/**
 * v11 -> v12: 音频书签功能整体移除，删除对应表。
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `audio_bookmarks`")
    }
}

/**
 * v12 -> v13: 云端串流（Navidrome/Subsonic + Jellyfin）功能整体移除。
 *
 * - 删除四张云端缓存表。
 * - 清理 `songs` 中 source_type IN (5, 6) 的历史云端歌曲，并按
 *   [MusicDao.deleteSongsAndRelatedData] 的级联顺序清理关联表
 *   （cross-ref / favorites / lyrics / playlist_songs / 离线下载记录），
 *   最后回收孤儿专辑与艺人并刷新计数。
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `navidrome_songs`")
        db.execSQL("DROP TABLE IF EXISTS `navidrome_playlists`")
        db.execSQL("DROP TABLE IF EXISTS `jellyfin_songs`")
        db.execSQL("DROP TABLE IF EXISTS `jellyfin_playlists`")

        db.execSQL(
            "DELETE FROM `song_artist_cross_ref` " +
                "WHERE `song_id` IN (SELECT `id` FROM `songs` WHERE `source_type` IN (5, 6))"
        )
        db.execSQL(
            "DELETE FROM `favorites` " +
                "WHERE `songId` IN (SELECT `id` FROM `songs` WHERE `source_type` IN (5, 6))"
        )
        db.execSQL(
            "DELETE FROM `lyrics` " +
                "WHERE `songId` IN (SELECT `id` FROM `songs` WHERE `source_type` IN (5, 6))"
        )
        db.execSQL(
            "DELETE FROM `playlist_songs` " +
                "WHERE `song_id` IN (SELECT CAST(`id` AS TEXT) FROM `songs` WHERE `source_type` IN (5, 6))"
        )
        db.execSQL(
            "DELETE FROM `offline_tracks` " +
                "WHERE `provider` IN ('navidrome', 'jellyfin') " +
                "OR `source_uri` LIKE 'navidrome://%' OR `source_uri` LIKE 'jellyfin://%'"
        )
        db.execSQL("DELETE FROM `songs` WHERE `source_type` IN (5, 6)")

        db.execSQL(
            "DELETE FROM `albums` WHERE NOT EXISTS (SELECT 1 FROM `songs` WHERE `songs`.`album_id` = `albums`.`id`)"
        )
        db.execSQL(
            "DELETE FROM `artists` WHERE " +
                "NOT EXISTS (SELECT 1 FROM `song_artist_cross_ref` WHERE `song_artist_cross_ref`.`artist_id` = `artists`.`id`) " +
                "AND NOT EXISTS (SELECT 1 FROM `songs` WHERE `songs`.`artist_id` = `artists`.`id`) " +
                "AND NOT EXISTS (SELECT 1 FROM `songs` WHERE `songs`.`album_artist_id` = `artists`.`id`)"
        )
        db.execSQL(
            "UPDATE `albums` SET `song_count` = " +
                "(SELECT COUNT(*) FROM `songs` WHERE `songs`.`album_id` = `albums`.`id`)"
        )
        db.execSQL(
            "UPDATE `artists` SET `track_count` = " +
                "(SELECT COUNT(DISTINCT `song_artist_cross_ref`.`song_id`) FROM `song_artist_cross_ref` " +
                "WHERE `song_artist_cross_ref`.`artist_id` = `artists`.`id`)"
        )
        // 云端串流已删：清掉 ListenBrainz 待上报队列里由旧云源开关产生的行
        // （历史 source 值为大写 NAVIDROME/JELLYFIN，LOCAL 行不受影响）
        db.execSQL(
            "DELETE FROM `listenbrainz_pending_listens` WHERE `source` IN ('NAVIDROME', 'JELLYFIN')"
        )
    }
}

/**
 * v13 -> v14: ListenBrainz 听歌记录上报（scrobble）功能整体移除，删除对应队列表。
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `listenbrainz_pending_listens`")
    }
}
