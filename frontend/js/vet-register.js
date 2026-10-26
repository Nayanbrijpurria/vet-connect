
document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("vet-registration-form");
    const message = document.getElementById("vet-registration-message");
    const submitButton = form.querySelector('button[type="submit"]');

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        message.textContent = "";
        message.style.color = "";

        const fullName = document.getElementById("fullName").value.trim();
        const email = document.getElementById("email").value.trim();
        const phone = document.getElementById("phone").value.trim();
        const qualification =
            document.getElementById("qualification").value.trim();
        const registrationNumber =
            document.getElementById("registrationNumber").value.trim();

        const experienceText =
            document.getElementById("experienceYears").value;
        const feeText =
            document.getElementById("consultationFee").value;

        const password = document.getElementById("password").value;
        const confirmPassword =
            document.getElementById("confirmPassword").value;

        if (password !== confirmPassword) {
            message.style.color = "#b91c1c";
            message.textContent = "The passwords do not match.";
            return;
        }

        if (password.length < 8 || password.length > 72) {
            message.style.color = "#b91c1c";
            message.textContent =
                "Password must be between 8 and 72 characters.";
            return;
        }

        const experienceYears = Number(experienceText);
        const consultationFee = Number(feeText);

        if (
            experienceText.trim() === "" ||
            !Number.isInteger(experienceYears) ||
            experienceYears < 0 ||
            experienceYears > 70
        ) {
            message.style.color = "#b91c1c";
            message.textContent =
                "Experience must be a whole number between 0 and 70.";
            return;
        }

        if (
            feeText.trim() === "" ||
            !Number.isFinite(consultationFee) ||
            consultationFee < 0 ||
            consultationFee > 1000000
        ) {
            message.style.color = "#b91c1c";
            message.textContent =
                "Enter a consultation fee between 0 and 1,000,000.";
            return;
        }

        const registrationData = {
            fullName: fullName,
            email: email,
            password: password,
            phone: phone || null,
            qualification: qualification,
            registrationNumber: registrationNumber,
            experienceYears: experienceYears,
            consultationFee: consultationFee
        };

        submitButton.disabled = true;
        submitButton.textContent = "Submitting application...";

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/vets/register`,
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
                    data.message ||
                    data.error ||
                    "Unable to submit your application."
                );
            }

            message.style.color = "#15803d";
            message.textContent =
                data.message ||
                "Application submitted. Your verification is pending.";

            form.reset();

            // The veterinarian can log in after registration to
            // check their account status. Approval is still required.
            setTimeout(() => {
                window.location.href = "login.html";
            }, 3000);

        } catch (error) {
            message.style.color = "#b91c1c";

            if (error instanceof TypeError) {
                message.textContent =
                    "Could not connect to the server. Please try again later.";
            } else {
                message.textContent = error.message;
            }

            submitButton.disabled = false;
            submitButton.textContent = "Submit application →";
        }
    });
});
