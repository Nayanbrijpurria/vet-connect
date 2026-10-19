
package com.vetplatform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.UUID

data class StoredMedicalDocument(
    val storageKey: String,
    val originalFilename: String,
    val contentType: String,
    val fileSizeBytes: Long
)

class MedicalDocumentStorage(
    storageDirectory: String = System.getenv("MEDICAL_DOCUMENT_STORAGE_DIR")
        ?: "uploads/medical-documents"
) {
    private val rootDirectory: Path =
        Paths.get(storageDirectory).toAbsolutePath().normalize()

    companion object {
        const val MAX_FILE_SIZE_BYTES: Long = 10L * 1024 * 1024

        private val ALLOWED_TYPES = mapOf(
            "pdf" to "application/pdf",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png"
        )
    }

    init {
        Files.createDirectories(rootDirectory)
    }

    fun store(
        originalFilename: String,
        contentType: String,
        bytes: ByteArray
    ): StoredMedicalDocument {
        require(originalFilename.isNotBlank()) {
            "Filename must not be empty"
        }

        require(bytes.isNotEmpty()) {
            "The uploaded file is empty"
        }

        require(bytes.size.toLong() <= MAX_FILE_SIZE_BYTES) {
            "File size must not exceed 10 MB"
        }

        val extension = originalFilename
            .substringAfterLast('.', "")
            .lowercase()

        val expectedContentType = ALLOWED_TYPES[extension]
            ?: throw IllegalArgumentException(
                "Only PDF, JPG, JPEG, and PNG files are allowed"
            )

        require(contentType.equals(expectedContentType, ignoreCase = true)) {
            "File extension and content type do not match"
        }

        val storageKey = "${UUID.randomUUID()}.$extension"
        val destination = resolveStorageKey(storageKey)

        Files.write(
            destination,
            bytes,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        )

        return StoredMedicalDocument(
            storageKey = storageKey,
            originalFilename = originalFilename
                .substringAfterLast('/')
                .substringAfterLast('\\'),
            contentType = expectedContentType,
            fileSizeBytes = bytes.size.toLong()
        )
    }

    fun read(storageKey: String): ByteArray {
        val path = resolveStorageKey(storageKey)

        require(Files.isRegularFile(path)) {
            "Medical document not found"
        }

        return Files.readAllBytes(path)
    }

    fun delete(storageKey: String) {
        val path = resolveStorageKey(storageKey)
        Files.deleteIfExists(path)
    }

    private fun resolveStorageKey(storageKey: String): Path {
        require(
            storageKey.matches(
                Regex("^[a-f0-9-]{36}\\.(pdf|jpg|jpeg|png)$")
            )
        ) {
            "Invalid storage key"
        }

        val resolvedPath = rootDirectory
            .resolve(storageKey)
            .normalize()

        require(resolvedPath.startsWith(rootDirectory)) {
            "Invalid document path"
        }

        return resolvedPath
    }
}
