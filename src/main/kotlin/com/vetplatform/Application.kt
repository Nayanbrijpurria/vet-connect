
package com.vetplatform

import com.auth0.jwt.interfaces.Payload
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

fun Application.module() {
    FlywayFactory.migrate()
    configureApplication()
}

fun Application.configureApplication() {

    install(CORS) {
        allowHost("localhost:63342", schemes = listOf("http"))
        allowHost("127.0.0.1:63342", schemes = listOf("http"))

        allowHost("localhost:5500", schemes = listOf("http"))
        allowHost("127.0.0.1:5500", schemes = listOf("http"))

        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
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

                val email = credential.payload
                    .getClaim("email")
                    .asString()

                val role = credential.payload
                    .getClaim("role")
                    .asString()

                if (userId != null &&
                    userId > 0 &&
                    !email.isNullOrBlank() &&
                    !role.isNullOrBlank() &&
                    runCatching { UserRepository.isAccountActive(userId) }
                        .getOrDefault(false)
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
