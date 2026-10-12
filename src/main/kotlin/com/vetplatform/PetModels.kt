
package com.vetplatform

import kotlinx.serialization.Serializable

@Serializable
data class CreatePetRequest(
    val name: String,
    val species: String,
    val breed: String? = null,
    val dateOfBirth: String? = null,
    val weightKg: Double? = null,
    val notes: String? = null
)

@Serializable
data class PetResponse(
    val id: Long,
    val ownerId: Long,
    val name: String,
    val species: String,
    val breed: String? = null,
    val dateOfBirth: String? = null,
    val weightKg: Double? = null,
    val notes: String? = null
)

@Serializable
data class PetListResponse(
    val pets: List<PetResponse>
)
