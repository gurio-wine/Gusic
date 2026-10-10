package com.gurio.gusic.data.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** One persisted URI permission as exposed by ContentResolver. */
data class PersistedUriReadPermission(
    val uri: Uri,
    val isReadPermission: Boolean,
)

/** Small seam around Android's persistable URI permission APIs for JVM tests. */
interface PersistedUriPermissionStore {
    fun takePersistableReadPermission(uri: Uri)
    fun persistedUriReadPermissions(): List<PersistedUriReadPermission>
}

class ContentResolverPersistedUriPermissionStore @Inject constructor(
    @ApplicationContext context: Context,
) : PersistedUriPermissionStore {
    private val contentResolver: ContentResolver = context.contentResolver

    override fun takePersistableReadPermission(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun persistedUriReadPermissions(): List<PersistedUriReadPermission> =
        contentResolver.persistedUriPermissions.map { permission ->
            PersistedUriReadPermission(permission.uri, permission.isReadPermission)
        }
}

/** Acquires and verifies a persisted READ grant for the exact selected document URI. */
class SafPersistedReadGrantVerifier @Inject constructor(
    private val permissionStore: PersistedUriPermissionStore,
) {
    fun ensureReadGrant(uri: Uri): Boolean {
        // Even if acquisition throws (e.g. the picker did not return persistable flags), an
        // already-persisted exact READ grant is sufficient. No caller may mutate stored locators
        // unless the subsequent permission snapshot proves that grant exists.
        runCatching { permissionStore.takePersistableReadPermission(uri) }
        return runCatching {
            permissionStore.persistedUriReadPermissions().any { permission ->
                permission.isReadPermission && permission.uri == uri
            }
        }.getOrDefault(false)
    }
}
