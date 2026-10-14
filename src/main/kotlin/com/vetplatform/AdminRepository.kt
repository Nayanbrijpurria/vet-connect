
package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLException
import java.sql.Timestamp

object AdminRepository {

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
