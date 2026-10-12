package com.gurio.gusic.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scrobble toggle. Defaults to enabled — connecting a ListenBrainz account is the consent act;
 * users disable it when their player already reports listens another way. With no account
 * connected this value has no effect.
 */
@Singleton
class ListenBrainzPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val SCROBBLE_LOCAL = booleanPreferencesKey("listenbrainz_scrobble_local")
    }

    val scrobbleLocalFlow: Flow<Boolean> = dataStore.data.map { it[Keys.SCROBBLE_LOCAL] ?: true }

    suspend fun setScrobbleLocal(enabled: Boolean) {
        dataStore.edit { it[Keys.SCROBBLE_LOCAL] = enabled }
    }

    suspend fun isSourceEnabled(@Suppress("UNUSED_PARAMETER") source: String): Boolean =
        dataStore.data.first()[Keys.SCROBBLE_LOCAL] ?: true
}

