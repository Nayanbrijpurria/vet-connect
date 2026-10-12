
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
}
