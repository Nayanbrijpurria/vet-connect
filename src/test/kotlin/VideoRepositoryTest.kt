package com.vetplatform

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VideoRepositoryTest {
    private val appointment = VideoAppointment(
        appointmentId = 42,
        customerId = 10,
        vetUserId = 20,
        status = AppointmentStatus.CONFIRMED,
        scheduledStart = Instant.parse("2026-11-19T10:00:00Z"),
        scheduledEnd = Instant.parse("2026-11-19T10:30:00Z")
    )

    @Test
    fun `only an active owner or assigned approved veterinarian is authorized`() {
        kotlin.test.assertTrue(isAuthorizedVideoParticipant(appointment, 10, "CUSTOMER", true, false))
        kotlin.test.assertFalse(isAuthorizedVideoParticipant(appointment, 11, "CUSTOMER", true, false))
        kotlin.test.assertTrue(isAuthorizedVideoParticipant(appointment, 20, "VET", true, true))
        kotlin.test.assertFalse(isAuthorizedVideoParticipant(appointment, 21, "VET", true, true))
        kotlin.test.assertFalse(isAuthorizedVideoParticipant(appointment, 20, "VET", true, false))
        kotlin.test.assertFalse(isAuthorizedVideoParticipant(appointment, 10, "CUSTOMER", false, false))
    }

    @Test
    fun `only confirmed appointments are eligible and join window uses appointment instant`() {
        kotlin.test.assertTrue(isVideoAppointmentEligible(appointment))
        kotlin.test.assertFalse(isVideoAppointmentEligible(appointment.copy(status = AppointmentStatus.CANCELLED)))
        kotlin.test.assertFalse(isVideoAppointmentEligible(appointment.copy(status = AppointmentStatus.COMPLETED)))

        assertEquals(
            true,
            isWithinVideoJoinWindow(
                Instant.parse("2026-11-19T09:50:00Z"),
                appointment.scheduledStart,
                earlyMinutes = 10,
                lateMinutes = 15
            )
        )
        kotlin.test.assertFalse(
            isWithinVideoJoinWindow(
                Instant.parse("2026-11-19T09:49:59Z"),
                appointment.scheduledStart,
                earlyMinutes = 10,
                lateMinutes = 15
            )
        )
        kotlin.test.assertFalse(
            isWithinVideoJoinWindow(
                Instant.parse("2026-11-19T10:15:01Z"),
                appointment.scheduledStart,
                earlyMinutes = 10,
                lateMinutes = 15
            )
        )
    }

    @Test
    fun `webhook transaction commits successful deduplication and effects together`() {
        val connection = FakeConnection()

        val result = withJdbcTransaction(connection.proxy) { transaction ->
            assertEquals(false, transaction.autoCommit)
            "processed"
        }

        assertEquals("processed", result)
        assertEquals(1, connection.commits)
        assertEquals(0, connection.rollbacks)
        assertEquals(true, connection.autoCommit)
    }

    @Test
    fun `webhook transaction rolls back deduplication when an effect fails`() {
        val connection = FakeConnection()

        assertFailsWith<IllegalStateException> {
            withJdbcTransaction(connection.proxy) {
                error("side effect failed")
            }
        }

        assertEquals(0, connection.commits)
        assertEquals(1, connection.rollbacks)
        assertEquals(true, connection.autoCommit)
    }

    private class FakeConnection : InvocationHandler {
        var autoCommit = true
        var commits = 0
        var rollbacks = 0
        val proxy: Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
            this
        ) as Connection

        override fun invoke(proxy: Any, method: java.lang.reflect.Method, args: Array<out Any?>?): Any? =
            when (method.name) {
                "getAutoCommit" -> autoCommit
                "setAutoCommit" -> {
                    autoCommit = args!![0] as Boolean
                    null
                }
                "commit" -> {
                    commits += 1
                    null
                }
                "rollback" -> {
                    rollbacks += 1
                    null
                }
                "toString" -> "FakeConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> null
            }
    }
}
