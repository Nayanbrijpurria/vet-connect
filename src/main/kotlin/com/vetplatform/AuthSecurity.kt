package com.vetplatform

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import java.net.URI

object AuthSecurity {
    const val REFRESH_COOKIE = "vetconnect_refresh"
    const val CSRF_HEADER = "X-VetConnect-CSRF"
    const val CSRF_VALUE = "1"
    const val REFRESH_PATH = "/api/auth"
    const val CSRF_MESSAGE = "Request origin or CSRF validation failed"

    const val FRONTEND_ORIGINS_ENV = "CORS_ALLOWED_ORIGINS"

    private val defaults = setOf(
        "http://localhost:63342",
        "http://127.0.0.1:63342",
        "http://localhost:5500",
        "http://127.0.0.1:5500",
        "https://vet-connect-frontend-reemltg48.vercel.app"
    )

    val allowedOrigins: Set<String>
        get() = System.getenv(FRONTEND_ORIGINS_ENV)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toSet()
            ?.also { origins ->
                require(origins.isNotEmpty() && origins.none { it == "*" }) {
                    "$FRONTEND_ORIGINS_ENV must contain explicit origins"
                }
                origins.forEach { origin ->
                    val uri = URI(origin)
                    require(uri.isAbsolute && uri.host != null && uri.path.isNullOrEmpty() &&
                        uri.query == null && uri.fragment == null && uri.userInfo == null &&
                        uri.scheme in setOf("http", "https")) {
                        "$FRONTEND_ORIGINS_ENV contains an invalid origin"
                    }
                }
            } ?: defaults

    val secureCookies: Boolean
        get() = !System.getenv("APP_ENV").equals("development", ignoreCase = true)

    val cookieSameSite: String
        get() = if (secureCookies) "None" else "Lax"

    fun isTrustedOrigin(origin: String?): Boolean =
        origin != null && origin in allowedOrigins

    fun isValidCookieRequest(call: ApplicationCall): Boolean {
        val origin = call.request.headers[HttpHeaders.Origin]
        val csrf = call.request.headers[CSRF_HEADER]
        return isTrustedOrigin(origin) && csrf == CSRF_VALUE
    }

    fun appendRefreshCookie(call: ApplicationCall, token: String) {
        call.response.cookies.append(
            name = REFRESH_COOKIE,
            value = token,
            maxAge = JwtService.REFRESH_TOKEN_EXPIRY_SECONDS,
            path = REFRESH_PATH,
            secure = secureCookies,
            httpOnly = true,
            extensions = mapOf("SameSite" to cookieSameSite)
        )
    }

    fun clearRefreshCookie(call: ApplicationCall) {
        call.response.cookies.append(
            name = REFRESH_COOKIE,
            value = "",
            maxAge = 0,
            path = REFRESH_PATH,
            secure = secureCookies,
            httpOnly = true,
            extensions = mapOf("SameSite" to cookieSameSite)
        )
    }
}
