package com.vetplatform

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppointmentAvailabilityRepositoryTest {
    private val date = LocalDate.parse("2026-11-23")
    private val utc = ZoneId.of("UTC")
    private val future = Instant.parse("2026-11-23T00:00:00Z")

    @Test
    fun `empty schedule returns no slots`() {
        assertEquals(
            emptyList(),
            generateAvailableAppointmentSlots(date, utc, future, emptyList(), emptyList())
        )
    }

    @Test
    fun `multiple schedules create sorted future slots in veterinarian timezone`() {
        val slots = generateAvailableAppointmentSlots(
            date = date,
            timezone = ZoneId.of("Asia/Kolkata"),
            now = future,
            schedules = listOf(
                AvailabilitySchedule(LocalTime.of(11, 0), LocalTime.NOON, 30),
                AvailabilitySchedule(LocalTime.of(9, 0), LocalTime.of(10, 0), 30),
                AvailabilitySchedule(LocalTime.of(9, 0), LocalTime.of(10, 0), 30)
            ),
            blockingIntervals = emptyList()
        )

        assertEquals(4, slots.size)
        assertEquals("2026-11-23T09:00+05:30", slots.first().start)
        assertEquals("2026-11-23T11:30+05:30", slots.last().start)
    }

    @Test
    fun `invalid veterinarian timezone is reported as configuration data failure`() {
        assertFailsWith<IllegalStateException> {
            availabilityZoneId("not/a-timezone")
        }
    }

    @Test
    fun `appointments and time off block slots on any overlap but not at adjacent boundaries`() {
        val slots = generateAvailableAppointmentSlots(
            date = date,
            timezone = utc,
            now = future,
            schedules = listOf(
                AvailabilitySchedule(LocalTime.of(9, 0), LocalTime.of(11, 0), 30)
            ),
            blockingIntervals = listOf(
                AvailabilityInterval(
                    Instant.parse("2026-11-23T09:00:00Z"),
                    Instant.parse("2026-11-23T09:30:00Z")
                ),
                AvailabilityInterval(
                    Instant.parse("2026-11-23T09:10:00Z"),
                    Instant.parse("2026-11-23T09:20:00Z")
                ),
                AvailabilityInterval(
                    Instant.parse("2026-11-23T10:00:00Z"),
                    Instant.parse("2026-11-23T10:30:00Z")
                )
            )
        )

        assertEquals(
            listOf("2026-11-23T09:30Z", "2026-11-23T10:30Z"),
            slots.map { it.start }
        )
    }

    @Test
    fun `past slots and nonexistent daylight-saving local times are excluded`() {
        val pastSlots = generateAvailableAppointmentSlots(
            date = date,
            timezone = utc,
            now = Instant.parse("2026-11-23T09:00:00Z"),
            schedules = listOf(
                AvailabilitySchedule(LocalTime.of(9, 0), LocalTime.of(10, 0), 30)
            ),
            blockingIntervals = emptyList()
        )
        assertEquals(listOf("2026-11-23T09:30Z"), pastSlots.map { it.start })

        val daylightSavingSlots = generateAvailableAppointmentSlots(
            date = LocalDate.parse("2026-03-08"),
            timezone = ZoneId.of("America/New_York"),
            now = Instant.parse("2026-03-08T00:00:00Z"),
            schedules = listOf(
                AvailabilitySchedule(LocalTime.of(1, 30), LocalTime.of(3, 30), 30)
            ),
            blockingIntervals = emptyList()
        )
        assertEquals(listOf("2026-03-08T03:00-04:00"), daylightSavingSlots.map { it.start })
    }

    @Test
    fun `normal local slots preserve configured elapsed duration`() {
        val slots = generateAvailableAppointmentSlots(
            date = date,
            timezone = ZoneId.of("America/New_York"),
            now = future,
            schedules = listOf(
                AvailabilitySchedule(LocalTime.of(9, 0), LocalTime.of(10, 0), 30)
            ),
            blockingIntervals = emptyList()
        )

        assertEquals(2, slots.size)
        assertTrue(
            slots.all {
                java.time.Duration.between(
                    java.time.OffsetDateTime.parse(it.start).toInstant(),
                    java.time.OffsetDateTime.parse(it.end).toInstant()
                ).toMinutes() == 30L
            }
        )
    }

    @Test
    fun `fall back slots include distinct valid occurrences without duplicate instants`() {
        val slots = generateAvailableAppointmentSlots(
            date = LocalDate.parse("2026-11-01"),
            timezone = ZoneId.of("America/New_York"),
            now = Instant.parse("2026-10-31T00:00:00Z"),
            schedules = listOf(
                AvailabilitySchedule(LocalTime.of(1, 0), LocalTime.of(2, 0), 30)
            ),
            blockingIntervals = emptyList()
        )

        assertEquals(
            listOf(
                "2026-11-01T01:00-04:00",
                "2026-11-01T01:00-05:00",
                "2026-11-01T01:30-05:00"
            ),
            slots.map { it.start }
        )
        val startInstants = slots.map { java.time.OffsetDateTime.parse(it.start).toInstant() }
        assertEquals(startInstants.size, startInstants.distinct().size)
        assertTrue(
            slots.all {
                java.time.Duration.between(
                    java.time.OffsetDateTime.parse(it.start).toInstant(),
                    java.time.OffsetDateTime.parse(it.end).toInstant()
                ).toMinutes() == 30L
            }
        )
    }

    @Test
    fun `fall back blockers are compared by instant`() {
        val slots = generateAvailableAppointmentSlots(
            date = LocalDate.parse("2026-11-01"),
            timezone = ZoneId.of("America/New_York"),
            now = Instant.parse("2026-10-31T00:00:00Z"),
            schedules = listOf(
                AvailabilitySchedule(LocalTime.of(1, 0), LocalTime.of(2, 0), 30)
            ),
            blockingIntervals = listOf(
                AvailabilityInterval(
                    Instant.parse("2026-11-01T05:00:00Z"),
                    Instant.parse("2026-11-01T05:30:00Z")
                )
            )
        )

        assertEquals(
            listOf("2026-11-01T01:00-05:00", "2026-11-01T01:30-05:00"),
            slots.map { it.start }
        )
    }

    @Test
    fun `availability deadline and caller cancellation do not return partial results`() = runBlocking {
        var timedOut = false
        var timeoutCleanupRan = false
        try {
            withAvailabilityDeadline(20) {
                try {
                    delay(100)
                    listOf("partial")
                } finally {
                    timeoutCleanupRan = true
                }
            }
        } catch (_: TimeoutCancellationException) {
            timedOut = true
        }

        assertTrue(timedOut)
        assertTrue(timeoutCleanupRan)

        var cancellationCleanupRan = false
        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            withAvailabilityDeadline(10_000) {
                try {
                    awaitCancellation()
                } finally {
                    cancellationCleanupRan = true
                }
            }
        }
        delay(20)
        request.cancel(CancellationException("test request cancellation"))
        request.join()
        assertTrue(cancellationCleanupRan)
    }

    @Test
    fun `invalid schedule duration or time range fails instead of looping`() {
        assertFailsWith<IllegalArgumentException> {
            generateAvailableAppointmentSlots(
                date,
                utc,
                future,
                listOf(AvailabilitySchedule(LocalTime.of(9, 0), LocalTime.of(10, 0), 0)),
                emptyList()
            )
        }
        assertFailsWith<IllegalArgumentException> {
            generateAvailableAppointmentSlots(
                date,
                utc,
                future,
                listOf(AvailabilitySchedule(LocalTime.NOON, LocalTime.of(9, 0), 30)),
                emptyList()
            )
        }
    }
}
