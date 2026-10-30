package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Types
import java.time.LocalDate
import java.time.OffsetDateTime

object PetHealthWriteRepository {
    suspend fun updateHealthFact(vetId: Long, appointmentId: Long, type: String, recordId: Long, status: String, reason: String? = null): Boolean = withContext(Dispatchers.IO) {
        val (table, allowed) = when (type) {
            "allergies" -> "pet_allergies" to setOf("ACTIVE", "RESOLVED")
            "conditions" -> "pet_conditions" to setOf("ACTIVE", "CHRONIC", "RESOLVED")
            "vaccinations" -> "pet_vaccinations" to setOf("VOIDED")
            else -> throw IllegalArgumentException("Unsupported health record type")
        }
        require(recordId > 0 && status in allowed) { "Invalid health record status" }
        if (type == "vaccinations") require(!reason.isNullOrBlank() && reason.length <= 1000) { "A correction reason is required" }
        withCompletedAppointment(vetId, appointmentId) { c, petId, _ ->
            val sql = if (type == "vaccinations") "UPDATE $table SET status = ?, correction_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND pet_id = ? AND status = 'RECORDED'" else "UPDATE $table SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND pet_id = ?"
            val changed = c.prepareStatement(sql).use { ps ->
                ps.setString(1, status)
                if (type == "vaccinations") { ps.setString(2, reason!!.trim()); ps.setLong(3, recordId); ps.setLong(4, petId) }
                else { ps.setLong(2, recordId); ps.setLong(3, petId) }
                ps.executeUpdate() == 1
            }
            if (changed) audit(c, vetId, "VET_UPDATE_PET_${type.uppercase()}", type.uppercase(), recordId)
            changed
        }
    }

    suspend fun addAllergy(vetId: Long, appointmentId: Long, request: CreatePetHealthFactRequest): Long = withContext(Dispatchers.IO) {
        require(request.name.isNotBlank() && request.name.length <= 200)
        require(request.severity == null || request.severity in setOf("MILD", "MODERATE", "SEVERE", "UNKNOWN"))
        require((request.details?.length ?: 0) <= 10000)
        withCompletedAppointment(vetId, appointmentId) { c, petId, profileId ->
            c.prepareStatement("""INSERT INTO pet_allergies(pet_id, allergen, reaction, severity, identified_at, recorded_by, recorded_by_vet_profile_id, verified_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id""").use { ps ->
                ps.setLong(1, petId); ps.setString(2, request.name.trim()); ps.setString(3, request.details)
                ps.setString(4, request.severity)
                if (request.date == null) ps.setNull(5, Types.DATE) else ps.setDate(5, java.sql.Date.valueOf(LocalDate.parse(request.date)))
                ps.setLong(6, vetId); ps.setLong(7, profileId)
                ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
            }.also { audit(c, vetId, "VET_ADD_PET_ALLERGY", "PET_ALLERGY", it) }
        }
    }

    suspend fun addCondition(vetId: Long, appointmentId: Long, request: CreatePetHealthFactRequest): Long = withContext(Dispatchers.IO) {
        require(request.name.isNotBlank() && request.name.length <= 200)
        require((request.details?.length ?: 0) <= 10000)
        withCompletedAppointment(vetId, appointmentId) { c, petId, profileId ->
            c.prepareStatement("""INSERT INTO pet_conditions(pet_id, condition_name, details, diagnosed_at, recorded_by, recorded_by_vet_profile_id, verified_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id""").use { ps ->
                ps.setLong(1, petId); ps.setString(2, request.name.trim()); ps.setString(3, request.details)
                if (request.date == null) ps.setNull(4, Types.DATE) else ps.setDate(4, java.sql.Date.valueOf(LocalDate.parse(request.date)))
                ps.setLong(5, vetId); ps.setLong(6, profileId)
                ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
            }.also { audit(c, vetId, "VET_ADD_PET_CONDITION", "PET_CONDITION", it) }
        }
    }

