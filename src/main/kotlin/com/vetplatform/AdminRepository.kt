
package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp

object AdminRepository {

    suspend fun getVetCredentials(
        vetUserId: Long
    ): List<AdminVetCredentialResponse> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT id, vet_user_id, document_type, original_file_name,
                   content_type, file_size, verification_status, admin_notes,
                   uploaded_at, reviewed_at
            FROM vet_credential_documents
            WHERE vet_user_id = ?
            ORDER BY uploaded_at ASC, id ASC
        """.trimIndent()

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, vetUserId)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                AdminVetCredentialResponse(
                                    id = result.getLong("id"),
                                    vetUserId = result.getLong("vet_user_id"),
                                    documentType = result.getString("document_type"),
                                    originalFileName = result.getString("original_file_name"),
                                    contentType = result.getString("content_type"),
                                    fileSize = result.getLong("file_size"),
                                    verificationStatus = result.getString("verification_status"),
                                    adminNotes = result.getString("admin_notes"),
                                    uploadedAt = result.getTimestamp("uploaded_at")
                                        .toInstant().toString(),
                                    reviewedAt = result.getTimestamp("reviewed_at")
                                        ?.toInstant()?.toString()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun reviewCredential(
        vetUserId: Long,
        documentId: Long,
        adminId: Long,
        approved: Boolean,
        adminNotes: String?
    ): AdminVetCredentialResponse? = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            val originalAutoCommit = connection.autoCommit
            try {
                connection.autoCommit = false
                ensureAdministrator(connection, adminId)

                val sql = """
                    UPDATE vet_credential_documents
                    SET verification_status = ?,
                        admin_notes = ?,
                        reviewed_by = ?,
                        reviewed_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                      AND vet_user_id = ?
                      AND verification_status = 'PENDING'
                    RETURNING id, vet_user_id, document_type, original_file_name,
                              content_type, file_size, verification_status,
                              admin_notes, uploaded_at, reviewed_at
                """.trimIndent()

                val document = connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, if (approved) "APPROVED" else "REJECTED")
                    statement.setString(2, adminNotes)
                    statement.setLong(3, adminId)
                    statement.setLong(4, documentId)
                    statement.setLong(5, vetUserId)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.toAdminCredential() else null
                    }
                }

                if (document == null) {
                    connection.rollback()
                    return@withConnection null
                }

                connection.commit()
                document
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
    }

    private fun ensureAdministrator(connection: Connection, adminId: Long) {
        val sql = "SELECT role FROM users WHERE id = ? FOR UPDATE"
        val isAdmin = connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, adminId)
            statement.executeQuery().use { result ->
                result.next() && result.getString("role") == "ADMIN"
            }
        }
        if (!isAdmin) throw SecurityException("Administrator access required")
    }

    private fun java.sql.ResultSet.toAdminCredential() = AdminVetCredentialResponse(
        id = getLong("id"),
        vetUserId = getLong("vet_user_id"),
        documentType = getString("document_type"),
        originalFileName = getString("original_file_name"),
        contentType = getString("content_type"),
        fileSize = getLong("file_size"),
        verificationStatus = getString("verification_status"),
        adminNotes = getString("admin_notes"),
        uploadedAt = getTimestamp("uploaded_at").toInstant().toString(),
        reviewedAt = getTimestamp("reviewed_at")?.toInstant()?.toString()
    )

    suspend fun getPendingVets(): List<PendingVetResponse> =
        withContext(Dispatchers.IO) {
            val sql = """
                SELECT
                    u.id AS user_id,
                    u.full_name,
                    u.email,
                    u.phone,
                    vp.qualification,
                    vp.registration_number,
                    vp.experience_years,
                    vp.consultation_fee,
                    vp.verification_status
                FROM vet_profiles vp
                JOIN users u ON u.id = vp.user_id
                WHERE vp.verification_status = 'PENDING'
                ORDER BY vp.created_at ASC
            """.trimIndent()

            DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) {
                                add(
                                    PendingVetResponse(
                                        userId = result.getLong("user_id"),
                                        fullName = result.getString("full_name"),
                                        email = result.getString("email"),
                                        phone = result.getString("phone"),
                                        qualification =
                                            result.getString("qualification"),
                                        registrationNumber =
                                            result.getString("registration_number"),
                                        experienceYears =
                                            result.getInt("experience_years"),
                                        consultationFee =
                                            result.getBigDecimal("consultation_fee")
                                                .toDouble(),
                                        verificationStatus =
                                            result.getString("verification_status")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

    suspend fun reviewVet(
        userId: Long,
        adminId: Long,
        approved: Boolean,
        rejectionReason: String? = null
    ): VetReviewResponse? = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            val originalAutoCommit = connection.autoCommit

            try {
                connection.autoCommit = false

                // Confirm that the reviewer is an administrator in the database.
                val adminSql = """
                    SELECT role
                    FROM users
                    WHERE id = ?
                    FOR UPDATE
                """.trimIndent()

                val isAdmin = connection.prepareStatement(adminSql).use { statement ->
                    statement.setLong(1, adminId)

                    statement.executeQuery().use { result ->
                        result.next() && result.getString("role") == "ADMIN"
                    }
                }

                if (!isAdmin) {
                    throw SecurityException(
                        "Only an administrator can review applications"
                    )
                }

                if (approved) {
                    val requiredCredentialsSql = """
                        SELECT
                            COUNT(*) FILTER (
                                WHERE document_type = 'DEGREE_CERTIFICATE'
                                  AND verification_status = 'APPROVED'
                            ) AS approved_degree,
                            COUNT(*) FILTER (
                                WHERE document_type = 'REGISTRATION_CERTIFICATE'
                                  AND verification_status = 'APPROVED'
                            ) AS approved_registration
                        FROM vet_credential_documents
                        WHERE vet_user_id = ?
                    """.trimIndent()

                    val hasRequiredCredentials = connection
                        .prepareStatement(requiredCredentialsSql).use { statement ->
                            statement.setLong(1, userId)
                            statement.executeQuery().use { result ->
                                result.next() &&
                                    result.getInt("approved_degree") > 0 &&
                                    result.getInt("approved_registration") > 0
                            }
                        }

                    if (!hasRequiredCredentials) {
                        throw IllegalStateException(
                            "Approve the degree and registration certificates before approving this veterinarian"
                        )
                    }
                }

                val status = if (approved) "APPROVED" else "REJECTED"

                val updateSql = """
                    UPDATE vet_profiles
                    SET
                        verification_status = ?,
                        reviewed_by = ?,
                        reviewed_at = CURRENT_TIMESTAMP,
                        rejection_reason = ?
                    WHERE user_id = ?
                      AND verification_status = 'PENDING'
                    RETURNING user_id
                """.trimIndent()

                val updatedUserId =
                    connection.prepareStatement(updateSql).use { statement ->
                        statement.setString(1, status)
                        statement.setLong(2, adminId)
                        statement.setString(3, rejectionReason)
                        statement.setLong(4, userId)

                        statement.executeQuery().use { result ->
                            if (result.next()) {
                                result.getLong("user_id")
                            } else {
                                null
                            }
                        }
                    }

                if (updatedUserId == null) {
                    connection.rollback()
                    return@withConnection null
                }

                connection.commit()

                VetReviewResponse(
                    message = if (approved) {
                        "Veterinarian application approved"
                    } else {
                        "Veterinarian application rejected"
                    },
                    userId = updatedUserId,
                    verificationStatus = status
                )
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
    }
}
