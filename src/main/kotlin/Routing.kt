
package com.vetplatform

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.routing.put
import io.ktor.server.routing.delete


fun Route.configureRouting() {

    get("/") {
        call.respondText(
            "Veterinary Platform Backend is running!",
            contentType = ContentType.Text.Plain
        )
    }

    get("/health") {
        call.respondText(
            """{"status":"ok","service":"vet-platform"}""",
            contentType = ContentType.Application.Json
        )
    }

    post("/api/auth/register") {
        val request = call.receive<RegisterRequest>()

        val fullName = request.fullName.trim()
        val email = request.email.trim().lowercase()
        val password = request.password

        if (fullName.isBlank() || fullName.length > 150) {
            call.respond(
                HttpStatusCode.BadRequest,
                AuthResponse(message = "Name must be between 1 and 150 characters")
            )
            return@post
        }

        if (!email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) ||
            email.length > 255
        ) {
            call.respond(
                HttpStatusCode.BadRequest,
                AuthResponse(message = "Please provide a valid email address")
            )
            return@post
        }

        if (password.length < 8 || password.length > 72) {
            call.respond(
                HttpStatusCode.BadRequest,
                AuthResponse(message = "Password must be between 8 and 72 characters")
            )
            return@post
        }

        if (request.phone != null && request.phone.length > 20) {
            call.respond(
                HttpStatusCode.BadRequest,
                AuthResponse(message = "Phone number is too long")
            )
            return@post
        }

        try {
            val passwordHash = PasswordService.hashPassword(password)

            val user = UserRepository.createCustomer(
                fullName = fullName,
                email = email,
                passwordHash = passwordHash,
                phone = request.phone?.trim()?.takeIf { it.isNotEmpty() }
            )

            call.respond(
                HttpStatusCode.Created,
                AuthResponse(
                    message = "Customer registered successfully",
                    userId = user.id,
                    email = user.email,
                    role = user.role
                )
            )
        } catch (exception: java.sql.SQLException) {
            if (exception.sqlState == "23505") {
                call.respond(
                    HttpStatusCode.Conflict,
                    AuthResponse(message = "An account with this email already exists")
                )
            } else {
                application.log.error("Customer registration failed", exception)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AuthResponse(message = "Registration failed")
                )
            }
        } catch (exception: Exception) {
            application.log.error("Customer registration failed", exception)
            call.respond(
                HttpStatusCode.InternalServerError,
                AuthResponse(message = "Registration failed")
            )
        }
    }
