package com.vetplatform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import org.slf4j.LoggerFactory

object AppointmentAvailabilityRepository {
    private const val QUERY_TIMEOUT_SECONDS = 10
    private const val REQUEST_TIMEOUT_MILLIS = 25_000L
    private val logger = LoggerFactory.getLogger(AppointmentAvailabilityRepository::class.java)

    suspend fun getAvailableSlots(
        vetUserId: Long,
        date: LocalDate,
        requestId: String? = null
    ): AvailableAppointmentSlotsResponse {
        val requestStartedAt = System.nanoTime()
        var stage = "connection_acquisition"
        var connectionAcquisitionMs = -1L
        var veterinarianQueryMs = -1L
        var scheduleQueryMs = -1L
        var intervalsQueryMs = -1L
        var slotGenerationMs = -1L
        var slotCount = 0
        var failure: String? = null

        return try {
            withAvailabilityDeadline(REQUEST_TIMEOUT_MILLIS) {
                withContext(Dispatchers.IO) {
                    val requestContext = currentCoroutineContext()
                    val connectionWaitStartedAt = System.nanoTime()
                    val queryData = DatabaseFactory.withConnection { connection ->
                        // JDBC is blocking: check cancellation between statements and rely
                        // on bounded acquisition/query timeouts while a statement is active.
                        requestContext.ensureActive()
                        connectionAcquisitionMs = elapsedMillis(connectionWaitStartedAt)

                        stage = "veterinarian_lookup"
                        val veterinarianQueryStartedAt = System.nanoTime()
                        val veterinarian = connection.prepareStatement(
                            """
                            SELECT vp.user_id, vp.timezone
                            FROM vet_profiles vp
                            JOIN users u ON u.id = vp.user_id
                            WHERE vp.user_id = ?
                              AND u.role = 'VET'
                              AND u.account_status = 'ACTIVE'
                              AND vp.verification_status = 'APPROVED'
                            """.trimIndent()
                        ).use { statement ->
                            statement.queryTimeout = QUERY_TIMEOUT_SECONDS
                            statement.setLong(1, vetUserId)
                            statement.executeQuery().use { rs ->
                                if (rs.next()) {
                                    rs.getLong("user_id") to availabilityZoneId(rs.getString("timezone"))
                                } else {
                                    null
                                }
                            }
                        } ?: throw IllegalArgumentException("Veterinarian not found or not approved")
                        requestContext.ensureActive()
                        veterinarianQueryMs = elapsedMillis(veterinarianQueryStartedAt)

                        val timezone = veterinarian.second
                        require(!date.isBefore(LocalDate.now(timezone))) {
                            "Please select today or a future date"
                        }

                        stage = "schedule_lookup"
                        val scheduleQueryStartedAt = System.nanoTime()
                        val schedules = connection.prepareStatement(
                            """
                            SELECT start_time, end_time, slot_duration_minutes
                            FROM vet_availability
                            WHERE vet_profile_id = ?
                              AND day_of_week = ?
                              AND active = TRUE
                            ORDER BY start_time
                            """.trimIndent()
                        ).use { statement ->
                            statement.queryTimeout = QUERY_TIMEOUT_SECONDS
                            statement.setLong(1, vetUserId)
                            statement.setInt(2, date.dayOfWeek.value)
                            statement.executeQuery().use { rs ->
                                buildList {
                                    while (rs.next()) {
                                        add(
                                            AvailabilitySchedule(
                                                start = rs.getObject("start_time", LocalTime::class.java),
                                                end = rs.getObject("end_time", LocalTime::class.java),
                                                durationMinutes = rs.getInt("slot_duration_minutes")
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        requestContext.ensureActive()
                        scheduleQueryMs = elapsedMillis(scheduleQueryStartedAt)

                        val blockingIntervals = if (schedules.isEmpty()) {
                            emptyList()
                        } else {
                            stage = "interval_lookup"
                            val intervalQueryStartedAt = System.nanoTime()
                            val dayStart = date.atStartOfDay(timezone).toOffsetDateTime()
                            val dayEnd = date.plusDays(1).atStartOfDay(timezone).toOffsetDateTime()

                            val timeOff = connection.prepareStatement(
                                """
                                SELECT starts_at, ends_at
                                FROM vet_time_off
                                WHERE vet_profile_id = ?
                                  AND starts_at < ?
                                  AND ends_at > ?
                                """.trimIndent()
                            ).use { statement ->
                                statement.queryTimeout = QUERY_TIMEOUT_SECONDS
                                statement.setLong(1, vetUserId)
                                statement.setObject(2, dayEnd)
                                statement.setObject(3, dayStart)
                                statement.executeQuery().use { rs ->
                                    buildList {
                                        while (rs.next()) {
                                            add(
                                                AvailabilityInterval(
                                                    start = rs.getObject("starts_at", OffsetDateTime::class.java).toInstant(),
                                                    end = rs.getObject("ends_at", OffsetDateTime::class.java).toInstant()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            requestContext.ensureActive()

                            val appointments = connection.prepareStatement(
                                """
                                SELECT a.scheduled_start, a.scheduled_end
                                FROM appointments a
                                JOIN vet_profiles vp ON vp.id = a.vet_profile_id
                                WHERE vp.user_id = ?
                                  AND a.status IN ('PENDING', 'CONFIRMED')
                                  AND a.scheduled_start < ?
                                  AND a.scheduled_end > ?
                                """.trimIndent()
                            ).use { statement ->
                                statement.queryTimeout = QUERY_TIMEOUT_SECONDS
                                statement.setLong(1, vetUserId)
                                statement.setObject(2, dayEnd)
                                statement.setObject(3, dayStart)
                                statement.executeQuery().use { rs ->
                                    buildList {
                                        while (rs.next()) {
                                            add(
                                                AvailabilityInterval(
                                                    start = rs.getObject("scheduled_start", OffsetDateTime::class.java).toInstant(),
                                                    end = rs.getObject("scheduled_end", OffsetDateTime::class.java).toInstant()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            requestContext.ensureActive()
                            intervalsQueryMs = elapsedMillis(intervalQueryStartedAt)
                            timeOff + appointments
                        }

                        AvailabilityQueryData(
                            timezone = timezone,
                            schedules = schedules,
                            blockingIntervals = blockingIntervals
                        )
                    }

                    requestContext.ensureActive()
                    stage = "slot_generation"
                    val slotGenerationStartedAt = System.nanoTime()
                    val slots = generateAvailableAppointmentSlots(
                        date = date,
                        timezone = queryData.timezone,
                        now = Instant.now(),
                        schedules = queryData.schedules,
                        blockingIntervals = queryData.blockingIntervals
                    )
                    requestContext.ensureActive()
                    slotGenerationMs = elapsedMillis(slotGenerationStartedAt)
                    slotCount = slots.size

                    AvailableAppointmentSlotsResponse(
                        vetUserId = vetUserId,
                        date = date.toString(),
                        timezone = queryData.timezone.id,
                        slots = slots
                    )
                }
            }
        } catch (exception: TimeoutCancellationException) {
            failure = "request_timeout"
            throw exception
        } catch (exception: CancellationException) {
            failure = "cancelled"
            throw exception
        } catch (exception: Exception) {
            failure = exception.javaClass.simpleName
            throw exception
        } finally {
            logger.info(
                "appointment_availability_completed request_id={} vet_user_id={} requested_date={} stage={} failure={} " +
                    "connection_acquisition_ms={} veterinarian_query_ms={} schedule_query_ms={} " +
                    "intervals_query_ms={} slot_generation_ms={} slot_count={} total_ms={}",
                requestId ?: "unavailable",
                vetUserId,
                date,
                stage,
                failure ?: "none",
                connectionAcquisitionMs,
                veterinarianQueryMs,
                scheduleQueryMs,
                intervalsQueryMs,
                slotGenerationMs,
                slotCount,
                elapsedMillis(requestStartedAt)
            )
        }
    }
}

internal data class AvailabilityQueryData(
    val timezone: ZoneId,
    val schedules: List<AvailabilitySchedule>,
    val blockingIntervals: List<AvailabilityInterval>
)

internal data class AvailabilitySchedule(
    val start: LocalTime,
    val end: LocalTime,
    val durationMinutes: Int
)

internal data class AvailabilityInterval(
    val start: Instant,
    val end: Instant
)

internal suspend fun <T> withAvailabilityDeadline(
    timeoutMillis: Long,
    block: suspend () -> T
): T = withTimeout(timeoutMillis) {
    block()
}

/**
 * Gaps produce no slots. During a repeated hour, each valid offset pair is considered,
 * but only pairs matching the schedule's exact elapsed duration are bookable.
 */
internal fun generateAvailableAppointmentSlots(
    date: LocalDate,
    timezone: ZoneId,
    now: Instant,
    schedules: List<AvailabilitySchedule>,
    blockingIntervals: List<AvailabilityInterval>
): List<AvailableAppointmentSlot> {
    val uniqueSlots = linkedMapOf<Pair<Instant, Instant>, AvailableAppointmentSlot>()
    val zoneRules = timezone.rules
    val mergedBlockingIntervals = mergeAvailabilityIntervals(blockingIntervals)

    for (schedule in schedules) {
        require(schedule.durationMinutes in 10..240) {
            "Veterinarian availability has an invalid slot duration"
        }
        require(schedule.end.isAfter(schedule.start)) {
            "Veterinarian availability has an invalid time range"
        }

        var slotStart = schedule.start
        while (!slotStart.plusMinutes(schedule.durationMinutes.toLong()).isAfter(schedule.end)) {
            val slotEnd = slotStart.plusMinutes(schedule.durationMinutes.toLong())
            val localStart = date.atTime(slotStart)
            val localEnd = date.atTime(slotEnd)
            val validStartOffsets = zoneRules.getValidOffsets(localStart)
            val validEndOffsets = zoneRules.getValidOffsets(localEnd)
            val expectedDuration = Duration.ofMinutes(schedule.durationMinutes.toLong())

            // Weekly schedules define local endpoints; only offset pairs preserving the
            // configured elapsed duration are bookable at a DST transition.
            for (startOffset in validStartOffsets) {
                val startInstant = localStart.toInstant(startOffset)
                for (endOffset in validEndOffsets) {
                    val endInstant = localEnd.toInstant(endOffset)
                    if (
                        !endInstant.isAfter(startInstant) ||
                        Duration.between(startInstant, endInstant) != expectedDuration ||
                        !startInstant.isAfter(now) ||
                        availabilityIntervalOverlaps(mergedBlockingIntervals, startInstant, endInstant)
                    ) {
                        continue
                    }

                    uniqueSlots.putIfAbsent(
                        startInstant to endInstant,
                        AvailableAppointmentSlot(
                            start = OffsetDateTime.of(localStart, startOffset).toString(),
                            end = OffsetDateTime.of(localEnd, endOffset).toString()
                        )
                    )
                }
            }

            slotStart = slotEnd
        }
    }

    return uniqueSlots.values.sortedBy { OffsetDateTime.parse(it.start).toInstant() }
}

private fun mergeAvailabilityIntervals(
    intervals: List<AvailabilityInterval>
): List<AvailabilityInterval> {
    val sorted = intervals.sortedBy { it.start }
    val merged = mutableListOf<AvailabilityInterval>()
    for (interval in sorted) {
        val previous = merged.lastOrNull()
        if (previous != null && !interval.start.isAfter(previous.end)) {
            merged[merged.lastIndex] = AvailabilityInterval(
                start = previous.start,
                end = maxOf(previous.end, interval.end)
            )
        } else {
            merged.add(interval)
        }
    }
    return merged
}

private fun availabilityIntervalOverlaps(
    intervals: List<AvailabilityInterval>,
    start: Instant,
    end: Instant
): Boolean {
    var low = 0
    var high = intervals.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (!intervals[middle].end.isAfter(start)) {
            low = middle + 1
        } else {
            high = middle
        }
    }
    return low < intervals.size && intervals[low].start.isBefore(end)
}

private fun elapsedMillis(startedAt: Long): Long =
    (System.nanoTime() - startedAt) / 1_000_000L

internal fun availabilityZoneId(timezone: String): ZoneId =
    try {
        ZoneId.of(timezone)
    } catch (exception: java.time.DateTimeException) {
        throw IllegalStateException("Veterinarian has an invalid timezone configured", exception)
    }
