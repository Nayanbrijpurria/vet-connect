(function () {
    "use strict";

    const signInButton = document.getElementById("google-signin-button");
    const linkButton = document.getElementById("google-link-button");
    const isSignInPage = Boolean(signInButton);
    const isLinkPage = Boolean(linkButton);
    if (!isSignInPage && !isLinkPage) return;

    const message = document.getElementById(
        isSignInPage ? "google-auth-message" : "google-link-message"
    );
    const googleSection = document.getElementById(
        isSignInPage ? "google-signin-section" : "google-link-section"
    );
    let requestInProgress = false;

    function showMessage(text, isError = true) {
        if (!message) return;
        message.textContent = text;
        message.classList.remove("hidden");
        message.classList.toggle("form-error", isError);
        message.classList.toggle("google-auth-success", !isError);
    }

    function loadGoogleIdentityServices() {
        return new Promise((resolve, reject) => {
            if (window.google?.accounts?.id) {
                resolve();
                return;
            }

            const script = document.createElement("script");
            script.src = "https://accounts.google.com/gsi/client";
            script.async = true;
            script.defer = true;
            script.onload = resolve;
            script.onerror = () => reject(new Error("Google Sign-In could not be loaded."));
            document.head.appendChild(script);
        });
    }

    function saveCustomerSession(data) {
        if (!data.token || data.role !== "CUSTOMER") {
            throw new Error("Google Sign-In is only available for customer accounts.");
        }
        window.vetApi.setAccessToken(data.token);
        sessionStorage.setItem("vetplatform_role", data.role);
        sessionStorage.setItem("vetplatform_email", data.email || "");
        sessionStorage.setItem("vetplatform_user_id", data.userId ?? "");
        window.location.assign("/pages/dashboard.html");
    }

    async function handleCredentialResponse(response) {
        if (requestInProgress || !response?.credential) return;
        requestInProgress = true;
        if (message) message.classList.add("hidden");

        let currentPassword = "";
        try {
            let endpoint = "/api/auth/google";
            const body = { credential: response.credential };
            const headers = {
                "Content-Type": "application/json",
                "X-VetConnect-CSRF": "1"
            };

            if (isLinkPage) {
                currentPassword = document.getElementById("google-link-password")?.value || "";
                endpoint = "/api/auth/google/link";
                body.currentPassword = currentPassword;
                headers.Authorization = `Bearer ${window.vetApi.getAccessToken() || ""}`;
            }

            const result = await window.vetApi.fetch(`${API_BASE_URL}${endpoint}`, {
                method: "POST",
                headers,
                body: JSON.stringify(body)
            });
            const data = await result.json().catch(() => ({}));
            if (!result.ok) {
                throw new Error(data.message || "Google Sign-In failed. Please try again.");
            }

            if (isSignInPage) {
                saveCustomerSession(data);
            } else {
                showMessage(data.message || "Google account linked.", false);
            }
        } catch (error) {
            const text = error instanceof TypeError
                ? "Could not reach VetConnect. Check your connection and try again."
                : (error.message || "Google Sign-In failed. Please try again.");
            showMessage(text);
        } finally {
            if (isLinkPage) {
                const passwordInput = document.getElementById("google-link-password");
                if (passwordInput) passwordInput.value = "";
            }
            requestInProgress = false;
        }
    }

    async function initialize() {
        try {
            if (isLinkPage) {
                const restored = await window.vetApi.ensureSession();
                if (!restored || sessionStorage.getItem("vetplatform_role") !== "CUSTOMER") return;
            }

            const configResponse = await window.vetApi.fetch(
                `${API_BASE_URL}/api/auth/google/config`
            );
            const config = await configResponse.json().catch(() => ({}));
            if (!configResponse.ok || !config.clientId) return;

            await loadGoogleIdentityServices();
            const container = isSignInPage ? signInButton : linkButton;
            window.google.accounts.id.initialize({
                client_id: config.clientId,
                callback: handleCredentialResponse,
                auto_select: false,
                context: isSignInPage ? "signin" : "use"
            });
            window.google.accounts.id.renderButton(container, {
                theme: "outline",
                size: "large",
                text: "continue_with",
                shape: "rectangular",
                logo_alignment: "left",
                width: Math.min(400, Math.max(240, container.clientWidth || 320))
            });
            googleSection?.classList.remove("hidden");
        } catch (_) {
            // Password login and the rest of the dashboard remain available if GIS is down.
            googleSection?.classList.remove("hidden");
            showMessage("Google Sign-In is temporarily unavailable.");
        }
    }

    initialize();
})();
