package com.gurio.gusic.data.lxmusic

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LxScriptPolicyTest {
    @Test
    fun `default source membership is explicit and does not include every bundled source`() {
        val defaultPath = LxScriptPolicy.HELLO_WORLD_SOURCE_PATH
        val otherBundledPath = "lxmusic/sources/community.js"

        assertThat(LxScriptPolicy.isDefaultSource(defaultPath, isImported = false)).isTrue()
        assertThat(LxScriptPolicy.isDefaultSource(defaultPath, isImported = true)).isFalse()
        assertThat(LxScriptPolicy.isDefaultSource(otherBundledPath, isImported = false)).isFalse()
    }

    @Test
    fun `normalizing disabled paths removes defaults but retains unrelated disabled scripts`() {
        val unrelated = "/data/user/0/app/files/lxmusic_imported/disabled.js"

        assertThat(LxScriptPolicy.normalizeDisabledPaths(setOf(
            LxScriptPolicy.HELLO_WORLD_SOURCE_PATH,
            unrelated,
        ))).containsExactly(unrelated)
    }

    @Test
    fun `enabling imported script clears only its own disabled state`() {
        val importedPath = "/data/user/0/app/files/lxmusic_imported/new.js"
        val otherDisabledPath = "/data/user/0/app/files/lxmusic_imported/other.js"
        val defaultPath = LxScriptPolicy.HELLO_WORLD_SOURCE_PATH

        assertThat(LxScriptPolicy.enableImportedPath(
            setOf(importedPath, otherDisabledPath, defaultPath),
            importedPath,
        )).containsExactly(otherDisabledPath)
    }

    @Test
    fun `default scripts cannot be disabled or deleted while imported scripts can`() {
        val defaultScript = LxScriptMeta(
            assetPath = LxScriptPolicy.HELLO_WORLD_SOURCE_PATH,
            name = "Gusic内置-1",
            isDefault = true,
        )
        val importedScript = LxScriptMeta(
            assetPath = "/data/user/0/app/files/lxmusic_imported/new.js",
            name = "Imported",
            isImported = true,
        )

        assertThat(LxScriptPolicy.canDisable(defaultScript)).isFalse()
        assertThat(LxScriptPolicy.canDelete(defaultScript)).isFalse()
        assertThat(LxScriptPolicy.canDisable(importedScript)).isTrue()
        assertThat(LxScriptPolicy.canDelete(importedScript)).isTrue()
    }

    @Test
    fun `preferred quality defaults to master and preserves a saved custom value`() {
        val savedQuality = "legacy-custom"

        assertThat(LxQualities.preferredOrDefault(null)).isEqualTo(LxQualities.MASTER)
        assertThat(LxQualities.preferredOrDefault(savedQuality)).isEqualTo(savedQuality)
    }
}