//----------------------LOGIN API---------------------
    post("/api/auth/login") {

        val request = call.receive<LoginRequest>()

        val email = request.email.trim().lowercase()
        val password = request.password

        // Validate input
        if (email.isBlank() || password.isBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                AuthResponse(message = "Email and password are required")
            )
            return@post
        }

        try {

            // Step 1: Find user in PostgreSQL
            val user = UserRepository.findByEmail(email)

            // Step 2: Verify email and password
            if (user == null ||
                !PasswordService.verifyPassword(
                    password,
                    user.passwordHash
                )
            ) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Invalid email or password")
                )
                return@post
            }

            // Step 3: Generate JWT
            val token = JwtService.generateToken(user)

            // Step 4: Return login response
            call.respond(
                HttpStatusCode.OK,
                AuthResponse(
                    message = "Login successful",
                    userId = user.id,
                    email = user.email,
                    role = user.role,
                    token = token
                )
            )

        } catch (exception: Exception) {

            application.log.error("Customer login failed", exception)

            call.respond(
                HttpStatusCode.InternalServerError,
                AuthResponse(message = "Login failed")
            )
        }
    }

    //---------Authentication---------------


    authenticate("auth-jwt") {
        get("/api/auth/me") {
            val principal = call.principal<JWTPrincipal>()

            if (principal == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Authentication required")
                )
                return@get
            }

            val userId = principal.payload.subject?.toLongOrNull()
            val email = principal.payload
                .getClaim("email")
                .asString()
            val role = principal.payload
                .getClaim("role")
                .asString()

            if (userId == null || email.isNullOrBlank() || role.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Invalid token claims")
                )
                return@get
            }

            call.respond(
                HttpStatusCode.OK,
                AuthResponse(
                    message = "Authenticated successfully",
                    userId = userId,
                    email = email,
                    role = role
                )
            )
        }


        get("/api/pets") {
            val principal = call.principal<JWTPrincipal>()
            val ownerId = principal?.payload?.subject?.toLongOrNull()

            if (ownerId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Invalid authentication token")
                )
                return@get
            }

            try {
                val pets = PetRepository.findPetsByOwner(ownerId)

                call.respond(
                    HttpStatusCode.OK,
                    PetListResponse(pets = pets)
                )
            } catch (exception: Exception) {
                application.log.error("Failed to retrieve customer pets", exception)

                call.respond(
                    HttpStatusCode.InternalServerError,
                    AuthResponse(message = "Unable to retrieve pets")
                )
            }
        }

        post("/api/pets") {
            val principal = call.principal<JWTPrincipal>()
            val ownerId = principal?.payload?.subject?.toLongOrNull()

            if (ownerId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Invalid authentication token")
                )
                return@post
            }

            val request = try {
                call.receive<CreatePetRequest>()
            } catch (exception: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "Invalid pet request body")
                )
                return@post
            }

            val name = request.name.trim()
            val species = request.species.trim()

            if (name.isBlank() || name.length > 100) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "Pet name must be between 1 and 100 characters")
                )
                return@post
            }

            if (species.isBlank() || species.length > 50) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "Species must be between 1 and 50 characters")
                )
                return@post
            }

            if (request.breed != null && request.breed.length > 100) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "Breed must not exceed 100 characters")
                )
                return@post
            }

            if (request.weightKg != null &&
                (!request.weightKg.isFinite() || request.weightKg <= 0.0 ||
                        request.weightKg > 99999.99)
            ) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "Weight must be a positive, valid number")
                )
                return@post
            }

            if (request.notes != null && request.notes.length > 10000) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "Notes must not exceed 10000 characters")
                )
                return@post
            }

            if (request.dateOfBirth != null) {
                try {
                    java.time.LocalDate.parse(request.dateOfBirth)
                } catch (exception: java.time.format.DateTimeParseException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AuthResponse(message = "Date of birth must use YYYY-MM-DD format")
                    )
                    return@post
                }
            }

            try {
                val pet = PetRepository.createPet(
                    ownerId = ownerId,
                    request = request.copy(
                        name = name,
                        species = species,
                        breed = request.breed?.trim()?.takeIf { it.isNotEmpty() },
                        notes = request.notes?.trim()
                    )
                )

                call.respond(HttpStatusCode.Created, pet)
            } catch (exception: Exception) {
                application.log.error("Failed to create pet", exception)

                call.respond(
                    HttpStatusCode.InternalServerError,
                    AuthResponse(message = "Unable to create pet")
                )
            }
        }


        // Get one pet belonging to the logged-in customer
        get("/api/pets/{id}") {
            val ownerId = call.principal<JWTPrincipal>()
                ?.payload?.subject?.toLongOrNull()
            val petId = call.parameters["id"]?.toLongOrNull()

            if (ownerId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Invalid authentication token")
                )
                return@get
            }

            if (petId == null || petId <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "A valid pet ID is required")
                )
                return@get
            }

            try {
                val pet = PetRepository.findPetById(ownerId, petId)

                if (pet == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        AuthResponse(message = "Pet not found")
                    )
                } else {
                    call.respond(HttpStatusCode.OK, pet)
                }
            } catch (exception: Exception) {
                application.log.error("Failed to retrieve pet", exception)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AuthResponse(message = "Unable to retrieve pet")
                )
            }
        }

        // Update one pet belonging to the logged-in customer
        put("/api/pets/{id}") {
            val ownerId = call.principal<JWTPrincipal>()
                ?.payload?.subject?.toLongOrNull()
            val petId = call.parameters["id"]?.toLongOrNull()

            if (ownerId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Invalid authentication token")
                )
                return@put
            }

            if (petId == null || petId <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "A valid pet ID is required")
                )
                return@put
            }

            val request = try {
                call.receive<CreatePetRequest>()
            } catch (exception: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "Invalid pet request body")
                )
                return@put
            }

            val name = request.name.trim()
            val species = request.species.trim()

            if (name.isBlank() || name.length > 100 ||
                species.isBlank() || species.length > 50 ||
                (request.breed?.length ?: 0) > 100 ||
                (request.notes?.length ?: 0) > 10000 ||
                (request.weightKg != null &&
                        (!request.weightKg.isFinite() ||
                                request.weightKg <= 0.0 ||
                                request.weightKg > 99999.99))
            ) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "Please check the pet details")
                )
                return@put
            }

            if (request.dateOfBirth != null) {
                try {
                    val date = java.time.LocalDate.parse(request.dateOfBirth)
                    if (date.isAfter(java.time.LocalDate.now())) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            AuthResponse(message = "Date of birth cannot be in the future")
                        )
                        return@put
                    }
                } catch (exception: java.time.format.DateTimeParseException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        AuthResponse(message = "Date of birth must use YYYY-MM-DD format")
                    )
                    return@put
                }
            }

            try {
                val pet = PetRepository.updatePet(
                    ownerId = ownerId,
                    petId = petId,
                    request = request.copy(
                        name = name,
                        species = species,
                        breed = request.breed?.trim()?.takeIf { it.isNotEmpty() },
                        notes = request.notes?.trim()
                    )
                )

                if (pet == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        AuthResponse(message = "Pet not found")
                    )
                } else {
                    call.respond(HttpStatusCode.OK, pet)
                }
            } catch (exception: Exception) {
                application.log.error("Failed to update pet", exception)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AuthResponse(message = "Unable to update pet")
                )
            }
        }

        // Delete one pet belonging to the logged-in customer
        delete("/api/pets/{id}") {
            val ownerId = call.principal<JWTPrincipal>()
                ?.payload?.subject?.toLongOrNull()
            val petId = call.parameters["id"]?.toLongOrNull()

            if (ownerId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Invalid authentication token")
                )
                return@delete
            }

            if (petId == null || petId <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "A valid pet ID is required")
                )
                return@delete
            }

            try {
                val deleted = PetRepository.deletePet(ownerId, petId)

                if (deleted) {
                    call.respond(
                        HttpStatusCode.OK,
                        AuthResponse(message = "Pet deleted successfully")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        AuthResponse(message = "Pet not found")
                    )
                }
            } catch (exception: Exception) {
                application.log.error("Failed to delete pet", exception)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AuthResponse(message = "Unable to delete pet")
                )
            }
        }


    }

    //--------------------------------------

    get("/health/db") {
        try {
            val connected = DatabaseFactory.checkConnection()

            if (connected) {
                call.respondText(
                    """{"status":"ok","database":"connected"}""",
                    contentType = ContentType.Application.Json
                )
            } else {
                call.respondText(
                    """{"status":"error","database":"unavailable"}""",
                    status = HttpStatusCode.ServiceUnavailable,
                    contentType = ContentType.Application.Json
                )
            }
        } catch (exception: Exception) {
            application.log.error(
                "Database health check failed",
                exception
            )

            call.respondText(
                """{"status":"error","database":"unavailable"}""",
                status = HttpStatusCode.ServiceUnavailable,
                contentType = ContentType.Application.Json
            )
        }
    }
}
