plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)

  }



group = "com"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Ktor server
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.core)
    implementation("io.ktor:ktor-server-core-jvm:3.6.0")
    implementation("io.ktor:ktor-server-cors-jvm:3.6.0")
    implementation(ktorLibs.server.netty)

    // Logging
    implementation(libs.logback.classic)

    // PostgreSQL database and connection pool
    implementation("com.zaxxer:HikariCP:6.3.3")
    implementation("org.postgresql:postgresql:42.7.7")

    // Database migrations
    implementation("org.flywaydb:flyway-core:11.13.1")
    implementation("org.flywaydb:flyway-database-postgresql:11.13.1")

    // Password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // JSON request and response serialization
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.6.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.6.0")

    // JWT authentication
    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.6.0")


    // Tests
    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}


tasks.register<JavaExec>("createAdmin") {
    group = "application"
    description = "Creates one local administrator account."

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.vetplatform.CreateAdminKt")
}
