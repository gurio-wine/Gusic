package com.gurio.gusic.data.library

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SafDocumentIdentityTest {

    @Test
    fun `same authority and opaque document id identify the same document`() {
        val first = parseDocumentUri(
            "content://com.example.provider/document/music%2F001",
            "music/001",
        )
        val second = parseDocumentUri(
            "content://com.example.provider/document/music%2F001",
            "music/001",
        )

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `authority is part of document identity`() {
        val first = parseDocumentUri("content://provider.one/document/42", "42")
        val second = parseDocumentUri("content://provider.two/document/42", "42")

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `document ids remain opaque strings`() {
        val zeroPadded = parseDocumentUri("content://provider/document/001", "001")
        val unpadded = parseDocumentUri("content://provider/document/1", "1")

        assertThat(zeroPadded).isNotEqualTo(unpadded)
    }

    @Test
    fun `document and tree wrapped document uris share identity`() {
        val document = parseDocumentUri(
            "content://com.example.provider/document/music%2F001",
            "music/001",
        )
        val treeDocument = parseDocumentUri(
            "content://com.example.provider/tree/root/document/music%2F001",
            "music/001",
        )

        assertThat(treeDocument).isEqualTo(document)
        assertThat(document.documentId).isEqualTo("music/001")
    }

    @Test
    fun `non document and malformed uris are rejected`() {
        listOf(
            "https://example.com/music.mp3",
            "content://com.example.provider/tree/root",
            "content://com.example.provider/document/",
            "not a uri",
        ).forEach { uri ->
            assertThrows<IllegalArgumentException> {
                parseDocumentUri(uri, "unused")
            }
        }
    }

    @Test
    fun `same name does not affect document identity`() {
        val first = parseDocumentUri(
            "content://provider/document/folder%2Fone%2Fsong-a",
            "folder/one/song-a",
        )
        val second = parseDocumentUri(
            "content://provider/document/folder%2Ftwo%2Fsong-b",
            "folder/two/song-b",
        )

        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `empty identity components are rejected`() {
        assertThrows<IllegalArgumentException> {
            SafDocumentIdentity(authority = "provider", documentId = "")
        }
        assertThrows<IllegalArgumentException> {
            SafDocumentIdentity(authority = "", documentId = "opaque-id")
        }
    }

    private fun parseDocumentUri(uriString: String, extractedDocumentId: String) =
        SafDocumentIdentity.fromDocumentUri(uriString) { extractedDocumentId }
}
