
document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("customer-registration-form");
    const message = document.getElementById("registration-message");
    const submitButton = form.querySelector('button[type="submit"]');

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        message.textContent = "";
        message.style.color = "";

        const fullName = document.getElementById("fullName").value.trim();
        const email = document.getElementById("email").value.trim();
        const phone = document.getElementById("phone").value.trim();
        const password = document.getElementById("password").value;
        const confirmPassword =
            document.getElementById("confirmPassword").value;

        // Check that both password fields match.
        if (password !== confirmPassword) {
            message.style.color = "#b91c1c";
            message.textContent = "The passwords do not match.";
            return;
        }

        // Match the password limits enforced by the backend.
        if (password.length < 8 || password.length > 72) {
            message.style.color = "#b91c1c";
            message.textContent =
                "Password must be between 8 and 72 characters.";
            return;
        }

        const registrationData = {
            fullName: fullName,
            email: email,
            password: password,
            phone: phone || null
        };

        submitButton.disabled = true;
        submitButton.textContent = "Creating account...";

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/auth/register`,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(registrationData)
                }
            );

            const data = await response.json();

            if (!response.ok) {
                throw new Error(
                    data.message || data.error ||
                    "Unable to create your account."
                );
            }

            message.style.color = "#15803d";
            message.textContent =
                "Account created successfully! Redirecting you to login...";

            form.reset();

            // Registration does not automatically log the user in.
            setTimeout(() => {
                window.location.href = "login.html";
            }, 1800);

        } catch (error) {
            message.style.color = "#b91c1c";

            if (error instanceof TypeError) {
                message.textContent =
                    "Could not connect to the server. Please try again later.";
            } else {
                message.textContent = error.message;
            }

            submitButton.disabled = false;
            submitButton.textContent = "Create account →";
        }
    });
});
