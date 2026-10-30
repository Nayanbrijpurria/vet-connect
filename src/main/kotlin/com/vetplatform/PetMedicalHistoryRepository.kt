package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.time.OffsetDateTime

object PetMedicalHistoryRepository {
    suspend fun getForOwner(ownerId: Long, petId: Long): PetMedicalHistoryResponse =
        withContext(Dispatchers.IO) {
            require(ownerId > 0 && petId > 0)
            loadHistory(ownerId, petId, null)
        }

    suspend fun getForVetAppointment(vetUserId: Long, appointmentId: Long): PetMedicalHistoryResponse =
        withContext(Dispatchers.IO) {
            require(vetUserId > 0 && appointmentId > 0)
            loadHistory(null, null, vetUserId to appointmentId)
        }

    private suspend fun loadHistory(ownerId: Long?, petId: Long?, vetAppointment: Pair<Long, Long>?): PetMedicalHistoryResponse =
        DatabaseFactory.withConnection { connection ->
            val oldAutoCommit = connection.autoCommit
            try {
                connection.autoCommit = false
                val pet = if (vetAppointment == null) {
                    connection.prepareStatement("SELECT id, owner_id, name, species, breed, date_of_birth, weight_kg, notes FROM pets WHERE id = ? AND owner_id = ? FOR SHARE").use { ps ->
                        ps.setLong(1, petId!!); ps.setLong(2, ownerId!!)
                        ps.executeQuery().use { rs -> if (!rs.next()) throw SecurityException("Pet was not found") else rs.toPetResponse() }
                    }
                } else {
                    connection.prepareStatement("""SELECT p.id, p.owner_id, p.name, p.species, p.breed, p.date_of_birth, p.weight_kg, p.notes
                        FROM appointments a JOIN pets p ON p.id = a.pet_id
                        JOIN vet_profiles vp ON vp.id = a.vet_profile_id JOIN users u ON u.id = vp.user_id
                        WHERE a.id = ? AND vp.user_id = ? AND a.status IN ('CONFIRMED', 'COMPLETED')
                          AND vp.verification_status = 'APPROVED' AND u.account_status = 'ACTIVE'
                        FOR SHARE OF a, p, vp, u""".trimIndent()).use { ps ->
                        ps.setLong(1, vetAppointment.second); ps.setLong(2, vetAppointment.first)
                        ps.executeQuery().use { rs -> if (!rs.next()) throw SecurityException("An upcoming or completed appointment assigned to you is required") else rs.toPetResponse() }
                    }
                }
                val id = pet.id
                val amendmentsByConsultation = connection.prepareStatement("""SELECT ca.consultation_id, ca.id, u.full_name, ca.amendment, ca.reason, ca.created_at
                    FROM consultation_amendments ca JOIN consultations c ON c.id = ca.consultation_id
                    JOIN users u ON u.id = ca.author_user_id WHERE c.pet_id = ? ORDER BY ca.created_at""").use { ps ->
                    ps.setLong(1, id); ps.executeQuery().use { rs ->
                        val grouped = mutableMapOf<Long, MutableList<ConsultationAmendmentEntry>>()
                        while (rs.next()) grouped.getOrPut(rs.getLong("consultation_id")) { mutableListOf() }.add(
                            ConsultationAmendmentEntry(rs.getLong("id"), rs.getString("full_name"), rs.getString("amendment"), rs.getString("reason"), rs.getObject("created_at", OffsetDateTime::class.java).toString())
                        )
                        grouped
                    }
                }
                val consultations = connection.prepareStatement("""SELECT c.id, c.appointment_id, v.full_name AS vet_name,
                    a.scheduled_start, c.chief_complaint, c.diagnosis, c.clinical_notes, c.treatment_notes, c.follow_up_notes
                    FROM consultations c JOIN appointments a ON a.id = c.appointment_id
                    JOIN vet_profiles vp ON vp.id = c.vet_profile_id JOIN users v ON v.id = vp.user_id
                    WHERE c.pet_id = ? ORDER BY a.scheduled_start DESC, c.id DESC""").use { ps ->
                    ps.setLong(1, id); ps.executeQuery().use { rs -> buildList {
                        while (rs.next()) {
                            val consultationId = rs.getLong("id")
                            add(ConsultationHistoryEntry(consultationId, rs.getLong("appointment_id"), rs.getString("vet_name"), rs.getObject("scheduled_start", OffsetDateTime::class.java).toString(), rs.getString("chief_complaint"), rs.getString("diagnosis"), rs.getString("clinical_notes"), rs.getString("treatment_notes"), rs.getString("follow_up_notes"), amendmentsByConsultation[consultationId].orEmpty()))
                        }
                    } }
                }
                val itemsByPrescription = connection.prepareStatement("""SELECT pi.prescription_id, pi.id, pi.medicine_name, pi.dosage, pi.frequency, pi.duration, pi.route, pi.instructions
                    FROM prescription_items pi JOIN prescriptions pr ON pr.id = pi.prescription_id
                    WHERE pr.pet_id = ? ORDER BY pi.id""").use { ps ->
                    ps.setLong(1, id); ps.executeQuery().use { rs ->
                        val grouped = mutableMapOf<Long, MutableList<PrescriptionItemEntry>>()
                        while (rs.next()) grouped.getOrPut(rs.getLong("prescription_id")) { mutableListOf() }.add(
                            PrescriptionItemEntry(rs.getLong("id"), rs.getString("medicine_name"), rs.getString("dosage"), rs.getString("frequency"), rs.getString("duration"), rs.getString("route"), rs.getString("instructions"))
                        )
                        grouped
                    }
                }
                val prescriptions = connection.prepareStatement("""SELECT pr.id, pr.consultation_id, pr.prescription_number, pr.diagnosis,
                    pr.instructions, pr.status, pr.issued_at, u.full_name AS vet_name
                    FROM prescriptions pr JOIN vet_profiles vp ON vp.id = pr.vet_profile_id JOIN users u ON u.id = vp.user_id
                    WHERE pr.pet_id = ? ORDER BY pr.issued_at DESC""").use { ps ->
                    ps.setLong(1, id); ps.executeQuery().use { rs -> buildList {
                        while (rs.next()) {
                            val prescriptionId = rs.getLong("id")
                            add(PrescriptionHistoryEntry(prescriptionId, rs.getLong("consultation_id"), rs.getString("prescription_number"), rs.getString("diagnosis"), rs.getString("instructions"), rs.getString("status"), rs.getObject("issued_at", OffsetDateTime::class.java).toString(), rs.getString("vet_name"), itemsByPrescription[prescriptionId].orEmpty()))
                        }
                    } }
                }
                val observations = connection.prepareStatement("""SELECT id, test_name, test_code, numeric_value, text_value, unit, observed_at, verification_status
                    FROM clinical_observations WHERE pet_id = ? ORDER BY observed_at DESC""").use { ps ->
                    ps.setLong(1, id); ps.executeQuery().use { rs -> buildList {
                        while (rs.next()) add(ObservationHistoryEntry(rs.getLong("id"), rs.getString("test_name"), rs.getString("test_code"), listOfNotNull(rs.getBigDecimal("numeric_value")?.toPlainString(), rs.getString("text_value")).joinToString("; "), rs.getString("unit"), rs.getObject("observed_at", OffsetDateTime::class.java).toString(), rs.getString("verification_status")))
                    } }
                }
                val allergies = connection.prepareStatement("""SELECT x.id, x.allergen, x.reaction, x.severity, x.status, x.identified_at, u.full_name, x.verified_at
                    FROM pet_allergies x JOIN users u ON u.id = x.recorded_by WHERE x.pet_id = ? ORDER BY x.created_at DESC""").use { ps ->
                    ps.setLong(1, id); ps.executeQuery().use { rs -> buildList {
                        while (rs.next()) add(PetAllergyEntry(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getDate(6)?.toLocalDate()?.toString(), rs.getString(7), rs.getTimestamp(8) != null))
                    } }
                }
                val conditions = connection.prepareStatement("""SELECT x.id, x.condition_name, x.condition_code, x.details, x.status, x.diagnosed_at, u.full_name, x.verified_at
                    FROM pet_conditions x JOIN users u ON u.id = x.recorded_by WHERE x.pet_id = ? ORDER BY x.created_at DESC""").use { ps ->
                    ps.setLong(1, id); ps.executeQuery().use { rs -> buildList {
                        while (rs.next()) add(PetConditionEntry(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getDate(6)?.toLocalDate()?.toString(), rs.getString(7), rs.getTimestamp(8) != null))
                    } }
                }
                val vaccinations = connection.prepareStatement("""SELECT x.id, x.vaccine_name, x.administered_at, x.dose, x.route, x.manufacturer,
                    x.batch_number, x.next_due_at, x.notes, v.full_name, x.verified_at, x.status, x.correction_reason
                    FROM pet_vaccinations x LEFT JOIN vet_profiles vp ON vp.id = x.administered_by_vet_profile_id
                    LEFT JOIN users v ON v.id = vp.user_id WHERE x.pet_id = ? ORDER BY x.administered_at DESC""").use { ps ->
                    ps.setLong(1, id); ps.executeQuery().use { rs -> buildList {
                        while (rs.next()) add(PetVaccinationEntry(rs.getLong(1), rs.getString(2), rs.getObject(3, OffsetDateTime::class.java).toString(), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getDate(8)?.toLocalDate()?.toString(), rs.getString(9), rs.getString(10), rs.getTimestamp(11) != null, rs.getString(12), rs.getString(13)))
                    } }
                }
                val documents = connection.prepareStatement("""SELECT id, pet_id, consultation_id, uploaded_by, document_type,
                    original_filename, mime_type, file_size_bytes, processing_status, uploaded_at
                    FROM medical_documents WHERE pet_id = ? ORDER BY uploaded_at DESC""").use { ps ->
                    ps.setLong(1, id); ps.executeQuery().use { rs -> buildList {
                        while (rs.next()) add(MedicalDocumentResponse(rs.getLong("id"), rs.getLong("pet_id"), rs.getLong("consultation_id").let { if (rs.wasNull()) null else it }, rs.getLong("uploaded_by"), rs.getString("document_type"), rs.getString("original_filename"), rs.getString("mime_type"), rs.getLong("file_size_bytes"), rs.getString("processing_status"), rs.getObject("uploaded_at", OffsetDateTime::class.java).toString()))
                    } }
                }
                val actorId = vetAppointment?.first ?: ownerId!!
                val action = if (vetAppointment == null) "CUSTOMER_VIEW_PET_MEDICAL_HISTORY" else "VET_VIEW_PET_MEDICAL_HISTORY"
                connection.prepareStatement("INSERT INTO audit_logs(actor_user_id, action, entity_type, entity_id, details) VALUES (?, ?, 'PET', ?, jsonb_build_object('petId', ?))").use { ps ->
                    ps.setLong(1, actorId); ps.setString(2, action); ps.setLong(3, id); ps.setLong(4, id); ps.executeUpdate()
                }
                connection.commit()
                PetMedicalHistoryResponse(pet, consultations, prescriptions, observations, allergies, conditions, vaccinations, documents)
            } catch (e: Exception) {
                try { connection.rollback() } catch (rollback: Exception) { e.addSuppressed(rollback) }
                throw e
            } finally { connection.autoCommit = oldAutoCommit }
        }

    private fun java.sql.ResultSet.toPetResponse() = PetResponse(
        id = getLong("id"), ownerId = getLong("owner_id"), name = getString("name"),
        species = getString("species"), breed = getString("breed"),
        dateOfBirth = getDate("date_of_birth")?.toLocalDate()?.toString(),
        weightKg = getBigDecimal("weight_kg")?.toDouble(), notes = getString("notes")
    )
}
