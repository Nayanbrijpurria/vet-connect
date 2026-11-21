
package com.vetplatform

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {

    private fun configureTestApplication() {
        // Configure a test-only JWT secret before the application starts.
        // JWT verification is not disabled by this setup.
    }

    @Test
    fun `test root endpoint`() = testApplication {
        application {
            configureApplication()
        }

        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `test health endpoint`() = testApplication {
        application {
            configureApplication()
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.bodyAsText().contains("\"status\":\"ok\"")
        )
    }

    @Test
    fun `medical history endpoints require authentication`() = testApplication {
        application { configureApplication() }

        val customerResponse = client.get("/api/pets/1/medical-history")
        val veterinarianResponse = client.get("/api/vets/appointments/1/medical-history")

        assertEquals(HttpStatusCode.Unauthorized, customerResponse.status)
        assertEquals(HttpStatusCode.Unauthorized, veterinarianResponse.status)
    }

    @Test
    fun `admin veterinarian queue rejects unauthenticated requests`() = testApplication {
        application { configureApplication() }

        val response = client.get("/api/admin/vets/pending")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `availability route rejects invalid veterinarian IDs and dates without database access`() = testApplication {
        application { configureApplication() }

        val invalidVet = client.get("/api/vets/not-a-vet/available-slots?date=2026-11-23")
        val invalidDate = client.get("/api/vets/8/available-slots?date=2026-02-30")

        assertEquals(HttpStatusCode.BadRequest, invalidVet.status)
        assertEquals(HttpStatusCode.BadRequest, invalidDate.status)
    }

    @Test
    fun `Google sign-in rejects requests without the VetConnect origin and CSRF header`() = testApplication {
        application { configureApplication() }

        val noOrigin = client.post("/api/auth/google")
        assertEquals(HttpStatusCode.Forbidden, noOrigin.status)

        val noCsrf = client.post("/api/auth/google") {
            header(HttpHeaders.Origin, "https://vet-connect-frontend-reemltg48.vercel.app")
        }
        assertEquals(HttpStatusCode.Forbidden, noCsrf.status)

        val untrustedOrigin = client.post("/api/auth/google") {
            header(HttpHeaders.Origin, "https://attacker.example")
            header("X-VetConnect-CSRF", "1")
        }
        assertEquals(HttpStatusCode.Forbidden, untrustedOrigin.status)
    }

    @Test
    fun `Google public configuration returns only the client id field`() = testApplication {
        application { configureApplication() }

        val response = client.get("/api/auth/google/config")
        assertTrue(response.status == HttpStatusCode.OK || response.status == HttpStatusCode.ServiceUnavailable)
        assertTrue(response.bodyAsText().matches(Regex("""\{"clientId":"[^"]*"\}""")))
    }
}
