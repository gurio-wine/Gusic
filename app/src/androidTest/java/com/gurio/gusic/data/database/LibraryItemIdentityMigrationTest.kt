package com.gurio.gusic.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryItemIdentityMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = PixelPlayerDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrationFromTenBackfillsExistingIdsWithoutRekeyingRowsOrReferences() {
        migrationHelper.createDatabase(DATABASE_NAME, 10).apply {
            execSQL("INSERT INTO artists (id, name, track_count) VALUES (20, 'Artist', 1)")
            execSQL(
                "INSERT INTO albums (id, title, artist_name, artist_id, song_count, date_added, year) " +
                    "VALUES (30, 'Album', 'Artist', 20, 1, 100, 2020)"
            )
            execSQL(
                "INSERT INTO songs (id, title, artist_name, artist_id, album_name, album_id, " +
                    "content_uri_string, duration, file_path, parent_directory_path) " +
                    "VALUES (40, 'Song', 'Artist', 20, 'Album', 30, " +
                    "'content://media/external/audio/media/40', 1000, '/music/song.mp3', '/music')"
            )
            execSQL("INSERT INTO favorites (songId, isFavorite, timestamp) VALUES (40, 1, 1234)")
            execSQL("INSERT INTO lyrics (songId, content, isSynced) VALUES (40, 'lyrics', 0)")
            execSQL(
                "INSERT INTO song_artist_cross_ref (song_id, artist_id, is_primary) " +
                    "VALUES (40, 20, 1)"
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            name = DATABASE_NAME,
            version = 11,
            validateDroppedTables = true,
            MIGRATION_10_11,
        ).use { database ->
            assertThat(database.longValue("SELECT id FROM songs")).isEqualTo(40L)
            assertThat(database.longValue("SELECT id FROM albums")).isEqualTo(30L)
            assertThat(database.longValue("SELECT id FROM artists")).isEqualTo(20L)
            assertThat(database.longValue("SELECT songId FROM favorites")).isEqualTo(40L)
            assertThat(database.longValue("SELECT songId FROM lyrics")).isEqualTo(40L)
            assertThat(database.longValue("SELECT song_id FROM song_artist_cross_ref")).isEqualTo(40L)

            assertThat(database.identityId("song", "legacy", "40")).isEqualTo(40L)
            assertThat(database.identityId("album", "legacy", "30")).isEqualTo(30L)
            assertThat(database.identityId("artist", "legacy", "20")).isEqualTo(20L)
            assertThat(database.longValue("SELECT COUNT(*) FROM library_item_identities"))
                .isEqualTo(3L)
        }
    }

    private fun SupportSQLiteDatabase.longValue(sql: String): Long =
        query(sql).use { cursor ->
            check(cursor.moveToFirst()) { "Expected a row for $sql" }
            cursor.getLong(0)
        }

    private fun SupportSQLiteDatabase.identityId(
        itemType: String,
        namespace: String,
        nativeId: String,
    ): Long = query(
        "SELECT entity_id FROM library_item_identities " +
            "WHERE item_type = ? AND source_namespace = ? AND native_id = ?",
        arrayOf(itemType, namespace, nativeId),
    ).use { cursor ->
        check(cursor.moveToFirst()) { "Missing identity $itemType/$namespace/$nativeId" }
        cursor.getLong(0)
    }

    private companion object {
        const val DATABASE_NAME = "library-item-identity-migration-test"
    }
}
