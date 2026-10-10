package com.gurio.gusic.data.library

import android.net.Uri
import android.provider.DocumentsContract
import java.net.URI

/**
 * Stable natural identity for a user-selected Storage Access Framework document.
 *
 * The URI is deliberately not part of this value: it is an access locator that can change when the
 * same provider document is granted through a different URI form or tree. [documentId] is opaque
 * and must never be parsed, normalized, or hashed to derive an application database ID.
 */
data class SafDocumentIdentity(
    val authority: String,
    val documentId: String,
) {
    init {
        require(authority.isNotBlank()) { "SAF provider authority must not be blank" }
        require(documentId.isNotEmpty()) { "SAF document ID must not be empty" }
    }

    companion object {
        /** Parse a canonical document URI, accepting both standalone and tree-wrapped documents. */
        fun fromDocumentUri(uriString: String): SafDocumentIdentity {
            validateDocumentUriShape(uriString)
            val uri = Uri.parse(uriString)
            val authority = uri.authority
            require(uri.scheme == "content" && !authority.isNullOrBlank()) {
                "SAF document URI must be a content URI with provider authority"
            }
            return SafDocumentIdentity(
                authority = authority,
                documentId = DocumentsContract.getDocumentId(uri),
            )
        }

        /** JVM-test seam: the injected extractor represents DocumentsContract.getDocumentId. */
        internal fun fromDocumentUri(
            uriString: String,
            extractDocumentId: (String) -> String,
        ): SafDocumentIdentity {
            val checked = validateDocumentUriShape(uriString)
            return SafDocumentIdentity(
                authority = checked.authority,
                documentId = extractDocumentId(uriString),
            )
        }

        private fun validateDocumentUriShape(uriString: String): CheckedDocumentUri {
            val uri = try {
                URI(uriString)
            } catch (cause: Exception) {
                throw IllegalArgumentException("Malformed SAF document URI", cause)
            }
            require(uri.scheme == "content") {
                "SAF document URI must use the content scheme"
            }
            val authority = uri.rawAuthority
            require(!authority.isNullOrBlank()) {
                "SAF document URI must include a provider authority"
            }

            val segments = uri.rawPath.orEmpty().split('/').drop(1)
            val hasDocumentId = when {
                segments.size == 2 -> segments[0] == "document" && segments[1].isNotEmpty()
                segments.size == 4 -> segments[0] == "tree" && segments[1].isNotEmpty() &&
                    segments[2] == "document" && segments[3].isNotEmpty()
                else -> false
            }
            require(hasDocumentId) { "SAF URI must identify a document" }
            return CheckedDocumentUri(authority)
        }

        private data class CheckedDocumentUri(val authority: String)
    }
}
