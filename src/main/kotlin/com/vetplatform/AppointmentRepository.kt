
package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.sql.Time
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.LocalTime

object AppointmentRepository {

    suspend fun createAppointment(
        customerId: Long,
        request: CreateAppointmentRequest
    ): AppointmentResponse = withContext(Dispatchers.IO) {

        val start = try {
            OffsetDateTime.parse(request.scheduledStart)
        } catch (exception: Exception) {
            throw IllegalArgumentException(
                "scheduledStart must be an ISO-8601 timestamp with timezone"
            )
        }

        val end = try {
            OffsetDateTime.parse(request.scheduledEnd)
        } catch (exception: Exception) {
            throw IllegalArgumentException(
                "scheduledEnd must be an ISO-8601 timestamp with timezone"
            )
        }

        require(end.isAfter(start)) {
            "Appointment end must be after its start"
        }

        require(start.isAfter(OffsetDateTime.now())) {
            "Appointment must be scheduled in the future"
        }

        DatabaseFactory.withConnection { connection ->
            val originalAutoCommit = connection.autoCommit

            try {
                connection.autoCommit = false

                // 1. Verify that this pet belongs to the customer.
                val ownsPet = connection.prepareStatement(
                    """
                    SELECT id
                    FROM pets
                    WHERE id = ? AND owner_id = ?
                    FOR UPDATE
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, request.petId)
                    statement.setLong(2, customerId)

                    statement.executeQuery().use { rs ->
                        rs.next()
                    }
                }

                require(ownsPet) {
                    "Pet not found or does not belong to this customer"
                }

                // 2. Lock the veterinarian profile.
                // Concurrent bookings for this vet must acquire this same lock.
                val vet = connection.prepareStatement(
                    """
                    

SELECT
    vp.id AS vet_profile_id,
    vp.user_id AS vet_user_id,
    vp.consultation_fee,
    vp.timezone
FROM vet_profiles vp
JOIN users u ON u.id = vp.user_id
WHERE vp.user_id = ?
  AND u.role = 'VET'
  AND vp.verification_status = 'APPROVED'
FOR UPDATE OF vp


                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, request.vetUserId)

                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            VetBookingInfo(
                                profileId = rs.getLong("vet_profile_id"),
                                userId = rs.getLong("vet_user_id"),
                                consultationFee = rs.getBigDecimal("consultation_fee"),
                                timezone = rs.getString("timezone")
                            )
                        } else {
                            null
                        }
                    }
                } ?: throw IllegalArgumentException(
                    "Veterinarian not found or not approved"
                )

                // 3. Convert the requested instant to the veterinarian's timezone.
                val zone = try {
                    ZoneId.of(vet.timezone)
                } catch (exception: Exception) {
                    throw IllegalStateException(
                        "Veterinarian has an invalid timezone configured"
                    )
                }

                val localStart = start.toInstant().atZone(zone)
                val localEnd = end.toInstant().atZone(zone)

                val elapsed = Duration.between(start.toInstant(), end.toInstant())

                require(elapsed.seconds > 0 && elapsed.nano == 0) {
                    "Appointment duration must be a whole number of seconds"
                }


                println("DEBUG vet profile ID: ${vet.profileId}")
                println("DEBUG vet timezone: ${vet.timezone}")
                println("DEBUG local start: $localStart")
                println("DEBUG local end: $localEnd")
                println("DEBUG requested elapsed: $elapsed")


