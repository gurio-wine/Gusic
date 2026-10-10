package com.gurio.gusic.data.lxmusic

import com.gurio.gusic.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first

/** Persists enabling only the newly imported script, preserving unrelated disabled choices. */
internal suspend fun enableImportedLxScript(
    preferences: UserPreferencesRepository,
    assetPath: String,
): Set<String> {
    val disabled = preferences.lxMusicDisabledScriptsFlow.first()
    val enabledDisabledPaths = LxScriptPolicy.enableImportedPath(disabled, assetPath)
    preferences.setLxMusicDisabledScripts(enabledDisabledPaths)
    return enabledDisabledPaths
}