    suspend fun addVaccination(vetId: Long, appointmentId: Long, request: CreateVaccinationRequest): Long = withContext(Dispatchers.IO) {
        require(request.vaccineName.isNotBlank() && request.vaccineName.length <= 200)
        require(listOf(request.dose, request.route, request.manufacturer, request.batchNumber).all { (it?.length ?: 0) <= 200 } && (request.notes?.length ?: 0) <= 10000)
        val administered = OffsetDateTime.parse(request.administeredAt)
        withCompletedAppointment(vetId, appointmentId) { c, petId, profileId ->
            c.prepareStatement("""INSERT INTO pet_vaccinations(pet_id, vaccine_name, administered_at, dose, route, manufacturer, batch_number, next_due_at, notes, recorded_by, administered_by_vet_profile_id, verified_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id""").use { ps ->
                ps.setLong(1, petId); ps.setString(2, request.vaccineName.trim()); ps.setObject(3, administered)
                ps.setString(4, request.dose); ps.setString(5, request.route); ps.setString(6, request.manufacturer); ps.setString(7, request.batchNumber)
                if (request.nextDueAt == null) ps.setNull(8, Types.DATE) else ps.setDate(8, java.sql.Date.valueOf(LocalDate.parse(request.nextDueAt)))
                ps.setString(9, request.notes); ps.setLong(10, vetId); ps.setLong(11, profileId)
                ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
            }.also { audit(c, vetId, "VET_ADD_PET_VACCINATION", "PET_VACCINATION", it) }
        }
    }

    suspend fun createPrescription(vetId: Long, request: CreatePrescriptionRequest): Long = withContext(Dispatchers.IO) {
        require(request.consultationId > 0 && request.prescriptionNumber.isNotBlank() && request.prescriptionNumber.length <= 100)
        require(request.items.isNotEmpty() && request.items.size <= 50)
        require((request.instructions?.length ?: 0) <= 10000 && (request.diagnosis?.length ?: 0) <= 10000)
        request.items.forEach { require(it.medicineName.isNotBlank() && it.medicineName.length <= 255 && it.dosage.isNotBlank() && it.dosage.length <= 255 && it.frequency.isNotBlank() && it.frequency.length <= 255 && it.duration.isNotBlank() && it.duration.length <= 100 && (it.route?.length ?: 0) <= 100 && (it.instructions?.length ?: 0) <= 10000) }
        DatabaseFactory.withConnection { c ->
            val old = c.autoCommit
            try {
                c.autoCommit = false
                val identity = c.prepareStatement("""SELECT c.pet_id, c.vet_profile_id FROM consultations c
                    JOIN vet_profiles vp ON vp.id = c.vet_profile_id JOIN users u ON u.id = vp.user_id
                    JOIN appointments a ON a.id = c.appointment_id
                    WHERE c.id = ? AND vp.user_id = ? AND a.status = 'COMPLETED' AND vp.verification_status = 'APPROVED' AND u.account_status = 'ACTIVE'
                    FOR SHARE OF c, a, vp, u""").use { ps ->
                    ps.setLong(1, request.consultationId); ps.setLong(2, vetId)
                    ps.executeQuery().use { rs -> if (!rs.next()) throw SecurityException("Completed consultation assigned to you is required") else rs.getLong("pet_id") to rs.getLong("vet_profile_id") }
                }
                val prescriptionId = c.prepareStatement("""INSERT INTO prescriptions(consultation_id, pet_id, vet_profile_id, prescription_number, diagnosis, instructions, follow_up_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""").use { ps ->
                    ps.setLong(1, request.consultationId); ps.setLong(2, identity.first); ps.setLong(3, identity.second)
                    ps.setString(4, request.prescriptionNumber.trim()); ps.setString(5, request.diagnosis); ps.setString(6, request.instructions)
                    if (request.followUpAt == null) ps.setNull(7, Types.TIMESTAMP_WITH_TIMEZONE) else ps.setObject(7, OffsetDateTime.parse(request.followUpAt))
                    ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
                }
                c.prepareStatement("INSERT INTO prescription_items(prescription_id, medicine_name, dosage, frequency, duration, route, instructions) VALUES (?, ?, ?, ?, ?, ?, ?)").use { ps ->
                    request.items.forEach { item ->
                        ps.setLong(1, prescriptionId); ps.setString(2, item.medicineName.trim()); ps.setString(3, item.dosage.trim())
                        ps.setString(4, item.frequency.trim()); ps.setString(5, item.duration.trim()); ps.setString(6, item.route); ps.setString(7, item.instructions); ps.addBatch()
                    }
                    ps.executeBatch()
                }
                audit(c, vetId, "VET_ISSUE_PRESCRIPTION", "PRESCRIPTION", prescriptionId)
                c.commit(); prescriptionId
            } catch (e: Exception) { try { c.rollback() } catch (r: Exception) { e.addSuppressed(r) }; throw e }
            finally { c.autoCommit = old }
        }
    }

