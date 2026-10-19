
package com.vetplatform

class MedicalDocumentService(
    private val storage: MedicalDocumentStorage = MedicalDocumentStorage(),
    private val repository: MedicalDocumentRepository = MedicalDocumentRepository()
) {

    companion object {
        private const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024

        private val ALLOWED_CONTENT_TYPES = setOf(
            "application/pdf",
            "image/jpeg",
            "image/png"
        )
    }

    suspend fun uploadDocument(
        uploaderUserId: Long,
        petId: Long,
        consultationId: Long?,
        documentType: String,
        originalFilename: String,
        contentType: String,
        bytes: ByteArray
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

        require(documentType.isNotBlank() && documentType.length <= 50) {
            "Document type must contain between 1 and 50 characters"
        }

        require(originalFilename.isNotBlank()) {
            "Filename must not be empty"
        }

        require(bytes.isNotEmpty()) {
            "The uploaded file is empty"
        }

        require(bytes.size.toLong() <= MAX_FILE_SIZE_BYTES) {
            "File size must not exceed 10 MB"
        }

        val normalizedContentType = contentType
            .substringBefore(';')
            .trim()
            .lowercase()

        require(normalizedContentType in ALLOWED_CONTENT_TYPES) {
            "Only PDF, JPG, JPEG, and PNG files are allowed"
        }

        validateFileSignature(normalizedContentType, bytes)

        // Store the actual file first.
        val storedDocument = storage.store(
            originalFilename = originalFilename,
            contentType = normalizedContentType,
            bytes = bytes
        )

        try {
            // Save metadata only after local storage succeeds.
            return repository.createDocument(
                uploaderUserId = uploaderUserId,
                petId = petId,
                consultationId = consultationId,
                documentType = documentType,
                storedDocument = storedDocument
            )
        } catch (exception: Exception) {
            // Avoid leaving a local file when metadata saving fails.
            try {
                storage.delete(storedDocument.storageKey)
            } catch (cleanupException: Exception) {
                exception.addSuppressed(cleanupException)
            }

            throw exception
        }
    }

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

        return repository.getDocumentsForPetOwner(
            ownerUserId = ownerUserId,
            petId = petId
        )
    }


    suspend fun downloadDocumentForPetOwner(
        ownerUserId: Long,
        documentId: Long
    ): Pair<MedicalDocumentRecord, ByteArray>? {

        val document = repository.getDocumentForPetOwner(
            ownerUserId = ownerUserId,
            documentId = documentId
        ) ?: return null

        val bytes = storage.read(document.storageKey)

        return document to bytes
    }


    private fun validateFileSignature(
        contentType: String,
        bytes: ByteArray
    ) {
        val validSignature = when (contentType) {
            "application/pdf" -> {
                bytes.size >= 5 &&
                        bytes[0] == '%'.code.toByte() &&
                        bytes[1] == 'P'.code.toByte() &&
                        bytes[2] == 'D'.code.toByte() &&
                        bytes[3] == 'F'.code.toByte() &&
                        bytes[4] == '-'.code.toByte()
            }

            "image/jpeg" -> {
                bytes.size >= 3 &&
                        bytes[0] == 0xFF.toByte() &&
                        bytes[1] == 0xD8.toByte() &&
                        bytes[2] == 0xFF.toByte()
            }

            "image/png" -> {
                val signature = byteArrayOf(
                    0x89.toByte(),
                    0x50.toByte(),
                    0x4E.toByte(),
                    0x47.toByte(),
                    0x0D.toByte(),
                    0x0A.toByte(),
                    0x1A.toByte(),
                    0x0A.toByte()
                )

                bytes.size >= signature.size &&
                        signature.indices.all { index ->
                            bytes[index] == signature[index]
                        }
            }

            else -> false
        }

        require(validSignature) {
            "The file contents do not match the declared file type"
        }
    }
}
