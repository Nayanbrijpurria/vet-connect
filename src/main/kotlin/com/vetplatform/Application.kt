
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

fun Application.module() {
    FlywayFactory.migrate()
    configureApplication()
}

fun Application.configureApplication() {
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
                    !role.isNullOrBlank()
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
