
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
import io.ktor.server.http.content.static
import io.ktor.server.routing.put
import io.ktor.server.routing.delete
import io.ktor.server.routing.patch
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.readAvailable
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import java.io.ByteArrayOutputStream

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


    // ---------------- VETERINARIAN REGISTRATION ----------------
    post("/api/vets/register") {
        val request = try {
            call.receive<VetRegistrationRequest>()
        } catch (exception: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                VetRegistrationResponse(
                    message = "Invalid veterinarian registration request"
                )
            )
            return@post
        }

        val fullName = request.fullName.trim()
        val email = request.email.trim().lowercase()
        val password = request.password
        val phone = request.phone?.trim()?.takeIf { it.isNotEmpty() }
        val qualification = request.qualification.trim()
        val registrationNumber = request.registrationNumber.trim()

        if (fullName.isBlank() || fullName.length > 150) {
            call.respond(
                HttpStatusCode.BadRequest,
                VetRegistrationResponse(
                    message = "Name must be between 1 and 150 characters"
                )
            )
            return@post
        }

        if (
            !email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) ||
            email.length > 255
        ) {
            call.respond(
                HttpStatusCode.BadRequest,
                VetRegistrationResponse(message = "Invalid email address")
            )
            return@post
        }

        if (password.length < 8 || password.length > 72) {
            call.respond(
                HttpStatusCode.BadRequest,
                VetRegistrationResponse(
                    message = "Password must be between 8 and 72 characters"
                )
            )
            return@post
        }

        if (phone != null && phone.length > 20) {
            call.respond(
                HttpStatusCode.BadRequest,
                VetRegistrationResponse(message = "Phone number is too long")
            )
            return@post
        }

        if (qualification.isBlank() || qualification.length > 150) {
            call.respond(
                HttpStatusCode.BadRequest,
                VetRegistrationResponse(
                    message = "Qualification must be between 1 and 150 characters"
                )
            )
            return@post
        }

        if (
            registrationNumber.isBlank() ||
            registrationNumber.length > 100
        ) {
            call.respond(
                HttpStatusCode.BadRequest,
                VetRegistrationResponse(
                    message = "Registration number must be between 1 and 100 characters"
                )
            )
            return@post
        }

        if (request.experienceYears !in 0..70) {
            call.respond(
                HttpStatusCode.BadRequest,
                VetRegistrationResponse(
                    message = "Experience must be between 0 and 70 years"
                )
            )
            return@post
        }

        if (
            !request.consultationFee.isFinite() ||
            request.consultationFee < 0.0 ||
            request.consultationFee > 1000000.0
        ) {
            call.respond(
                HttpStatusCode.BadRequest,
                VetRegistrationResponse(
                    message = "Please enter a valid consultation fee"
                )
            )
            return@post
        }

        try {
            val passwordHash = PasswordService.hashPassword(password)

            val user = VetRepository.registerVet(
                fullName = fullName,
                email = email,
                passwordHash = passwordHash,
                phone = phone,
                qualification = qualification,
                registrationNumber = registrationNumber,
                experienceYears = request.experienceYears,
                consultationFee = request.consultationFee
            )

            call.respond(
                HttpStatusCode.Created,
                VetRegistrationResponse(
                    message = "Veterinarian application submitted. Verification is pending.",
                    userId = user.id,
                    email = user.email,
                    role = user.role,
                    verificationStatus = "PENDING"
                )
            )
        } catch (exception: java.sql.SQLException) {
            if (exception.sqlState == "23505") {
                call.respond(
                    HttpStatusCode.Conflict,
                    VetRegistrationResponse(
                        message = "This email or registration number is already registered"
                    )
                )
            } else {
                application.log.error(
                    "Veterinarian registration failed",
                    exception
                )
                call.respond(
                    HttpStatusCode.InternalServerError,
                    VetRegistrationResponse(
                        message = "Veterinarian registration failed"
                    )
                )
            }
        } catch (exception: Exception) {
            application.log.error(
                "Veterinarian registration failed",
                exception
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                VetRegistrationResponse(
                    message = "Veterinarian registration failed"
                )
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


        post("/api/vets/time-off") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toLongOrNull()

            if (userId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid authentication")
                )
                return@post
            }

            try {
                if (UserRepository.findRoleById(userId) != "VET") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Only veterinarians can manage time off")
                    )
                    return@post
                }

                if (!VetRepository.isVetApproved(userId)) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Your veterinarian profile must be approved first")
                    )
                    return@post
                }

                val request = call.receive<CreateTimeOffRequest>()
                val result = TimeOffRepository.createTimeOff(userId, request)

                call.respond(HttpStatusCode.Created, result)

            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid time-off request"))
                )
            } catch (e: Exception) {
                call.application.log.error("Failed to create veterinarian time off", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Could not create time off")
                )
            }
        }

        get("/api/vets/time-off") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toLongOrNull()

            if (userId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid authentication")
                )
                return@get
            }

            try {
                if (UserRepository.findRoleById(userId) != "VET") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Only veterinarians can view time off")
                    )
                    return@get
                }

                call.respond(
                    HttpStatusCode.OK,
                    TimeOffRepository.getTimeOff(userId)
                )

            } catch (e: Exception) {
                call.application.log.error("Failed to retrieve veterinarian time off", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Could not retrieve time off")
                )
            }
        }

        delete("/api/vets/time-off/{id}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toLongOrNull()

            if (userId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid authentication")
                )
                return@delete
            }

            val timeOffId = call.parameters["id"]?.toLongOrNull()
            if (timeOffId == null || timeOffId <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "A valid time-off ID is required")
                )
                return@delete
            }

            try {
                if (UserRepository.findRoleById(userId) != "VET") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Only veterinarians can manage time off")
                    )
                    return@delete
                }

                val deleted = TimeOffRepository.deleteTimeOff(userId, timeOffId)

                if (deleted) {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("message" to "Time off deleted")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Time-off record not found")
                    )
                }

            } catch (e: Exception) {
                call.application.log.error("Failed to delete veterinarian time off", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Could not delete time off")
                )
            }
        }


        post("/api/vets/availability") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toLongOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid authentication"))
                return@post
            }

            try {
                val databaseRole = UserRepository.findRoleById(userId)
                if (databaseRole != "VET") {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only veterinarians can manage availability"))
                    return@post
                }

                if (!VetRepository.isVetApproved(userId)) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Your veterinarian profile must be approved first"))
                    return@post
                }

                val request = call.receive<CreateAvailabilityRequest>()
                val result = AvailabilityRepository.createAvailability(userId, request)
                call.respond(HttpStatusCode.Created, result)

            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid availability")))
            } catch (e: Exception) {
                call.application.log.error("Failed to create veterinarian availability", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Could not create availability"))
            }
        }

        get("/api/vets/availability") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toLongOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid authentication"))
                return@get
            }

            try {
                val databaseRole = UserRepository.findRoleById(userId)
                if (databaseRole != "VET") {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only veterinarians can view their availability"))
                    return@get
                }

                val result = AvailabilityRepository.getAvailability(userId)
                call.respond(HttpStatusCode.OK, result)

            } catch (e: Exception) {
                call.application.log.error("Failed to retrieve veterinarian availability", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Could not retrieve availability"))
            }
        }

        delete("/api/vets/availability/{id}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toLongOrNull()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid authentication"))
                return@delete
            }

            val availabilityId = call.parameters["id"]?.toLongOrNull()
            if (availabilityId == null || availabilityId <= 0) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "A valid availability ID is required"))
                return@delete
            }

            try {
                val databaseRole = UserRepository.findRoleById(userId)
                if (databaseRole != "VET") {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Only veterinarians can manage availability"))
                    return@delete
                }

                val deactivated = AvailabilityRepository.deactivateAvailability(userId, availabilityId)

                if (deactivated) {
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Availability deactivated"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Availability not found or already inactive"))
                }

            } catch (e: Exception) {
                call.application.log.error("Failed to deactivate veterinarian availability", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Could not deactivate availability"))
            }
        }


        get("/api/vets/verification-status") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.subject?.toLongOrNull()
            val role = principal?.payload
                ?.getClaim("role")
                ?.asString()

            if (userId == null || role.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Invalid authentication token")
                )
                return@get
            }

            if (role != "VET") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    AuthResponse(
                        message = "Only veterinarian accounts can access this endpoint"
                    )
                )
                return@get
            }

            try {
                // Check the user's current role in PostgreSQL.
                val currentRole = UserRepository.findRoleById(userId)

                if (currentRole != "VET") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        AuthResponse(
                            message = "This account is not an active veterinarian account"
                        )
                    )
                    return@get
                }

                // Pending and rejected veterinarians can still check their status.
                val status = VetRepository.getVerificationStatus(userId)

                if (status == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        VetRegistrationResponse(
                            message = "Veterinarian profile not found"
                        )
                    )
                } else {
                    call.respond(HttpStatusCode.OK, status)
                }
            } catch (exception: Exception) {
                application.log.error(
                    "Failed to retrieve veterinarian verification status",
                    exception
                )

                call.respond(
                    HttpStatusCode.InternalServerError,
                    VetRegistrationResponse(
                        message = "Unable to retrieve verification status"
                    )
                )
            }
        }




        // List pending veterinarian applications.
        get("/api/admin/vets/pending") {
            val principal = call.principal<JWTPrincipal>()
            val adminId = principal?.payload?.subject?.toLongOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()

            if (adminId == null || role.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Invalid authentication token")
                )
            } else if (role != "ADMIN") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    AuthResponse(message = "Administrator access required")
                )
            } else {
                try {
                    // Confirm the account's current database role.
                    val currentRole = UserRepository.findRoleById(adminId)

                    if (currentRole != "ADMIN") {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            AuthResponse(
                                message = "Administrator access required"
                            )
                        )
                        return@get
                    }

                    val applications = AdminRepository.getPendingVets()

                    call.respond(
                        HttpStatusCode.OK,
                        PendingVetsListResponse(applications)
                    )
                } catch (exception: Exception) {
                    application.log.error(
                        "Failed to retrieve pending veterinarian applications",
                        exception
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        AuthResponse(message = "Unable to retrieve applications")
                    )
                }
            }
        }

        // Approve a pending veterinarian application.
        put("/api/admin/vets/{userId}/approve") {
            val principal = call.principal<JWTPrincipal>()
            val adminId = principal?.payload?.subject?.toLongOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()
            val userId = call.parameters["userId"]?.toLongOrNull()

            if (adminId == null || role.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Invalid authentication token")
                )
            } else if (role != "ADMIN") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    AuthResponse(message = "Administrator access required")
                )
            } else if (userId == null || userId <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "A valid veterinarian user ID is required")
                )
            } else {
                try {
                    val result = AdminRepository.reviewVet(
                        userId = userId,
                        adminId = adminId,
                        approved = true
                    )

                    if (result == null) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            AuthResponse(
                                message = "Application not found or already reviewed"
                            )
                        )
                    } else {
                        call.respond(HttpStatusCode.OK, result)
                    }
                } catch (exception: SecurityException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        AuthResponse(message = "Administrator access required")
                    )
                } catch (exception: Exception) {
                    application.log.error(
                        "Failed to approve veterinarian application",
                        exception
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        AuthResponse(message = "Unable to approve application")
                    )
                }
            }
        }

        // Reject a pending veterinarian application.
        put("/api/admin/vets/{userId}/reject") {
            val principal = call.principal<JWTPrincipal>()
            val adminId = principal?.payload?.subject?.toLongOrNull()
            val role = principal?.payload?.getClaim("role")?.asString()
            val userId = call.parameters["userId"]?.toLongOrNull()

            if (adminId == null || role.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    AuthResponse(message = "Invalid authentication token")
                )
            } else if (role != "ADMIN") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    AuthResponse(message = "Administrator access required")
                )
            } else if (userId == null || userId <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    AuthResponse(message = "A valid veterinarian user ID is required")
                )
            } else {
                try {
                    val request = call.receive<VetReviewRequest>()
                    val reason = request.rejectionReason?.trim()

                    if (reason.isNullOrBlank() || reason.length > 500) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            AuthResponse(
                                message = "Rejection reason is required and must not exceed 500 characters"
                            )
                        )
                    } else {
                        val result = AdminRepository.reviewVet(
                            userId = userId,
                            adminId = adminId,
                            approved = false,
                            rejectionReason = reason
                        )

                        if (result == null) {
                            call.respond(
                                HttpStatusCode.Conflict,
                                AuthResponse(
                                    message = "Application not found or already reviewed"
                                )
                            )
                        } else {
                            call.respond(HttpStatusCode.OK, result)
                        }
                    }
                } catch (exception: SecurityException) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        AuthResponse(message = "Administrator access required")
                    )
                } catch (exception: Exception) {
                    application.log.error(
                        "Failed to reject veterinarian application",
                        exception
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        AuthResponse(message = "Unable to reject application")
                    )
                }
            }
        }


        //------------------------------------------------------
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

        // Create an appointment with instant confirmation.
        post("/api/appointments") {
            val principal = call.principal<JWTPrincipal>()
            val customerId = principal?.payload?.subject?.toLongOrNull()

            if (customerId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid authentication token")
                )
                return@post
            }

            try {
                // Check the current database role, not just the JWT claim.
                val currentRole = UserRepository.findRoleById(customerId)

                if (currentRole != "CUSTOMER") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Only customer accounts can book appointments")
                    )
                    return@post
                }

                val request = try {
                    call.receive<CreateAppointmentRequest>()
                } catch (exception: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid appointment request body")
                    )
                    return@post
                }

                if (request.petId <= 0 || request.vetUserId <= 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Valid petId and vetUserId are required")
                    )
                    return@post
                }

                val appointment = AppointmentRepository.createAppointment(
                    customerId = customerId,
                    request = request
                )

                call.respond(HttpStatusCode.Created, appointment)

            } catch (exception: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (exception.message ?: "Invalid appointment request"))
                )
            } catch (exception: java.sql.SQLException) {
                if (exception.sqlState == "23P01") {
                    // PostgreSQL exclusion constraint: conflicting appointment.
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "This veterinarian's time slot has already been booked")
                    )
                } else {
                    application.log.error("Appointment booking failed", exception)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Unable to book appointment")
                    )
                }
            } catch (exception: Exception) {
                application.log.error("Appointment booking failed", exception)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Unable to book appointment")
                )
            }
        }

        //-------------

        get("/api/appointments") {
            val principal = call.principal<JWTPrincipal>()
            val customerId = principal?.payload?.subject?.toLongOrNull()

            if (customerId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid authentication token")
                )
                return@get
            }

            try {
                val role = UserRepository.findRoleById(customerId)

                if (role != "CUSTOMER") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Only customers can view their appointments")
                    )
                    return@get
                }

                val appointments =
                    AppointmentRepository.getCustomerAppointments(customerId)

                call.respond(
                    HttpStatusCode.OK,
                    AppointmentListResponse(appointments)
                )
            } catch (exception: Exception) {
                application.log.error("Failed to retrieve customer appointments", exception)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Unable to retrieve appointments")
                )
            }
        }

        patch("/api/appointments/{id}/cancel") {
            val principal = call.principal<JWTPrincipal>()
            val customerId = principal?.payload?.subject?.toLongOrNull()

            if (customerId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid authentication token")
                )
                return@patch
            }

            try {
                val role = UserRepository.findRoleById(customerId)

                if (role != "CUSTOMER") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Only customers can cancel appointments")
                    )
                    return@patch
                }

                val appointmentId =
                    call.parameters["id"]?.toLongOrNull()

                if (appointmentId == null || appointmentId <= 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "A valid appointment ID is required")
                    )
                    return@patch
                }

                val cancelled = AppointmentRepository.cancelCustomerAppointment(
                    customerId = customerId,
                    appointmentId = appointmentId
                )

                if (cancelled) {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("message" to "Appointment cancelled successfully")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf(
                            "error" to "Appointment cannot be cancelled. It may not belong to you, may have already started, or may already be completed or cancelled."
                        )
                    )
                }
            } catch (exception: Exception) {
                application.log.error("Failed to cancel appointment", exception)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Unable to cancel appointment")
                )
            }
        }


        // View appointments assigned to the logged-in veterinarian.
        get("/api/vets/appointments") {
            val vetUserId = call.principal<JWTPrincipal>()
                ?.payload?.subject?.toLongOrNull()

            if (vetUserId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid authentication token")
                )
                return@get
            }

            try {
                if (UserRepository.findRoleById(vetUserId) != "VET") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Only veterinarians can view these appointments")
                    )
                    return@get
                }

                if (!VetRepository.isVetApproved(vetUserId)) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Your veterinarian profile must be approved")
                    )
                    return@get
                }

                val appointments =
                    AppointmentRepository.getVetAppointments(vetUserId)

                call.respond(
                    HttpStatusCode.OK,
                    AppointmentListResponse(appointments)
                )
            } catch (exception: Exception) {
                application.log.error(
                    "Failed to retrieve veterinarian appointments",
                    exception
                )
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Unable to retrieve appointments")
                )
            }
        }

        // Update the status of an appointment assigned to this veterinarian.
        patch("/api/vets/appointments/{id}/status") {
            val vetUserId = call.principal<JWTPrincipal>()
                ?.payload?.subject?.toLongOrNull()

            if (vetUserId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid authentication token")
                )
                return@patch
            }

            try {
                if (UserRepository.findRoleById(vetUserId) != "VET") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Only veterinarians can update appointments")
                    )
                    return@patch
                }

                if (!VetRepository.isVetApproved(vetUserId)) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Your veterinarian profile must be approved")
                    )
                    return@patch
                }

                val appointmentId = call.parameters["id"]?.toLongOrNull()

                if (appointmentId == null || appointmentId <= 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "A valid appointment ID is required")
                    )
                    return@patch
                }

                val request = try {
                    call.receive<AppointmentActionRequest>()
                } catch (exception: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid request body. Provide an action.")
                    )
                    return@patch
                }

                val newStatus = request.action.trim().uppercase()

                if (newStatus != "COMPLETED" && newStatus != "CANCELLED") {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Action must be COMPLETED or CANCELLED")
                    )
                    return@patch
                }

                val updated =
                    AppointmentRepository.updateVetAppointmentStatus(
                        vetUserId = vetUserId,
                        appointmentId = appointmentId,
                        newStatus = newStatus
                    )

                if (updated) {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("message" to "Appointment status updated", "status" to newStatus)
                    )
                } else {
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf(
                            "error" to "Appointment not found, not assigned to you, not confirmed, or the requested status change is not allowed at this time"
                        )
                    )
                }
            } catch (exception: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (exception.message ?: "Invalid appointment status"))
                )
            } catch (exception: Exception) {
                application.log.error(
                    "Failed to update veterinarian appointment",
                    exception
                )
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Unable to update appointment")
                )
            }
        }



        // Create a consultation for a completed appointment.
        post("/api/vets/consultations") {
            val vetUserId = call.principal<JWTPrincipal>()
                ?.payload?.subject?.toLongOrNull()

            if (vetUserId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid authentication token")
                )
                return@post
            }

            try {
                if (UserRepository.findRoleById(vetUserId) != "VET") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Only veterinarians can create consultations")
                    )
                    return@post
                }

                if (!VetRepository.isVetApproved(vetUserId)) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Your veterinarian profile must be approved")
                    )
                    return@post
                }

                val request = try {
                    call.receive<CreateConsultationRequest>()
                } catch (exception: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid consultation request body")
                    )
                    return@post
                }

                if (request.appointmentId <= 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "A valid appointment ID is required")
                    )
                    return@post
                }

                val fields = listOf(
                    request.chiefComplaint,
                    request.clinicalNotes,
                    request.diagnosis,
                    request.treatmentNotes,
                    request.followUpNotes
                )

                if (fields.any { it != null && it.length > 10000 }) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Consultation fields must not exceed 10000 characters")
                    )
                    return@post
                }

                val consultation = ConsultationRepository.createConsultation(
                    vetUserId = vetUserId,
                    request = request
                )

                call.respond(HttpStatusCode.Created, consultation)

            } catch (exception: SecurityException) {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "You are not authorized for this appointment")
                )
            } catch (exception: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (exception.message ?: "Invalid consultation request"))
                )
            } catch (exception: java.sql.SQLException) {
                if (exception.sqlState == "23505") {
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "A consultation already exists for this appointment")
                    )
                } else {
                    application.log.error("Failed to create consultation", exception)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Unable to create consultation")
                    )
                }
            } catch (exception: Exception) {
                application.log.error("Failed to create consultation", exception)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Unable to create consultation")
                )
            }
        }

        // List consultations belonging to the logged-in veterinarian.
        get("/api/vets/consultations") {
            val vetUserId = call.principal<JWTPrincipal>()
                ?.payload?.subject?.toLongOrNull()

            if (vetUserId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid authentication token")
                )
                return@get
            }

            try {
                if (UserRepository.findRoleById(vetUserId) != "VET") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Only veterinarians can view consultations")
                    )
                    return@get
                }

                if (!VetRepository.isVetApproved(vetUserId)) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Your veterinarian profile must be approved")
                    )
                    return@get
                }

                val consultations =
                    ConsultationRepository.getVetConsultations(vetUserId)

                call.respond(
                    HttpStatusCode.OK,
                    ConsultationListResponse(consultations)
                )

            } catch (exception: Exception) {
                application.log.error("Failed to retrieve consultations", exception)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Unable to retrieve consultations")
                )
            }
        }

        // Retrieve one consultation assigned to the logged-in veterinarian.
        get("/api/vets/consultations/{id}") {
            val vetUserId = call.principal<JWTPrincipal>()
                ?.payload?.subject?.toLongOrNull()

            if (vetUserId == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "Invalid authentication token")
                )
                return@get
            }

            val consultationId =
                call.parameters["id"]?.toLongOrNull()

            if (consultationId == null || consultationId <= 0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "A valid consultation ID is required")
                )
                return@get
            }

            try {
                if (UserRepository.findRoleById(vetUserId) != "VET") {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Only veterinarians can view consultations")
                    )
                    return@get
                }

                if (!VetRepository.isVetApproved(vetUserId)) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Your veterinarian profile must be approved")
                    )
                    return@get
                }

                val consultation = ConsultationRepository.getVetConsultation(
                    vetUserId = vetUserId,
                    consultationId = consultationId
                )

                if (consultation == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Consultation not found")
                    )
                } else {
                    call.respond(HttpStatusCode.OK, consultation)
                }

            } catch (exception: Exception) {
                application.log.error("Failed to retrieve consultation", exception)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Unable to retrieve consultation")
                )
            }
        }

        configureMedicalDocumentRoutes()


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


