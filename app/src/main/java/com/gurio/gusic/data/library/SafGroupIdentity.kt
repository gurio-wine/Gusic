package com.gurio.gusic.data.library

import java.util.Locale

/**
 * Role-qualified SAF group keys. Song identity remains the provider's opaque document ID; these
 * keys only group documents into shared artist/album rows when audio metadata agrees.
 */
internal object SafGroupIdentity {
    fun artistNativeId(artistName: String?, documentIdFallback: String): String =
        artistName.normalizedGroupValue()?.let { "artist:metadata:${segment(it)}" }
            ?: "artist:document:${segment(documentIdFallback)}"

    fun albumNativeId(albumName: String?, albumArtist: String?, documentIdFallback: String): String {
        val album = albumName.normalizedGroupValue()
            ?: return "album:document:${segment(documentIdFallback)}"
        val artist = albumArtist.normalizedGroupValue()
            ?: return "album:document:${segment(documentIdFallback)}"
        return "album:metadata:${segment(artist)}${segment(album)}"
    }

    private fun String?.normalizedGroupValue(): String? = this
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.lowercase(Locale.ROOT)

    private fun segment(value: String): String = "${value.length}:$value"
}
