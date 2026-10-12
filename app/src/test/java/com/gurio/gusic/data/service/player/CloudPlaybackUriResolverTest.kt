package com.gurio.gusic.data.service.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CloudPlaybackUriResolverTest {
    @Test
    fun `canonical lxmusic extra replaces a stale offline file uri`() {
        assertThat(
            selectCanonicalCloudPlaybackUri(
                playerUri = "file:///deleted/offline.flac",
                originalContentUri = "lxmusic://song-42",
            )
        ).isEqualTo("lxmusic://song-42")
    }

    @Test
    fun `canonical lxmusic extra replaces an ephemeral proxy uri`() {
        assertThat(
            selectCanonicalCloudPlaybackUri(
                playerUri = "http://127.0.0.1:54321/stream",
                originalContentUri = "lxmusic://song-42",
            )
        ).isEqualTo("lxmusic://song-42")
    }

    @Test
    fun `local files remain local without a supported cloud extra`() {
        assertThat(
            selectCanonicalCloudPlaybackUri(
                playerUri = "file:///music/local.flac",
                originalContentUri = "https://example.test/not-a-provider-id",
            )
        ).isEqualTo("file:///music/local.flac")
    }


    @Test
    fun `offline copy wins before cached and newly resolved remote urls`() = runTest {
        val calls = mutableListOf<String>()

        val result = resolvePreferredCloudPlaybackValue(
            original = "lxmusic://song",
            resolveOffline = {
                calls += "offline"
                "file:///offline/song.flac"
            },
            resolveCachedRemote = {
                calls += "cache"
                "http://127.0.0.1/cached"
            },
            resolveRemote = {
                calls += "remote"
                "http://127.0.0.1/new"
            }
        )

        assertThat(result).isEqualTo("file:///offline/song.flac")
        assertThat(calls).containsExactly("offline")
    }

    @Test
    fun `cached remote url is used only when no offline copy exists`() = runTest {
        var remoteResolutionCount = 0

        val result = resolvePreferredCloudPlaybackValue(
            original = "lxmusic://song",
            resolveOffline = { null },
            resolveCachedRemote = { "http://127.0.0.1/cached" },
            resolveRemote = {
                remoteResolutionCount += 1
                "http://127.0.0.1/new"
            }
        )

        assertThat(result).isEqualTo("http://127.0.0.1/cached")
        assertThat(remoteResolutionCount).isEqualTo(0)
    }

    @Test
    fun `new remote url is cached and original uri remains the final fallback`() = runTest {
        val cachedValues = mutableListOf<String>()

        val remoteResult = resolvePreferredCloudPlaybackValue(
            original = "lxmusic://song",
            resolveOffline = { null },
            resolveCachedRemote = { null },
            resolveRemote = { "http://127.0.0.1/new" },
            onRemoteResolved = { cachedValues += it }
        )
        val fallbackResult = resolvePreferredCloudPlaybackValue(
            original = "lxmusic://missing",
            resolveOffline = { null },
            resolveCachedRemote = { null },
            resolveRemote = { null }
        )

        assertThat(remoteResult).isEqualTo("http://127.0.0.1/new")
        assertThat(cachedValues).containsExactly("http://127.0.0.1/new")
        assertThat(fallbackResult).isEqualTo("lxmusic://missing")
    }
}
