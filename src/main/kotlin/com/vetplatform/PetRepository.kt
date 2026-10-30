
package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Date
import java.sql.Types
import java.time.LocalDate
import java.math.BigDecimal

object PetRepository {

    private fun mapPet(result: java.sql.ResultSet): PetResponse {
        return PetResponse(
            id = result.getLong("id"),
            ownerId = result.getLong("owner_id"),
            name = result.getString("name"),
            species = result.getString("species"),
            breed = result.getString("breed"),
            dateOfBirth = result.getDate("date_of_birth")
                ?.toLocalDate()?.toString(),
            weightKg = result.getBigDecimal("weight_kg")?.toDouble(),
            notes = result.getString("notes")
        )
    }

    suspend fun createPet(
        ownerId: Long,
        request: CreatePetRequest
    ): PetResponse = withContext(Dispatchers.IO) {
        val sql = """
            INSERT INTO pets (
                owner_id, name, species, breed,
                date_of_birth, weight_kg, notes
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            RETURNING id, owner_id, name, species, breed,
                      date_of_birth, weight_kg, notes
        """.trimIndent()

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, ownerId)
                statement.setString(2, request.name.trim())
                statement.setString(3, request.species.trim())
                statement.setString(
                    4,
                    request.breed?.trim()?.takeIf { it.isNotEmpty() }
                )

                if (request.dateOfBirth.isNullOrBlank()) {
                    statement.setNull(5, Types.DATE)
                } else {
                    statement.setDate(
                        5, Date.valueOf(LocalDate.parse(request.dateOfBirth))
                    )
                }

                if (request.weightKg == null) {
                    statement.setNull(6, Types.NUMERIC)
                } else {
                    statement.setBigDecimal(
                        6, BigDecimal.valueOf(request.weightKg)
                    )
                }

                statement.setString(7, request.notes?.trim())

                statement.executeQuery().use { result ->
                    check(result.next()) {
                        "Pet was not returned after insertion"
                    }
                    mapPet(result)
                }
            }
        }
    }

    suspend fun findPetsByOwner(
        ownerId: Long
    ): List<PetResponse> = withContext(Dispatchers.IO) {
        val sql = """
            SELECT id, owner_id, name, species, breed,
                   date_of_birth, weight_kg, notes
            FROM pets
            WHERE owner_id = ? AND archived_at IS NULL
            ORDER BY id DESC
        """.trimIndent()

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, ownerId)

                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(mapPet(result))
                        }
                    }
                }
            }
        }
    }

    suspend fun findArchivedPetsByOwner(ownerId: Long): List<PetResponse> = withContext(Dispatchers.IO) {
        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement("""SELECT id, owner_id, name, species, breed, date_of_birth, weight_kg, notes
                FROM pets WHERE owner_id = ? AND archived_at IS NOT NULL ORDER BY archived_at DESC""").use { ps ->
                ps.setLong(1, ownerId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(mapPet(rs)) } }
            }
        }
    }

    suspend fun findPetById(
        ownerId: Long,
        petId: Long
    ): PetResponse? = withContext(Dispatchers.IO) {
        val sql = """
            SELECT id, owner_id, name, species, breed,
                   date_of_birth, weight_kg, notes
            FROM pets
            WHERE id = ? AND owner_id = ? AND archived_at IS NULL
        """.trimIndent()

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setLong(1, petId)
                statement.setLong(2, ownerId)

                statement.executeQuery().use { result ->
                    if (result.next()) mapPet(result) else null
                }
            }
        }
    }

    suspend fun updatePet(
        ownerId: Long,
        petId: Long,
        request: CreatePetRequest
    ): PetResponse? = withContext(Dispatchers.IO) {
        val sql = """
            UPDATE pets
            SET name = ?,
                species = ?,
                breed = ?,
                date_of_birth = ?,
                weight_kg = ?,
                notes = ?
            WHERE id = ? AND owner_id = ? AND archived_at IS NULL
            RETURNING id, owner_id, name, species, breed,
                      date_of_birth, weight_kg, notes
        """.trimIndent()

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, request.name.trim())
                statement.setString(2, request.species.trim())
                statement.setString(
                    3,
                    request.breed?.trim()?.takeIf { it.isNotEmpty() }
                )

                if (request.dateOfBirth.isNullOrBlank()) {
                    statement.setNull(4, Types.DATE)
                } else {
                    statement.setDate(
                        4, Date.valueOf(LocalDate.parse(request.dateOfBirth))
                    )
                }

                if (request.weightKg == null) {
                    statement.setNull(5, Types.NUMERIC)
                } else {
                    statement.setBigDecimal(
                        5, BigDecimal.valueOf(request.weightKg)
                    )
                }

                statement.setString(6, request.notes?.trim())
                statement.setLong(7, petId)
                statement.setLong(8, ownerId)

                statement.executeQuery().use { result ->
                    if (result.next()) mapPet(result) else null
                }
            }
        }
    }

    suspend fun archivePet(
        ownerId: Long,
        petId: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val sql = """
            UPDATE pets SET archived_at = CURRENT_TIMESTAMP
            WHERE id = ? AND owner_id = ? AND archived_at IS NULL
        """.trimIndent()

        DatabaseFactory.withConnection { connection ->
            val old = connection.autoCommit
            try {
                connection.autoCommit = false
                val archived = connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, petId); statement.setLong(2, ownerId); statement.executeUpdate() > 0
                }
                if (!archived) { connection.rollback(); return@withConnection false }
                connection.prepareStatement("INSERT INTO audit_logs(actor_user_id, action, entity_type, entity_id) VALUES (?, 'CUSTOMER_ARCHIVE_PET', 'PET', ?)").use { audit ->
                    audit.setLong(1, ownerId); audit.setLong(2, petId); audit.executeUpdate()
                }
                connection.commit(); true
            } catch (e: Exception) {
                try { connection.rollback() } catch (rollback: Exception) { e.addSuppressed(rollback) }
                throw e
            } finally {
                connection.autoCommit = old
            }
        }
    }
}
