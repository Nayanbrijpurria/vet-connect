
package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.SQLException

object VetRepository {

    suspend fun registerVet(
        fullName: String,
        email: String,
        passwordHash: String,
        phone: String?,
        qualification: String,
        registrationNumber: String,
        experienceYears: Int,
        consultationFee: Double
    ): CreatedUser = withContext(Dispatchers.IO) {

        DatabaseFactory.withConnection { connection ->
            val originalAutoCommit = connection.autoCommit

            try {
                connection.autoCommit = false

                val userSql = """
                    INSERT INTO users (
                        full_name,
                        email,
                        password_hash,
                        phone,
                        role
                    )
                    VALUES (?, ?, ?, ?, 'VET')
                    RETURNING id, full_name, email, role
                """.trimIndent()

                val user = connection.prepareStatement(userSql).use { statement ->
                    statement.setString(1, fullName.trim())
                    statement.setString(2, email.trim().lowercase())
                    statement.setString(3, passwordHash)
                    statement.setString(4, phone)

                    statement.executeQuery().use { result ->
                        check(result.next()) {
                            "Veterinarian account was not created"
                        }

                        CreatedUser(
                            id = result.getLong("id"),
                            fullName = result.getString("full_name"),
                            email = result.getString("email"),
                            role = result.getString("role")
                        )
                    }
                }

                val profileSql = """
                    INSERT INTO vet_profiles (
                        user_id,
                        qualification,
                        registration_number,
                        experience_years,
                        consultation_fee,
                        verification_status
                    )
                    VALUES (?, ?, ?, ?, ?, 'PENDING')
                """.trimIndent()

                connection.prepareStatement(profileSql).use { statement ->
                    statement.setLong(1, user.id)
                    statement.setString(2, qualification.trim())
                    statement.setString(3, registrationNumber.trim())
                    statement.setInt(4, experienceYears)
                    statement.setBigDecimal(
                        5,
                        java.math.BigDecimal.valueOf(consultationFee)
                    )

                    check(statement.executeUpdate() == 1) {
                        "Veterinarian profile was not created"
                    }
                }

                connection.commit()
                user

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

// This function retrieves the logged-in doctors registration number and verification status from postgreSQL
    suspend fun getVerificationStatus(
        userId: Long
    ): VetVerificationStatusResponse? =
        withContext(Dispatchers.IO) {
            val sql = """
                SELECT user_id, registration_number, verification_status
                FROM vet_profiles
                WHERE user_id = ?
            """.trimIndent()

            DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, userId)

                    statement.executeQuery().use { result ->
                        if (result.next()) {
                            VetVerificationStatusResponse(
                                userId = result.getLong("user_id"),
                                registrationNumber =
                                    result.getString("registration_number"),
                                verificationStatus =
                                    result.getString("verification_status")
                            )
                        } else {
                            null
                        }
                    }
                }
            }
        }

    suspend fun isVetApproved(userId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM users u
                    JOIN vet_profiles vp ON vp.user_id = u.id
                    WHERE u.id = ?
                      AND u.role = 'VET'
                      AND vp.verification_status = 'APPROVED'
                )
            """.trimIndent()

            DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, userId)

                    statement.executeQuery().use { result ->
                        result.next() && result.getBoolean(1)
                    }
                }
            }
        }

    suspend fun getApprovedVets(): List<ApprovedVetResponse> =
        withContext(Dispatchers.IO) {
            val sql = """
            SELECT
                u.id AS user_id,
                u.full_name,
                vp.qualification,
                vp.experience_years,
                vp.consultation_fee
            FROM users u
            JOIN vet_profiles vp ON vp.user_id = u.id
            WHERE u.role = 'VET'
              AND vp.verification_status = 'APPROVED'
            ORDER BY u.full_name ASC
        """.trimIndent()

            DatabaseFactory.withConnection { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.executeQuery().use { result ->
                        buildList {

                            while (result.next()) {
                                add(
                                    ApprovedVetResponse(
                                        userId = result.getLong("user_id"),
                                        fullName = result.getString("full_name"),
                                        qualification = result.getString("qualification"),
                                        experienceYears = result.getInt("experience_years"),
                                        consultationFee = result.getBigDecimal(
                                            "consultation_fee"
                                        ).toDouble()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }


}