    suspend fun addConsultationAmendment(vetId: Long, consultationId: Long, request: CreateConsultationAmendmentRequest): Long = withContext(Dispatchers.IO) {
        require(request.amendment.isNotBlank() && request.reason.isNotBlank() && request.amendment.length <= 10000 && request.reason.length <= 1000)
        DatabaseFactory.withConnection { c ->
            val old = c.autoCommit
            try {
                c.autoCommit = false
                c.prepareStatement("""SELECT 1 FROM consultations c JOIN vet_profiles vp ON vp.id=c.vet_profile_id
                    JOIN users u ON u.id=vp.user_id WHERE c.id=? AND vp.user_id=? AND vp.verification_status='APPROVED' AND u.account_status='ACTIVE' FOR SHARE OF c, vp, u""").use { ps ->
                    ps.setLong(1, consultationId); ps.setLong(2, vetId); ps.executeQuery().use { if (!it.next()) throw SecurityException("Consultation assigned to you is required") }
                }
                val id = c.prepareStatement("INSERT INTO consultation_amendments(consultation_id, author_user_id, amendment, reason) VALUES (?, ?, ?, ?) RETURNING id").use { ps ->
                    ps.setLong(1, consultationId); ps.setLong(2, vetId); ps.setString(3, request.amendment.trim()); ps.setString(4, request.reason.trim())
                    ps.executeQuery().use { rs -> check(rs.next()); rs.getLong(1) }
                }
                audit(c, vetId, "VET_AMEND_CONSULTATION", "CONSULTATION", consultationId)
                c.commit(); id
            } catch (e: Exception) { try { c.rollback() } catch (r: Exception) { e.addSuppressed(r) }; throw e }
            finally { c.autoCommit = old }
        }
    }

    private suspend fun <T> withCompletedAppointment(vetId: Long, appointmentId: Long, block: (java.sql.Connection, Long, Long) -> T): T =
        DatabaseFactory.withConnection { c ->
            val old = c.autoCommit
            try {
                c.autoCommit = false
                val ids = c.prepareStatement("""SELECT a.pet_id, vp.id AS vet_profile_id FROM appointments a
                    JOIN vet_profiles vp ON vp.id=a.vet_profile_id JOIN users u ON u.id=vp.user_id
                    WHERE a.id=? AND vp.user_id=? AND a.status='COMPLETED' AND vp.verification_status='APPROVED' AND u.account_status='ACTIVE'
                    FOR SHARE OF a, vp, u""").use { ps ->
                    ps.setLong(1, appointmentId); ps.setLong(2, vetId)
                    ps.executeQuery().use { rs -> if (!rs.next()) throw SecurityException("Completed appointment assigned to you is required") else rs.getLong("pet_id") to rs.getLong("vet_profile_id") }
                }
                val result = block(c, ids.first, ids.second)
                c.commit(); result
            } catch (e: Exception) { try { c.rollback() } catch (r: Exception) { e.addSuppressed(r) }; throw e }
            finally { c.autoCommit = old }
        }

    private fun audit(c: java.sql.Connection, actor: Long, action: String, entity: String, id: Long) {
        c.prepareStatement("INSERT INTO audit_logs(actor_user_id, action, entity_type, entity_id) VALUES (?, ?, ?, ?)").use { ps ->
            ps.setLong(1, actor); ps.setString(2, action); ps.setString(3, entity); ps.setLong(4, id); ps.executeUpdate()
        }
    }
}
