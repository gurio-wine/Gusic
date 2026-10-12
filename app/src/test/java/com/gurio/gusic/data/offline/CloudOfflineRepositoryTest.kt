package com.gurio.gusic.data.offline

import com.google.common.truth.Truth.assertThat
import com.gurio.gusic.data.model.Song
import org.junit.jupiter.api.Test

class CloudOfflineRepositoryTest {
    @Test
    fun `provider detection accepts only supported cloud schemes`() {
        assertThat(CloudOfflineRepository.providerFor("lxmusic://track_1")).isEqualTo("lxmusic")
        assertThat(CloudOfflineRepository.providerFor("LXMUSIC://track_1")).isEqualTo("lxmusic")
        assertThat(CloudOfflineRepository.providerFor("https://example.com/song.mp3")).isNull()
        assertThat(CloudOfflineRepository.providerFor("file:///music/song.mp3")).isNull()
    }

    @Test
    fun `download ids are stable and do not expose provider identifiers`() {
        val uri = "lxmusic://private-track-id"
        val first = CloudOfflineRepository.downloadId(uri)
        val second = CloudOfflineRepository.downloadId(uri)

        assertThat(first).isEqualTo(second)
        assertThat(first).hasLength(64)
        assertThat(first).doesNotContain("private-track-id")
        assertThat(first).isNotEqualTo(CloudOfflineRepository.downloadId("lxmusic://other"))
    }

    @Test
    fun `cloud song detection uses canonical playback uri`() {
        assertThat(CloudOfflineRepository.isCloudSong(song("lxmusic://abc"))).isTrue()
        assertThat(CloudOfflineRepository.isCloudSong(song("content://media/audio/1"))).isFalse()
    }

    @Test
    fun `download candidates keep only lxmusic songs while excluding local songs and duplicate uris`() {
        val lxmusic = song("lxmusic://one")
        val duplicate = song("lxmusic://one").copy(id = "duplicate")
        val other = song("lxmusic://two")
        val local = song("content://media/audio/3")

        val candidates = CloudOfflineRepository.downloadCandidates(
            listOf(lxmusic, duplicate, other, local)
        )

        assertThat(candidates).containsExactly(lxmusic, other).inOrder()
    }

    @Test
    fun `separate attempts cannot share temporary or final file names`() {
        val downloadId = CloudOfflineRepository.downloadId("lxmusic://track_1")

        val first = CloudOfflineRepository.attemptFileStem(downloadId, "attempt-a")
        val second = CloudOfflineRepository.attemptFileStem(downloadId, "attempt-b")

        assertThat(first).isNotEqualTo(second)
        assertThat("$first.part").isNotEqualTo("$second.part")
        assertThat("$first.flac").isNotEqualTo("$second.flac")
    }

    @Test
    fun `repeated batch actions do not replace queued or active downloads`() {
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = OfflineDownloadStatus.QUEUED.storageValue,
                completedFileAvailable = false
            )
        ).isFalse()
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = OfflineDownloadStatus.DOWNLOADING.storageValue,
                completedFileAvailable = false
            )
        ).isFalse()
    }

    @Test
    fun `complete download is reused only while its non-empty file is available`() {
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = OfflineDownloadStatus.COMPLETE.storageValue,
                completedFileAvailable = true
            )
        ).isFalse()
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = OfflineDownloadStatus.COMPLETE.storageValue,
                completedFileAvailable = false
            )
        ).isTrue()
    }

    @Test
    fun `failed and previously unseen downloads create a new attempt`() {
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = OfflineDownloadStatus.FAILED.storageValue,
                completedFileAvailable = false
            )
        ).isTrue()
        assertThat(
            CloudOfflineRepository.shouldStartNewAttempt(
                existingState = null,
                completedFileAvailable = false
            )
        ).isTrue()
    }

    private fun song(uri: String) = Song.emptySong().copy(contentUriString = uri)
}
