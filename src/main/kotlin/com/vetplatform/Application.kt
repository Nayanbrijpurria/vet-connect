
package com.vetplatform

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import java.net.URI
import kotlinx.coroutines.CancellationException

fun Application.module() {
    FlywayFactory.migrate()
    configureApplication()
}

fun Application.configureApplication() {



    install(CORS) {
        AuthSecurity.allowedOrigins.forEach { origin ->
            val uri = URI(origin)
            allowHost(uri.rawAuthority, schemes = listOf(uri.scheme))
        }

        // Local development
        allowHost("localhost:63342", schemes = listOf("http"))
        allowHost("127.0.0.1:63342", schemes = listOf("http"))
        allowHost("localhost:5500", schemes = listOf("http"))
        allowHost("127.0.0.1:5500", schemes = listOf("http"))

        // Production frontend - Vercel
//        allowHost(
//            "vet-connect-frontend-nine.vercel.app",
//            schemes = listOf("https")
//        )
        allowCredentials = true

        // HTTP methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        // Allowed headers
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(AuthSecurity.CSRF_HEADER)
    }


    install(ContentNegotiation) {
        json()
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "vetplatform"
            verifier(JwtService.verifier())

            validate { credential ->
                val userId = credential.payload
                    .getClaim("userId")
                    .asLong()
                val subjectUserId = credential.payload.subject?.toLongOrNull()

                val email = credential.payload
                    .getClaim("email")
                    .asString()

                val role = credential.payload
                    .getClaim("role")
                    .asString()

                val activeRole = if (userId != null && !role.isNullOrBlank()) {
                    try {
                        UserRepository.hasActiveRole(userId, role)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }

                if (userId != null &&
                    userId > 0 &&
                    subjectUserId == userId &&
                    !email.isNullOrBlank() &&
                    !role.isNullOrBlank() &&
                    activeRole
                ) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    routing {
        configureRouting()
    }
}
