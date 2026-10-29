
package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.SQLException
import java.util.UUID

@kotlinx.serialization.Serializable
data class VetCredentialUploadResponse(
    val message: String,
    val documents: List<VetCredentialDocumentResponse>
)
data class VetCredentialUpload(
    val documentType: String,
    val originalFileName: String,
    val contentType: String,
    val bytes: ByteArray
)
@kotlinx.serialization.Serializable
data class VetCredentialDocumentResponse(
    val id: Long,
    val documentType: String,
    val originalFileName: String,
    val contentType: String,
    val fileSize: Long,
    val verificationStatus: String,
    val uploadedAt: String
)

data class VetCredentialFileDownload(
    val originalFileName: String,
    val contentType: String,
    val bytes: ByteArray
)

object VetCredentialDocumentService {

    const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
    const val MAX_FILES_PER_UPLOAD = 5

    private val allowedExtensions = setOf("pdf", "jpg", "jpeg", "png")

    private val storageDirectory: Path
        get() {
            val configuredDirectory = System.getenv("VET_CREDENTIAL_STORAGE_DIR")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            return if (configuredDirectory != null) {
                Path.of(configuredDirectory).toAbsolutePath().normalize()
            } else {
                Path.of("private-uploads", "vet-credentials")
                    .toAbsolutePath()
                    .normalize()
            }
        }

    private fun validateUpload(upload: VetCredentialUpload) {
        val safeOriginalName = upload.originalFileName
            .substringAfterLast('/')
            .substringAfterLast('\\')

        if (safeOriginalName.isBlank() || safeOriginalName.length > 255) {
            throw IllegalArgumentException("Invalid document filename")
        }

        val extension = safeOriginalName
            .substringAfterLast('.', "")
            .lowercase()

        if (extension !in allowedExtensions) {
            throw IllegalArgumentException(
                "Only PDF, JPG, JPEG and PNG files are allowed"
            )
        }

        if (upload.bytes.isEmpty()) {
            throw IllegalArgumentException("Uploaded file is empty")
        }

        if (upload.bytes.size.toLong() > MAX_FILE_SIZE_BYTES) {
            throw IllegalArgumentException("Each file must not exceed 10 MB")
        }

        val detectedType = detectContentType(upload.bytes)

        if (detectedType == null) {
            throw IllegalArgumentException(
                "The uploaded file is not a valid PDF, JPG or PNG file"
            )
        }

        val extensionMatches = when (extension) {
            "pdf" -> detectedType == "application/pdf"
            "jpg", "jpeg" -> detectedType == "image/jpeg"
            "png" -> detectedType == "image/png"
            else -> false
        }

        if (!extensionMatches) {
            throw IllegalArgumentException(
                "The file extension does not match the file contents"
            )
        }
    }

    private fun detectContentType(bytes: ByteArray): String? {
        if (
            bytes.size >= 5 &&
            bytes[0] == '%'.code.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'D'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[4] == '-'.code.toByte()
        ) {
            return "application/pdf"
        }

        if (
            bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }

        if (
            bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() &&
            bytes[4] == 0x0D.toByte() &&
            bytes[5] == 0x0A.toByte() &&
            bytes[6] == 0x1A.toByte() &&
            bytes[7] == 0x0A.toByte()
        ) {
            return "image/png"
        }

        return null
    }

