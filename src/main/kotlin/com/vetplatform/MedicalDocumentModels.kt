
package com.vetplatform

import kotlinx.serialization.Serializable

@Serializable
data class MedicalDocumentResponse(
    val id: Long,
    val petId: Long,
    val consultationId: Long?,
    val uploadedBy: Long,
    val documentType: String,
    val originalFilename: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val processingStatus: String,
    val uploadedAt: String
)

@Serializable
data class MedicalDocumentListResponse(
    val documents: List<MedicalDocumentResponse>
)
