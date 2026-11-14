
const loginForm = document.getElementById("login-form");
const loginMessage = document.getElementById("login-message");
const authNotice = sessionStorage.getItem("vetplatform_auth_notice");

if (authNotice && loginMessage) {
    loginMessage.textContent = authNotice;
    loginMessage.classList.remove("hidden");
    sessionStorage.removeItem("vetplatform_auth_notice");
}

if (loginForm) {
    loginForm.addEventListener("submit", async function (event) {
        event.preventDefault();

        const email = document.getElementById("email").value.trim();
        const password = document.getElementById("password").value;
        const message = document.getElementById("login-message");
        const submitButton = loginForm.querySelector('button[type="submit"]');

        message.textContent = "";
        message.classList.add("hidden");
        submitButton.disabled = true;
        submitButton.textContent = "Signing in...";

        try {
            const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    email: email,
                    password: password
                })
            });

            const data = await response.json();

            if (!response.ok) {
                throw new Error(data.message || "Login failed. Please check your details.");
            }

            if (!data.token || !data.role) {
                throw new Error("The server response is missing login information.");
            }

            // Keep the access token in memory; the refresh token stays in its HttpOnly cookie.
            window.vetApi.setAccessToken(data.token);
            sessionStorage.setItem("vetplatform_role", data.role);
            sessionStorage.setItem("vetplatform_email", data.email || email);
            sessionStorage.setItem("vetplatform_user_id", data.userId ?? "");

            // Temporary destination until separate role dashboards are ready.
            if (data.role === "CUSTOMER") {
                window.location.href = "/pages/dashboard.html";
            } else if (data.role === "VET") {
                window.location.href = "/pages/vet-dashboard.html";
            } else if (data.role === "ADMIN") {
                window.location.href = "/pages/admin-dashboard.html";
            } else {
                throw new Error("Your account has an unrecognized role.");
            }

        } catch (error) {
            message.textContent =
                error instanceof TypeError
                    ? "The login request was blocked or the authentication server is unreachable. Verify the Railway backend deployment and that CORS allows this site's exact origin."
                    : error.message;

            message.classList.remove("hidden");
        } finally {
            submitButton.disabled = false;
            submitButton.textContent = "Sign in →";
        }
    });
}
