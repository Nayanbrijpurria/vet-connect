
package com.vetplatform

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
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
}
