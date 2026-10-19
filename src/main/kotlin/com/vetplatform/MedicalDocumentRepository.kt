
package com.vetplatform

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

data class MedicalDocumentRecord(
    val id: Long,
    val petId: Long,
    val consultationId: Long?,
    val uploadedBy: Long,
    val documentType: String,
    val originalFilename: String,
    val storageKey: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val processingStatus: String,
    val uploadedAt: String
)

class MedicalDocumentRepository {

    /**
     * Saves metadata for a file that has already been stored locally.
     *
     * The uploader must either:
     * - Own the pet, or
     * - Be the veterinarian assigned to the specified consultation.
     */
    suspend fun createDocument(
        uploaderUserId: Long,
        petId: Long,
        consultationId: Long?,
        documentType: String,
        storedDocument: StoredMedicalDocument
    ): MedicalDocumentRecord {

        require(uploaderUserId > 0) {
            "Invalid uploader ID"
        }
        require(petId > 0) {
            "Invalid pet ID"
        }
        require(consultationId == null || consultationId > 0) {
            "Invalid consultation ID"
        }
        require(documentType.isNotBlank()) {
            "Document type must not be empty"
        }
        require(documentType.length <= 50) {
            "Document type is too long"
        }

        return DatabaseFactory.withConnection { connection ->
            val previousAutoCommit = connection.autoCommit

            try {
                connection.autoCommit = false

                val authorized = isAuthorizedToUpload(
                    connection = connection,
                    uploaderUserId = uploaderUserId,
                    petId = petId,
                    consultationId = consultationId
                )

                if (!authorized) {
                    throw SecurityException(
                        "You are not authorized to upload a document for this pet"
                    )
                }

                val sql = """
                    INSERT INTO medical_documents (
                        pet_id,
                        consultation_id,
                        uploaded_by,
                        document_type,
                        original_filename,
                        storage_key,
                        mime_type,
                        file_size_bytes
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING
                        id,
                        pet_id,
                        consultation_id,
                        uploaded_by,
                        document_type,
                        original_filename,
                        storage_key,
                        mime_type,
                        file_size_bytes,
                        processing_status,
                        uploaded_at
                """.trimIndent()

                val record = connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, petId)

                    if (consultationId == null) {
                        statement.setNull(
                            2,
                            java.sql.Types.BIGINT
                        )
                    } else {
                        statement.setLong(2, consultationId)
                    }

                    statement.setLong(3, uploaderUserId)
                    statement.setString(4, documentType)
                    statement.setString(
                        5,
                        storedDocument.originalFilename
                    )
                    statement.setString(
                        6,
                        storedDocument.storageKey
                    )
                    statement.setString(
                        7,
                        storedDocument.contentType
                    )
                    statement.setLong(
                        8,
                        storedDocument.fileSizeBytes
                    )

                    statement.executeQuery().use { resultSet ->
                        if (!resultSet.next()) {
                            throw SQLException(
                                "Document metadata could not be saved"
                            )
                        }

                        resultSet.toMedicalDocumentRecord()
                    }
                }

                connection.commit()
                record
            } catch (exception: Exception) {
                try {
                    connection.rollback()
                } catch (rollbackException: Exception) {
                    exception.addSuppressed(rollbackException)
                }

                throw exception
            } finally {
                connection.autoCommit = previousAutoCommit
            }
        }
    }

    /**
     * Lists documents belonging to a pet only when the requesting
     * customer owns that pet.
     */
    suspend fun getDocumentsForPetOwner(
        ownerUserId: Long,
        petId: Long
    ): List<MedicalDocumentRecord> {

        require(ownerUserId > 0) {
            "Invalid owner ID"
        }
        require(petId > 0) {
            "Invalid pet ID"
        }

        return DatabaseFactory.withConnection { connection ->
            val sql = """
                SELECT
                    md.id,
                    md.pet_id,
                    md.consultation_id,
                    md.uploaded_by,
                    md.document_type,
                    md.original_filename,
                    md.storage_key,
                    md.mime_type,
                    md.file_size_bytes,
                    md.processing_status,
                    md.uploaded_at
                FROM medical_documents md
                INNER JOIN pets p
                    ON p.id = md.pet_id
                WHERE p.id = ?
                  AND p.owner_id = ?
                ORDER BY md.uploaded_at DESC, md.id DESC
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, petId)
                statement.setLong(2, ownerUserId)

                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(resultSet.toMedicalDocumentRecord())
                        }
                    }
                }
            }
        }
    }


    /**
     * Retrieves one document only if the requesting customer owns its pet.
     */
    suspend fun getDocumentForPetOwner(
        ownerUserId: Long,
        documentId: Long
    ): MedicalDocumentRecord? {

        require(ownerUserId > 0) {
            "Invalid owner ID"
        }

        require(documentId > 0) {
            "Invalid document ID"
        }

        return DatabaseFactory.withConnection { connection ->
            val sql = """
                SELECT
                    md.id,
                    md.pet_id,
                    md.consultation_id,
                    md.uploaded_by,
                    md.document_type,
                    md.original_filename,
                    md.storage_key,
                    md.mime_type,
                    md.file_size_bytes,
                    md.processing_status,
                    md.uploaded_at
                FROM medical_documents md
                INNER JOIN pets p
                    ON p.id = md.pet_id
                WHERE md.id = ?
                  AND p.owner_id = ?
            """.trimIndent()

            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, documentId)
                statement.setLong(2, ownerUserId)

                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        resultSet.toMedicalDocumentRecord()
                    } else {
                        null
                    }
                }
            }
        }
    }


    private fun isAuthorizedToUpload(
        connection: Connection,
        uploaderUserId: Long,
        petId: Long,
        consultationId: Long?
    ): Boolean {

        val sql = """
            SELECT EXISTS (
                SELECT 1
                FROM pets p
                WHERE p.id = ?
                  AND p.owner_id = ?
                  AND (
                      ? IS NULL
                      OR EXISTS (
                          SELECT 1
                          FROM consultations c
                          WHERE c.id = ?
                            AND c.pet_id = p.id
                      )
                  )
            )
            OR EXISTS (
                SELECT 1
                FROM consultations c
                INNER JOIN vet_profiles vp
                    ON vp.id = c.vet_profile_id
                WHERE c.id = ?
                  AND c.pet_id = ?
                  AND vp.user_id = ?
                  AND vp.verification_status = 'APPROVED'
            )
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, petId)
            statement.setLong(2, uploaderUserId)

            if (consultationId == null) {
                statement.setNull(3, java.sql.Types.BIGINT)
                statement.setNull(4, java.sql.Types.BIGINT)
                statement.setNull(5, java.sql.Types.BIGINT)
            } else {
                statement.setLong(3, consultationId)
                statement.setLong(4, consultationId)
                statement.setLong(5, consultationId)
            }

            statement.setLong(6, petId)
            statement.setLong(7, uploaderUserId)

            statement.executeQuery().use { resultSet ->
                return resultSet.next() && resultSet.getBoolean(1)
            }
        }
    }

    private fun ResultSet.toMedicalDocumentRecord():
            MedicalDocumentRecord {

        return MedicalDocumentRecord(
            id = getLong("id"),
            petId = getLong("pet_id"),
            consultationId = getLong("consultation_id")
                .let { if (wasNull()) null else it },
            uploadedBy = getLong("uploaded_by"),
            documentType = getString("document_type"),
            originalFilename = getString("original_filename"),
            storageKey = getString("storage_key"),
            mimeType = getString("mime_type"),
            fileSizeBytes = getLong("file_size_bytes"),
            processingStatus = getString("processing_status"),
            uploadedAt = getString("uploaded_at")
        )
    }
}
