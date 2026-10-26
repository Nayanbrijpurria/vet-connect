//CREATE A NEW ADMIN.
//in powershell

//STEP 0 (Change the admin name in the code)
//right now it is nayanbrijpuria123@gmail.com.


//STEP 1 (CONNECT DATABASE)
//$env:DB_URL = "jdbc:postgresql://localhost:5433/vet_platform"
//$env:DB_USER = Read-Host "PostgreSQL username"
//$securePassword = Read-Host "PostgreSQL password" -AsSecureString
//$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password

//STEP 2 (Enter new admin password)
//$secureAdminPassword = Read-Host "New admin password (minimum 8 characters)" -AsSecureString
//$env:ADMIN_NEW_PASSWORD = [System.Net.NetworkCredential]::new("", $secureAdminPassword).Password

package com.vetplatform

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val fullName = "VetPlatform Test Administrator"
    val email = "nayanbrijpuria138@gmail.com"
    val password = System.getenv("ADMIN_NEW_PASSWORD")
        ?: error("ADMIN_NEW_PASSWORD environment variable is missing")

    require(password.length >= 8) {
        "Password must contain at least 8 characters."
    }

    try {
        val existingId = DatabaseFactory.withConnection { connection ->
            connection.prepareStatement(
                "SELECT id FROM users WHERE LOWER(email) = LOWER(?)"
            ).use { statement ->
                statement.setString(1, email)

                statement.executeQuery().use { result ->
                    if (result.next()) result.getLong("id") else null
                }
            }
        }

        if (existingId != null) {
            println("STOPPED: This email already exists. No account was changed.")
            println("Existing user ID: $existingId")
            return@runBlocking
        }

        val passwordHash = PasswordService.hashPassword(password)

        val created = UserRepository.createAdmin(
            fullName = fullName,
            email = email,
            passwordHash = passwordHash
        )

        println("Admin account created successfully.")
        println("User ID: ${created.id}")
        println("Name: ${created.fullName}")
        println("Email: ${created.email}")
        println("Role: ${created.role}")
    } finally {
        DatabaseFactory.close()
    }
}
