
package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.time.OffsetDateTime

object ConsultationRepository {

    /**
     * Creates a consultation for an appointment assigned to this veterinarian.
     * The veterinarian's user ID must come from the authenticated session.
     */
    suspend fun createConsultation(
        vetUserId: Long,
        request: CreateConsultationRequest
    ): ConsultationResponse = withContext(Dispatchers.IO) {

        require(request.appointmentId > 0) {
            "A valid appointment ID is required"
        }
        require(listOf(request.chiefComplaint, request.clinicalNotes, request.diagnosis, request.treatmentNotes, request.followUpNotes).any { !it.isNullOrBlank() }) {
            "At least one consultation field must be completed"
        }

        DatabaseFactory.withConnection { connection ->
            val originalAutoCommit = connection.autoCommit

            try {
                connection.autoCommit = false

                val consultation = connection.prepareStatement(
                    """
                    SELECT
                        a.id AS appointment_id,
                        a.pet_id,
                        a.vet_profile_id,
                        a.scheduled_end,
                        a.status,
                        vp.user_id AS vet_user_id
                    FROM appointments a
                    JOIN vet_profiles vp
                        ON vp.id = a.vet_profile_id
                    JOIN users u ON u.id = vp.user_id
                    WHERE a.id = ?
                      AND vp.verification_status = 'APPROVED'
                      AND u.account_status = 'ACTIVE'
                    FOR UPDATE OF a
                    """.trimIndent()
                ).use { statement ->

                    statement.setLong(1, request.appointmentId)

                    statement.executeQuery().use { rs ->
                        if (!rs.next()) {
                            throw IllegalArgumentException(
                                "Appointment not found"
                            )
                        }

                        val assignedVetUserId =
                            rs.getLong("vet_user_id")

                        if (assignedVetUserId != vetUserId) {
                            throw SecurityException(
                                "You are not authorized to access this appointment"
                            )
                        }
                        val appointmentStatus = rs.getString("status")
                        val scheduledEnd = rs.getObject(
                            "scheduled_end",
                            OffsetDateTime::class.java
                        )

                        require(appointmentStatus == "COMPLETED") {
                            "A consultation can only be created for a completed appointment"
                        }

                        require(!scheduledEnd.isAfter(OffsetDateTime.now())) {
                            "The appointment must have ended before creating a consultation"
                        }

                        val petId = rs.getLong("pet_id")
                        val vetProfileId = rs.getLong("vet_profile_id")

                        val insertSql =
                            """
                            INSERT INTO consultations (
                                appointment_id,
                                pet_id,
                                vet_profile_id,
                                chief_complaint,
                                clinical_notes,
                                diagnosis,
                                treatment_notes,
                                follow_up_notes
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            RETURNING
                                id,
                                appointment_id,
                                pet_id,
                                vet_profile_id,
                                chief_complaint,
                                clinical_notes,
                                diagnosis,
                                treatment_notes,
                                follow_up_notes,
                                created_at,
                                updated_at
                            """.trimIndent()

                        connection.prepareStatement(insertSql).use { insert ->
                            insert.setLong(1, request.appointmentId)
                            insert.setLong(2, petId)
                            insert.setLong(3, vetProfileId)
                            insert.setString(4, request.chiefComplaint)
                            insert.setString(5, request.clinicalNotes)
                            insert.setString(6, request.diagnosis)
                            insert.setString(7, request.treatmentNotes)
                            insert.setString(8, request.followUpNotes)

                            insert.executeQuery().use { result ->
                                check(result.next()) {
                                    "Consultation could not be created"
                                }

                                result.toConsultationResponse()
                            }
                        }
                    }
                }

                connection.prepareStatement("""INSERT INTO audit_logs(actor_user_id, action, entity_type, entity_id, details)
                    VALUES (?, 'VET_CREATE_CONSULTATION', 'CONSULTATION', ?, jsonb_build_object('appointmentId', ?, 'petId', ?))""").use { audit ->
                    audit.setLong(1, vetUserId); audit.setLong(2, consultation.id); audit.setLong(3, consultation.appointmentId); audit.setLong(4, consultation.petId); audit.executeUpdate()
                }

                connection.commit()
                consultation

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

    /**
     * Returns consultations belonging to a veterinarian.
     */
    suspend fun getVetConsultations(
        vetUserId: Long
    ): List<ConsultationResponse> = withContext(Dispatchers.IO) {

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT
                    c.id,
                    c.appointment_id,
                    c.pet_id,
                    c.vet_profile_id,
                    c.chief_complaint,
                    c.clinical_notes,
                    c.diagnosis,
                    c.treatment_notes,
                    c.follow_up_notes,
                    c.created_at,
                    c.updated_at
                FROM consultations c
                JOIN vet_profiles vp
                    ON vp.id = c.vet_profile_id
                WHERE vp.user_id = ?
                ORDER BY c.created_at DESC
                """.trimIndent()
            ).use { statement ->

                statement.setLong(1, vetUserId)

                statement.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(rs.toConsultationResponse())
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns one consultation only when it belongs to this veterinarian.
     */
    suspend fun getVetConsultation(
        vetUserId: Long,
        consultationId: Long
    ): ConsultationResponse? = withContext(Dispatchers.IO) {

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT
                    c.id,
                    c.appointment_id,
                    c.pet_id,
                    c.vet_profile_id,
                    c.chief_complaint,
                    c.clinical_notes,
                    c.diagnosis,
                    c.treatment_notes,
                    c.follow_up_notes,
                    c.created_at,
                    c.updated_at
                FROM consultations c
                JOIN vet_profiles vp
                    ON vp.id = c.vet_profile_id
                WHERE vp.user_id = ?
                  AND c.id = ?
                """.trimIndent()
            ).use { statement ->

                statement.setLong(1, vetUserId)
                statement.setLong(2, consultationId)

                statement.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.toConsultationResponse()
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun ResultSet.toConsultationResponse(): ConsultationResponse {
        return ConsultationResponse(
            id = getLong("id"),
            appointmentId = getLong("appointment_id"),
            petId = getLong("pet_id"),
            vetProfileId = getLong("vet_profile_id"),
            chiefComplaint = getString("chief_complaint"),
            clinicalNotes = getString("clinical_notes"),
            diagnosis = getString("diagnosis"),
            treatmentNotes = getString("treatment_notes"),
            followUpNotes = getString("follow_up_notes"),
            createdAt = getObject(
                "created_at",
                OffsetDateTime::class.java
            ).toString(),
            updatedAt = getObject(
                "updated_at",
                OffsetDateTime::class.java
            ).toString()
        )
    }
}