                // 4. Find a matching active weekly schedule.
                val matchingSlot = connection.prepareStatement(
                    """
                    SELECT start_time, end_time, slot_duration_minutes
                    FROM vet_availability
                    WHERE vet_profile_id = ?
                      AND day_of_week = ?
                      AND active = TRUE
                      AND start_time <= ?
                      AND end_time >= ?
                    ORDER BY start_time
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, vet.userId)
                    statement.setInt(2, localStart.dayOfWeek.value)
                    statement.setTime(
                        3,
                        Time.valueOf(localStart.toLocalTime())
                    )
                    statement.setTime(
                        4,
                        Time.valueOf(localEnd.toLocalTime())
                    )


                    println("DEBUG requested weekday: ${localStart.dayOfWeek.value}")
                    println("DEBUG SQL start time: ${localStart.toLocalTime()}")
                    println("DEBUG SQL end time: ${localEnd.toLocalTime()}")


                    statement.executeQuery().use { rs ->
                        var matched: AvailabilitySlot? = null

                        while (rs.next()) {
                            val slot = AvailabilitySlot(
                                start = rs.getTime("start_time").toLocalTime(),
                                end = rs.getTime("end_time").toLocalTime(),
                                durationMinutes = rs.getInt("slot_duration_minutes")
                            )

                            val expectedEnd =
                                localStart.toLocalTime()
                                    .plusMinutes(slot.durationMinutes.toLong())

                            val minutesFromOpening = Duration.between(
                                slot.start,
                                localStart.toLocalTime()
                            ).toMinutes()

                            val durationMatches =
                                elapsed == Duration.ofMinutes(
                                    slot.durationMinutes.toLong()
                                )

                            val localTimesMatch =
                                localStart.toLocalDate() == localEnd.toLocalDate() &&
                                        localEnd.toLocalTime() == expectedEnd &&
                                        expectedEnd <= slot.end

                            val startsOnBoundary =
                                minutesFromOpening >= 0 &&
                                        minutesFromOpening % slot.durationMinutes == 0L &&
                                        localStart.toLocalTime().second == 0 &&
                                        localStart.toLocalTime().nano == 0

                            if (
                                durationMatches &&
                                localTimesMatch &&
                                startsOnBoundary
                            ) {
                                matched = slot
                                break
                            }
                        }

                        matched
                    }
                }

                require(matchingSlot != null) {
                    "Requested time does not match an available appointment slot"
                }

                // 5. Reject any time-off interval that overlaps this appointment.
                val hasTimeOff = connection.prepareStatement(
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM vet_time_off
                        WHERE vet_profile_id = ?
                          AND starts_at < ?
                          AND ends_at > ?
                    )
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, vet.userId)
                    statement.setObject(2, end)
                    statement.setObject(3, start)

                    statement.executeQuery().use { rs ->
                        rs.next()
                        rs.getBoolean(1)
                    }
                }

                require(!hasTimeOff) {
                    "The veterinarian is unavailable during this time"
                }

                // 6. Reject overlapping active appointments.
                val hasOverlap = connection.prepareStatement(
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM appointments
                        WHERE vet_profile_id = ?
                          AND status IN ('PENDING', 'CONFIRMED')
                          AND scheduled_start < ?
                          AND scheduled_end > ?
                    )
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, vet.profileId)
                    statement.setObject(2, end)
                    statement.setObject(3, start)

                    statement.executeQuery().use { rs ->
                        rs.next()
                        rs.getBoolean(1)
                    }
                }

                require(!hasOverlap) {
                    "This veterinarian already has an overlapping appointment"
                }

                // 7. Insert as CONFIRMED for instant booking.
                val appointment = connection.prepareStatement(
                    """
                    INSERT INTO appointments (
                        customer_id,
                        pet_id,
                        vet_profile_id,
                        scheduled_start,
                        scheduled_end,
                        status,
                        consultation_fee
                    )
                    VALUES (?, ?, ?, ?, ?, 'CONFIRMED', ?)
                    RETURNING id, status, consultation_fee
                    """.trimIndent()
                ).use { statement ->
                    statement.setLong(1, customerId)
                    statement.setLong(2, request.petId)
                    statement.setLong(3, vet.profileId)
                    statement.setObject(4, start)
                    statement.setObject(5, end)
                    statement.setBigDecimal(6, vet.consultationFee)

                    statement.executeQuery().use { rs ->
                        check(rs.next()) {
                            "Appointment could not be created"
                        }

                        AppointmentResponse(
                            id = rs.getLong("id"),
                            customerId = customerId,
                            petId = request.petId,
                            vetUserId = request.vetUserId,
                            scheduledStart = start.toString(),
                            scheduledEnd = end.toString(),
                            status = rs.getString("status"),
                            consultationFee =
                                rs.getBigDecimal("consultation_fee").toDouble()
                        )
                    }
                }

                connection.commit()
                appointment

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

    // Get all appointments belonging to one customer.
    suspend fun getCustomerAppointments(
        customerId: Long
    ): List<AppointmentResponse> = withContext(Dispatchers.IO) {

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT
                    a.id,
                    a.customer_id,
                    a.pet_id,
                    vp.user_id AS vet_user_id,
                    a.scheduled_start,
                    a.scheduled_end,
                    a.status,
                    a.consultation_fee,
                    a.meeting_url
                FROM appointments a
                JOIN vet_profiles vp ON vp.id = a.vet_profile_id
                WHERE a.customer_id = ?
                ORDER BY a.scheduled_start DESC
                """.trimIndent()
            ).use { statement ->

                statement.setLong(1, customerId)

                statement.executeQuery().use { rs ->
                    val appointments = mutableListOf<AppointmentResponse>()

                    while (rs.next()) {
                        appointments.add(
                            AppointmentResponse(
                                id = rs.getLong("id"),
                                customerId = rs.getLong("customer_id"),
                                petId = rs.getLong("pet_id"),
                                vetUserId = rs.getLong("vet_user_id"),
                                scheduledStart = rs.getObject(
                                    "scheduled_start",
                                    OffsetDateTime::class.java
                                ).toString(),
                                scheduledEnd = rs.getObject(
                                    "scheduled_end",
                                    OffsetDateTime::class.java
                                ).toString(),
                                status = rs.getString("status"),
                                consultationFee = rs.getBigDecimal(
                                    "consultation_fee"
                                ).toDouble(),
                                meetingUrl = rs.getString("meeting_url")
                            )
                        )
                    }

                    appointments
                }
            }
        }
    }

    // Cancel an appointment belonging to the customer.
    suspend fun cancelCustomerAppointment(
        customerId: Long,
        appointmentId: Long
    ): Boolean = withContext(Dispatchers.IO) {

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE appointments
                SET status = 'CANCELLED'
                WHERE id = ?
                  AND customer_id = ?
                  AND status IN ('PENDING', 'CONFIRMED')
                  AND scheduled_start > CURRENT_TIMESTAMP
                """.trimIndent()
            ).use { statement ->

                statement.setLong(1, appointmentId)
                statement.setLong(2, customerId)

                statement.executeUpdate() == 1
            }
        }
    }


    // Get appointments assigned to the logged-in veterinarian.
    suspend fun getVetAppointments(
        vetUserId: Long
    ): List<AppointmentResponse> = withContext(Dispatchers.IO) {

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT
                    a.id,
                    a.customer_id,
                    a.pet_id,
                    vp.user_id AS vet_user_id,
                    a.scheduled_start,
                    a.scheduled_end,
                    a.status,
                    a.consultation_fee,
                    a.meeting_url
                FROM appointments a
                JOIN vet_profiles vp ON vp.id = a.vet_profile_id
                WHERE vp.user_id = ?
                ORDER BY a.scheduled_start DESC
                """.trimIndent()
            ).use { statement ->

                statement.setLong(1, vetUserId)

                statement.executeQuery().use { rs ->
                    val appointments = mutableListOf<AppointmentResponse>()

                    while (rs.next()) {
                        appointments.add(
                            AppointmentResponse(
                                id = rs.getLong("id"),
                                customerId = rs.getLong("customer_id"),
                                petId = rs.getLong("pet_id"),
                                vetUserId = rs.getLong("vet_user_id"),
                                scheduledStart = rs.getObject(
                                    "scheduled_start",
                                    OffsetDateTime::class.java
                                ).toString(),
                                scheduledEnd = rs.getObject(
                                    "scheduled_end",
                                    OffsetDateTime::class.java
                                ).toString(),
                                status = rs.getString("status"),
                                consultationFee = rs.getBigDecimal(
                                    "consultation_fee"
                                ).toDouble(),
                                meetingUrl = rs.getString("meeting_url")
                            )
                        )
                    }

                    appointments
                }
            }
        }
    }

    // Allow a veterinarian to complete a finished appointment
    // or cancel a future confirmed appointment assigned to them.
    suspend fun updateVetAppointmentStatus(
        vetUserId: Long,
        appointmentId: Long,
        newStatus: String
    ): Boolean = withContext(Dispatchers.IO) {

        require(newStatus == "COMPLETED" || newStatus == "CANCELLED") {
            "Status must be COMPLETED or CANCELLED"
        }

        DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                """
                UPDATE appointments
                SET status = ?
                WHERE id = ?
                  AND status = 'CONFIRMED'
                  AND EXISTS (
                      SELECT 1
                      FROM vet_profiles vp
                      WHERE vp.id = appointments.vet_profile_id
                        AND vp.user_id = ?
                  )
                  AND (
                      (? = 'COMPLETED'
                       AND scheduled_end <= CURRENT_TIMESTAMP)
                      OR
                      (? = 'CANCELLED'
                       AND scheduled_start > CURRENT_TIMESTAMP)
                  )
                """.trimIndent()
            ).use { statement ->

                statement.setString(1, newStatus)
                statement.setLong(2, appointmentId)
                statement.setLong(3, vetUserId)
                statement.setString(4, newStatus)
                statement.setString(5, newStatus)

                statement.executeUpdate() == 1
            }
        }
    }


    private data class VetBookingInfo(
        val profileId: Long,
        val userId: Long,
        val consultationFee: java.math.BigDecimal,
        val timezone: String

    )

    private data class AvailabilitySlot(
        val start: LocalTime,
        val end: LocalTime,
        val durationMinutes: Int
    )
}
