package com.vetplatform
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}



//TO RUN THE BACKEND.

//Rebuild the backend
//Open a new PowerShell terminal in IntelliJ and run:

//cd "C:\Users\ASUS\Downloads\vetplatform"
//.\gradlew.bat build

//Wait for the build to finish.
//- If it says BUILD SUCCESSFUL, continue to Step 3.

//Step 2 — Restart the backend
//Stop the currently running backend using Ctrl + C in the terminal where it is running.
//Then start it again with:
//$env:DB_URL = "jdbc:postgresql://localhost:5433/vet_platform"
//$env:DB_USER = Read-Host "Enter your PostgreSQL username"
//$securePassword = Read-Host "Enter your PostgreSQL password" -AsSecureString
//$env:DB_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password
//
//.\gradlew.bat run

