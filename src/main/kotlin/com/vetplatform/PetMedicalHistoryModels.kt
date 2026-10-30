package com.vetplatform

import kotlinx.serialization.Serializable

@Serializable
data class PetMedicalHistoryResponse(
    val pet: PetResponse,
    val consultations: List<ConsultationHistoryEntry>,
    val prescriptions: List<PrescriptionHistoryEntry>,
    val observations: List<ObservationHistoryEntry>,
    val allergies: List<PetAllergyEntry>,
    val conditions: List<PetConditionEntry>,
    val vaccinations: List<PetVaccinationEntry>,
    val documents: List<MedicalDocumentResponse>
)

@Serializable
data class ConsultationHistoryEntry(
    val id: Long, val appointmentId: Long, val vetName: String,
    val consultationDate: String, val chiefComplaint: String?,
    val diagnosis: String?, val clinicalNotes: String?,
    val treatmentNotes: String?, val followUpNotes: String?,
    val amendments: List<ConsultationAmendmentEntry> = emptyList()
)

@Serializable
data class ConsultationAmendmentEntry(val id: Long, val authorName: String, val amendment: String, val reason: String, val createdAt: String)

@Serializable
data class PrescriptionHistoryEntry(
    val id: Long, val consultationId: Long, val prescriptionNumber: String,
    val diagnosis: String?, val instructions: String?, val status: String,
    val issuedAt: String, val vetName: String, val items: List<PrescriptionItemEntry>
)

@Serializable
data class PrescriptionItemEntry(val id: Long, val medicineName: String, val dosage: String, val frequency: String, val duration: String, val route: String?, val instructions: String?)

@Serializable
data class ObservationHistoryEntry(val id: Long, val testName: String, val testCode: String?, val value: String, val unit: String?, val observedAt: String, val verificationStatus: String)

@Serializable
data class PetAllergyEntry(val id: Long, val allergen: String, val reaction: String?, val severity: String?, val status: String, val identifiedAt: String?, val recordedBy: String, val verified: Boolean)

@Serializable
data class PetConditionEntry(val id: Long, val conditionName: String, val conditionCode: String?, val details: String?, val status: String, val diagnosedAt: String?, val recordedBy: String, val verified: Boolean)

@Serializable
data class PetVaccinationEntry(val id: Long, val vaccineName: String, val administeredAt: String, val dose: String?, val route: String?, val manufacturer: String?, val batchNumber: String?, val nextDueAt: String?, val notes: String?, val veterinarianName: String?, val verified: Boolean, val status: String, val correctionReason: String?)

@Serializable
data class CreatePetHealthFactRequest(val name: String, val details: String? = null, val severity: String? = null, val date: String? = null)

@Serializable
data class CreateVaccinationRequest(val vaccineName: String, val administeredAt: String, val dose: String? = null, val route: String? = null, val manufacturer: String? = null, val batchNumber: String? = null, val nextDueAt: String? = null, val notes: String? = null)

@Serializable
data class CreatePrescriptionRequest(val consultationId: Long, val prescriptionNumber: String, val diagnosis: String? = null, val instructions: String? = null, val followUpAt: String? = null, val items: List<CreatePrescriptionItemRequest>)

@Serializable
data class CreatePrescriptionItemRequest(val medicineName: String, val dosage: String, val frequency: String, val duration: String, val route: String? = null, val instructions: String? = null)

@Serializable
data class CreateConsultationAmendmentRequest(val amendment: String, val reason: String)

@Serializable
data class UpdatePetHealthFactRequest(val status: String, val reason: String? = null)