    suspend fun uploadDocuments(
        vetUserId: Long,
        uploads: List<VetCredentialUpload>
    ): List<VetCredentialDocumentResponse> = withContext(Dispatchers.IO) {

        if (vetUserId <= 0) {
            throw IllegalArgumentException("Invalid veterinarian account")
        }

        if (uploads.isEmpty() || uploads.size > MAX_FILES_PER_UPLOAD) {
            throw IllegalArgumentException(
                "Upload between 1 and $MAX_FILES_PER_UPLOAD documents"
            )
        }

        val allowedDocumentTypes = setOf(
            "DEGREE_CERTIFICATE",
            "REGISTRATION_CERTIFICATE",
            "ADDITIONAL_CERTIFICATE"
        )

        uploads.forEach { upload ->
            if (upload.documentType !in allowedDocumentTypes) {
                throw IllegalArgumentException("Invalid document type")
            }
            validateUpload(upload)
        }

        if (
            uploads.count { it.documentType == "DEGREE_CERTIFICATE" } != 1 ||
            uploads.count { it.documentType == "REGISTRATION_CERTIFICATE" } != 1
        ) {
            throw IllegalArgumentException(
                "A degree certificate and a registration certificate are required"
            )
        }

        val directory = storageDirectory
        Files.createDirectories(directory)

        val normalizedDirectory = directory.toRealPath()

        val storedFiles = mutableListOf<Path>()
        val responses = mutableListOf<VetCredentialDocumentResponse>()
        var transactionCommitted = false

        try {
            DatabaseFactory.withConnection { connection ->
                val originalAutoCommit = connection.autoCommit

                try {
                    connection.autoCommit = false

                    for (upload in uploads) {
                        val extension = upload.originalFileName
                            .substringAfterLast('.', "")
                            .lowercase()

                        val storedName = "${UUID.randomUUID()}.$extension"
                        val target = normalizedDirectory.resolve(storedName).normalize()

                        if (target.parent != normalizedDirectory) {
                            throw SecurityException("Invalid storage path")
                        }

                        Files.write(target, upload.bytes)
                        storedFiles.add(target)

                        val sql = """
                            INSERT INTO vet_credential_documents (
                                vet_user_id,
                                document_type,
                                original_file_name,
                                stored_file_name,
                                content_type,
                                file_size
                            )
                            VALUES (?, ?, ?, ?, ?, ?)
                            RETURNING
                                id,
                                document_type,
                                original_file_name,
                                content_type,
                                file_size,
                                verification_status,
                                uploaded_at
                        """.trimIndent()

                        connection.prepareStatement(sql).use { statement ->
                            statement.setLong(1, vetUserId)
                            statement.setString(2, upload.documentType)
                            statement.setString(
                                3,
                                upload.originalFileName
                                    .substringAfterLast('/')
                                    .substringAfterLast('\\')
                            )
                            statement.setString(4, storedName)
                            statement.setString(
                                5,
                                detectContentType(upload.bytes)
                                    ?: throw IllegalArgumentException(
                                        "Unsupported document contents"
                                    )
                            )
                            statement.setLong(6, upload.bytes.size.toLong())

                            statement.executeQuery().use { result ->
                                if (!result.next()) {
                                    throw SQLException(
                                        "Document metadata was not returned"
                                    )
                                }

                                responses.add(
                                    VetCredentialDocumentResponse(
                                        id = result.getLong("id"),
                                        documentType = result.getString(
                                            "document_type"
                                        ),
                                        originalFileName = result.getString(
                                            "original_file_name"
                                        ),
                                        contentType = result.getString(
                                            "content_type"
                                        ),
                                        fileSize = result.getLong("file_size"),
                                        verificationStatus = result.getString(
                                            "verification_status"
                                        ),
                                        uploadedAt = result.getTimestamp(
                                            "uploaded_at"
                                        ).toInstant().toString()
                                    )
                                )
                            }
                        }
                    }

                    connection.commit()
                    transactionCommitted = true
                } catch (exception: Exception) {
                    try {
                        connection.rollback()
                    } catch (rollbackException: Exception) {
                        exception.addSuppressed(rollbackException)
                    }
                    throw exception
                } finally {
                    connection.autoCommit = originalAutoCommit
                }
            }

            responses
        } catch (exception: Exception) {
            if (!transactionCommitted) {
                storedFiles.forEach { file ->
                    try {
                        Files.deleteIfExists(file)
                    } catch (cleanupException: IOException) {
                        exception.addSuppressed(cleanupException)
                    }
                }
            }

            throw exception
        }
    }
    suspend fun getDocuments(
        vetUserId: Long
    ): List<VetCredentialDocumentResponse> = withContext(Dispatchers.IO) {

        DatabaseFactory.withConnection { connection ->
            val sql = """
                SELECT
                    id,
                    document_type,
                    original_file_name,
                    content_type,
                    file_size,
                    verification_status,
                    uploaded_at
                FROM vet_credential_documents
                WHERE vet_user_id = ?
                ORDER BY uploaded_at DESC
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, vetUserId)

                statement.executeQuery().use { result ->
                    val documents =
                        mutableListOf<VetCredentialDocumentResponse>()

                    while (result.next()) {
                        documents.add(
                            VetCredentialDocumentResponse(
                                id = result.getLong("id"),
                                documentType = result.getString("document_type"),
                                originalFileName =
                                    result.getString("original_file_name"),
                                contentType = result.getString("content_type"),
                                fileSize = result.getLong("file_size"),
                                verificationStatus =
                                    result.getString("verification_status"),
                                uploadedAt = result.getTimestamp("uploaded_at")
                                    .toInstant()
                                    .toString()
                            )
                        )
                    }

                    documents
                }
            }
        }
    }

    suspend fun getDocumentFileForAdmin(
        vetUserId: Long,
        documentId: Long
    ): VetCredentialFileDownload? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT stored_file_name, original_file_name, content_type
            FROM vet_credential_documents
            WHERE id = ? AND vet_user_id = ?
        """.trimIndent()

        val fileDetails = DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, documentId)
                statement.setLong(2, vetUserId)
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        Triple(
                            result.getString("stored_file_name"),
                            result.getString("original_file_name"),
                            result.getString("content_type")
                        )
                    } else {
                        null
                    }
                }
            }
        } ?: return@withContext null

        val normalizedDirectory = storageDirectory.toRealPath()
        val storedPath = normalizedDirectory
            .resolve(fileDetails.first)
            .normalize()

        if (storedPath.parent != normalizedDirectory) {
            throw SecurityException("Invalid credential storage path")
        }

        val realFile = storedPath.toRealPath()
        if (realFile.parent != normalizedDirectory || !Files.isRegularFile(realFile)) {
            throw SecurityException("Invalid credential storage file")
        }

        VetCredentialFileDownload(
            originalFileName = fileDetails.second,
            contentType = fileDetails.third,
            bytes = Files.readAllBytes(realFile)
        )
    }

}