private fun Route.configureMedicalDocumentRoutes() {
    post("/api/pets/{id}/documents") {
        val userId = call.principal<JWTPrincipal>()
            ?.payload?.subject?.toLongOrNull()

        if (userId == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("error" to "Invalid authentication token")
            )
            return@post
        }

        val petId = call.parameters["id"]?.toLongOrNull()
        if (petId == null || petId <= 0) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "A valid pet ID is required")
            )
            return@post
        }

        try {
            if (UserRepository.findRoleById(userId) != "CUSTOMER") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Only customers can upload pet documents")
                )
                return@post
            }

            val allowedTypes = setOf(
                "LAB_REPORT",
                "PRESCRIPTION",
                "MEDICAL_HISTORY",
                "IMAGING",
                "OTHER"
            )

            var documentType: String? = null
            var consultationId: Long? = null
            var originalFilename: String? = null
            var contentType: String? = null
            var fileBytes: ByteArray? = null

            val multipart = call.receiveMultipart(
                formFieldLimit = 16 * 1024L
            )

            multipart.forEachPart { part ->
                try {
                    when (part) {
                        is PartData.FormItem -> {
                            when (part.name) {
                                "documentType" -> {
                                    if (documentType != null) {
                                        throw IllegalArgumentException(
                                            "documentType must appear only once"
                                        )
                                    }
                                    documentType = part.value.trim()
                                }

                                "consultationId" -> {
                                    if (consultationId != null) {
                                        throw IllegalArgumentException(
                                            "consultationId must appear only once"
                                        )
                                    }
                                    consultationId = part.value.toLongOrNull()
                                        ?: throw IllegalArgumentException(
                                            "consultationId must be a valid positive number"
                                        )
                                    if (consultationId!! <= 0) {
                                        throw IllegalArgumentException(
                                            "consultationId must be positive"
                                        )
                                    }
                                }

                                else -> throw IllegalArgumentException(
                                    "Unexpected form field"
                                )
                            }
                        }

                        is PartData.FileItem -> {
                            if (fileBytes != null) {
                                throw IllegalArgumentException(
                                    "Upload exactly one file per request"
                                )
                            }

                            originalFilename = part.originalFileName
                            contentType = part.contentType?.toString()

                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            val channel = part.provider()
                            var totalBytes = 0L

                            while (true) {
                                val count = channel.readAvailable(
                                    buffer,
                                    0,
                                    buffer.size
                                )

                                if (count == -1) break
                                if (count == 0) continue

                                totalBytes += count

                                if (totalBytes >
                                    MedicalDocumentStorage.MAX_FILE_SIZE_BYTES
                                ) {
                                    throw IllegalArgumentException(
                                        "File size must not exceed 10 MB"
                                    )
                                }

                                output.write(buffer, 0, count)
                            }

                            fileBytes = output.toByteArray()
                        }

                        else -> throw IllegalArgumentException(
                            "Unsupported multipart field"
                        )
                    }
                } finally {
                    part.dispose()
                }
            }

            val finalDocumentType = documentType
                ?: throw IllegalArgumentException("documentType is required")

            if (finalDocumentType !in allowedTypes) {
                throw IllegalArgumentException(
                    "Invalid documentType. Allowed values: ${allowedTypes.joinToString()}"
                )
            }

            val finalFilename = originalFilename
                ?: throw IllegalArgumentException("A file is required")

            val finalContentType = contentType
                ?: throw IllegalArgumentException("File content type is required")

            val finalBytes = fileBytes
                ?: throw IllegalArgumentException("A file is required")

            val record = MedicalDocumentService().uploadDocument(
                uploaderUserId = userId,
                petId = petId,
                consultationId = consultationId,
                documentType = finalDocumentType,
                originalFilename = finalFilename,
                contentType = finalContentType,
                bytes = finalBytes
            )

            call.respond(
                HttpStatusCode.Created,
                MedicalDocumentResponse(
                    id = record.id,
                    petId = record.petId,
                    consultationId = record.consultationId,
                    uploadedBy = record.uploadedBy,
                    documentType = record.documentType,
                    originalFilename = record.originalFilename,
                    mimeType = record.mimeType,
                    fileSizeBytes = record.fileSizeBytes,
                    processingStatus = record.processingStatus,
                    uploadedAt = record.uploadedAt.toString()
                )
            )
        } catch (exception: IllegalArgumentException) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (exception.message ?: "Invalid upload"))
            )
        } catch (exception: SecurityException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("error" to "You are not authorized to upload a document for this pet")
            )
        } catch (exception: Exception) {
            application.log.error("Medical document upload failed", exception)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Unable to upload medical document")
            )
        }
    }

    get("/api/pets/{id}/documents") {
        val userId = call.principal<JWTPrincipal>()
            ?.payload
            ?.subject
            ?.toLongOrNull()

        if (userId == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("error" to "Invalid authentication token")
            )
            return@get
        }

        val petId = call.parameters["id"]?.toLongOrNull()

        if (petId == null || petId <= 0) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "A valid pet ID is required")
            )
            return@get
        }

        try {
            if (UserRepository.findRoleById(userId) != "CUSTOMER") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Only customers can view pet documents")
                )
                return@get
            }

            val documents = MedicalDocumentService()
                .getDocumentsForPetOwner(userId, petId)

            call.respond(
                HttpStatusCode.OK,
                MedicalDocumentListResponse(
                    documents = documents.map { document ->
                        MedicalDocumentResponse(
                            id = document.id,
                            petId = document.petId,
                            consultationId = document.consultationId,
                            uploadedBy = document.uploadedBy,
                            documentType = document.documentType,
                            originalFilename = document.originalFilename,
                            mimeType = document.mimeType,
                            fileSizeBytes = document.fileSizeBytes,
                            processingStatus = document.processingStatus,
                            uploadedAt = document.uploadedAt.toString()
                        )
                    }
                )
            )
        } catch (exception: SecurityException) {
            call.respond(
                HttpStatusCode.Forbidden,
                mapOf("error" to "You are not authorized to view these documents")
            )
        } catch (exception: Exception) {
            application.log.error(
                "Failed to retrieve pet medical documents",
                exception
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Unable to retrieve medical documents")
            )
        }
    }


    get("/api/documents/{id}/download") {
        val userId = call.principal<JWTPrincipal>()
            ?.payload
            ?.subject
            ?.toLongOrNull()

        if (userId == null) {
            call.respond(
                HttpStatusCode.Unauthorized,
                mapOf("error" to "Invalid authentication token")
            )
            return@get
        }

        val documentId = call.parameters["id"]?.toLongOrNull()

        if (documentId == null || documentId <= 0) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "A valid document ID is required")
            )
            return@get
        }

        try {
            if (UserRepository.findRoleById(userId) != "CUSTOMER") {
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Only customers can download pet documents")
                )
                return@get
            }

            val result = MedicalDocumentService()
                .downloadDocumentForPetOwner(
                    ownerUserId = userId,
                    documentId = documentId
                )

            if (result == null) {
                // Use the same response for missing and unauthorized documents.
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "Document not found")
                )
                return@get
            }

            val (document, bytes) = result

            if (bytes.isEmpty()) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Stored medical document is empty")
                )
                return@get
            }

            call.response.header(
                io.ktor.http.HttpHeaders.ContentDisposition,
                io.ktor.http.ContentDisposition.Attachment
                    .withParameter(
                        io.ktor.http.ContentDisposition.Parameters.FileName,
                        document.originalFilename
                            .substringAfterLast('/')
                            .substringAfterLast('\\')
                            .replace("\"", "")
                            .replace("\r", "")
                            .replace("\n", "")
                    )
                    .toString()
            )

            call.respondBytes(
                bytes = bytes,
                contentType = io.ktor.http.ContentType.parse(document.mimeType),
                status = HttpStatusCode.OK
            )
        } catch (exception: Exception) {
            application.log.error(
                "Medical document download failed",
                exception
            )

            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Unable to download medical document")
            )
        }
    }


}
