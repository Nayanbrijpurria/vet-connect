package com.vetplatform

fun main() {
    print("Enter new admin password: ")

    val password = readlnOrNull()
        ?: error("Password input was not received.")

    require(password.length >= 12) {
        "Password must contain at least 12 characters."
    }

    val passwordHash = PasswordService.hashPassword(password)

    println("\nBCrypt hash:")
    println(passwordHash)
}