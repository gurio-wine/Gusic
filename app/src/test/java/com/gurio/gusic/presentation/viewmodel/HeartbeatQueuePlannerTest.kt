package com.gurio.gusic.presentation.viewmodel

import com.gurio.gusic.data.lxmusic.LxSources
import com.gurio.gusic.data.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class HeartbeatQueuePlannerTest {

    private fun song(
        id: String,
        source: String? = LxSources.NETEASE,
        mid: String? = id,
    ) = Song(
        id = id,
        title = id,
        artist = "artist",
        artistId = 0L,
        album = "album",
        albumId = 0L,
        path = "/$id",
        contentUriString = "content://$id",
        albumArtUriString = null,
        duration = 1000L,
        mimeType = "audio/mpeg",
        bitrate = null,
        sampleRate = null,
        lxMusicSource = source,
        lxMusicSongMid = mid,
    )

    // ---------- buildSeedPool ----------

    @Test
    fun `snapshot has enough netease songs returns them unchanged`() {
        val snapshot = (1..5).map { song("n$it") }
        val pool = HeartbeatQueuePlanner.buildSeedPool(
            snapshot = snapshot,
            currentSongMid = "n1",
            likedCandidates = listOf(song("liked1")),
            random = Random(1),
        )
        assertEquals(5, pool.size)
        assertEquals(snapshot, pool)
    }

    @Test
    fun `non netease songs are excluded from base pool`() {
        val snapshot = listOf(
            song("n1"),
            song("local1", source = null),
            song("n2"),
            song("t1", source = LxSources.TENCENT),
        )
        val pool = HeartbeatQueuePlanner.buildSeedPool(
            snapshot = snapshot,
            currentSongMid = "n1",
            likedCandidates = emptyList(),
        )
        assertEquals(listOf("n1", "n2"), pool.map { it.id })
    }

    @Test
    fun `fills up to target from liked candidates when base is short`() {
        val snapshot = listOf(song("n1"), song("n2"))
        val liked = (1..5).map { song("liked$it") }
        val pool = HeartbeatQueuePlanner.buildSeedPool(
            snapshot = snapshot,
            currentSongMid = "n1",
            likedCandidates = liked,
            random = Random(42),
        )
        assertEquals(HeartbeatQueuePlanner.SEED_POOL_TARGET, pool.size)
        assertTrue(pool.take(2).map { it.id } == listOf("n1", "n2"))
        // 补足部分均来自 liked 且无重复
        val extras = pool.drop(2).map { it.id }
        assertEquals(3, extras.size)
        assertTrue(extras.all { it.startsWith("liked") })
        assertEquals(extras.size, extras.toSet().size)
    }

    @Test
    fun `liked candidates duplicating base mids or current song are skipped`() {
        val snapshot = listOf(song("n1"), song("n2"))
        // n3 与当前歌 mid 相同（同歌不同 id）、liked_dup 与 n1 同 mid
        val liked = listOf(
            song("n3", mid = "n1"),
            song("liked_dup", mid = "n2"),
            song("liked_ok"),
            song("liked_null_mid", mid = null),
        )
        val pool = HeartbeatQueuePlanner.buildSeedPool(
            snapshot = snapshot,
            currentSongMid = "n1",
            likedCandidates = liked,
        )
        assertEquals(3, pool.size)
        assertTrue(pool.any { it.id == "liked_ok" })
        assertTrue(pool.none { it.id == "n3" || it.id == "liked_dup" || it.id == "liked_null_mid" })
    }

    @Test
    fun `returns whatever exists when liked candidates cannot fill target`() {
        val snapshot = listOf(song("n1"))
        val pool = HeartbeatQueuePlanner.buildSeedPool(
            snapshot = snapshot,
            currentSongMid = "n1",
            likedCandidates = listOf(song("liked1"), song("local", source = null)),
        )
        assertEquals(listOf("n1", "liked1"), pool.map { it.id })
    }

    // ---------- planRestoreLayout ----------

    @Test
    fun `s2 in original set restores snapshot order around s2`() {
        val snapshot = listOf(song("a"), song("b"), song("c"), song("d"))
        val originalIds = setOf("a", "b", "c", "d")
        val currentQueue = listOf(song("a"), song("x1"), song("c"), song("x2"))
        val result = HeartbeatQueuePlanner.planRestoreLayout(
            snapshot = snapshot,
            originalNeteaseIds = originalIds,
            currentQueue = currentQueue,
            s2 = song("c"),
        )
        assertNotNull(result)
        val (before, after) = result!!
        assertEquals(listOf("a", "b"), before.map { it.id })
        assertEquals(listOf("d"), after.map { it.id })
    }

    @Test
    fun `s2 not in original set anchors to nearest original song above`() {
        val snapshot = listOf(song("a"), song("b"), song("c"), song("d"))
        val originalIds = setOf("a", "b", "c", "d")
        // 队列中 s2=x 上方最近的原歌是 b；复原布局 = [a,b] + x(当前项) + [c,d]
        val currentQueue = listOf(song("a"), song("b"), song("y1"), song("x"), song("c"))
        val result = HeartbeatQueuePlanner.planRestoreLayout(
            snapshot = snapshot,
            originalNeteaseIds = originalIds,
            currentQueue = currentQueue,
            s2 = song("x"),
        )
        assertNotNull(result)
        val (before, after) = result!!
        assertEquals(listOf("a", "b"), before.map { it.id })
        assertEquals(listOf("c", "d"), after.map { it.id })
    }

    @Test
    fun `s2 not in original set and no original song above returns null`() {
        val snapshot = listOf(song("a"), song("b"))
        val originalIds = setOf("a", "b")
        val currentQueue = listOf(song("x"), song("a"))
        val result = HeartbeatQueuePlanner.planRestoreLayout(
            snapshot = snapshot,
            originalNeteaseIds = originalIds,
            currentQueue = currentQueue,
            s2 = song("x"),
        )
        assertNull(result)
    }

    @Test
    fun `s2 not in original set and missing from current queue returns null`() {
        val snapshot = listOf(song("a"), song("b"))
        val originalIds = setOf("a", "b")
        val result = HeartbeatQueuePlanner.planRestoreLayout(
            snapshot = snapshot,
            originalNeteaseIds = originalIds,
            currentQueue = listOf(song("a")),
            s2 = song("ghost"),
        )
        assertNull(result)
    }

    @Test
    fun `s2 in original set but missing from snapshot returns null`() {
        val snapshot = listOf(song("a"))
        val result = HeartbeatQueuePlanner.planRestoreLayout(
            snapshot = snapshot,
            originalNeteaseIds = setOf("a", "b"),
            currentQueue = listOf(song("a")),
            s2 = song("b"),
        )
        assertNull(result)
    }
}
