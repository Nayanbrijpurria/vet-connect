
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
