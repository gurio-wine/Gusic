package com.gurio.gusic.data.database

/** Pure transitions for grant-dependent locator persistence. */
internal object LibraryItemGrantPolicy {

    fun afterGrantFailure(
        existing: LibraryItemIdentityEntity?,
    ): LibraryItemIdentityEntity? = existing

    fun afterSuccessfulGrant(
        existing: LibraryItemIdentityEntity?,
        locatorUri: String,
        lastSeenAt: Long,
    ): LibraryItemIdentityEntity? = existing?.copy(
        locatorUri = locatorUri,
        hasPersistedReadGrant = true,
        isMissing = false,
        lastSeenAt = lastSeenAt,
    )
}

/** Allocates a positive candidate while respecting every ID already used by library rows/maps. */
internal object LibraryItemIdAllocator {

    fun nextAvailableId(currentHighWater: Long, occupiedIds: Set<Long>): Long {
        val highWater = maxOf(0L, currentHighWater)
        check(highWater < Long.MAX_VALUE) { "Library identity ID space exhausted" }
        var candidate = highWater + 1L
        while (candidate in occupiedIds) {
            check(candidate < Long.MAX_VALUE) { "Library identity ID space exhausted" }
            candidate++
        }
        return candidate
    }
}
