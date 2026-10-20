
document.addEventListener("DOMContentLoaded", function () {
    const email = sessionStorage.getItem("vetplatform_email");
    const role = sessionStorage.getItem("vetplatform_role");
    const token = sessionStorage.getItem("vetplatform_token");

    // This is a basic frontend navigation check, not backend security.
    if (!token || role !== "VET") {
        window.location.replace("login.html");
        return;
    }

    const emailDisplay = email || "Veterinarian";

    document.getElementById("vet-email").textContent = emailDisplay;
    document.getElementById("vet-profile-email").textContent = emailDisplay;


    // Load the veterinarian's real verification status from the backend.
    async function loadVerificationStatus() {
        const title = document.getElementById("vet-verification-title");
        const description = document.getElementById("vet-verification-description");
        const shortStatus = document.getElementById("vet-verification-short");

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/vets/verification-status`,
                {
                    method: "GET",
                    headers: {
                        "Authorization": `Bearer ${token}`,
                        "Accept": "application/json"
                    }
                }
            );

            const data = await response.json();

            if (!response.ok) {
                throw new Error(
                    data.message || "Could not retrieve verification status."
                );
            }

            // Display the status returned by the backend.
            const status = String(
                data.verificationStatus || data.status || ""
            ).toUpperCase();

            if (!status) {
                title.textContent = "Status unavailable";
                description.textContent =
                    "The server did not return a recognised verification status.";
                shortStatus.textContent = "Unavailable";
                return;
            }

            const readableStatus = status.replaceAll("_", " ");

            title.textContent = readableStatus;
            shortStatus.textContent = readableStatus;

            if (status === "APPROVED" || status === "VERIFIED") {
                description.textContent =
                    "Your verification has been approved by the platform.";
            } else if (status === "PENDING") {
                description.textContent =
                    "Your application is awaiting review by the platform administrator.";
            } else if (status === "REJECTED") {
                description.textContent =
                    "Your application was rejected. Please review the information provided by the platform.";
            } else {
                description.textContent =
                    "This is the verification status returned by the server.";
            }

        } catch (error) {
            title.textContent = "Unable to load";
            shortStatus.textContent = "Unavailable";
            description.textContent =
                "Verification status could not be retrieved. Please try again when the backend is available.";
        }
    }

    loadVerificationStatus();


    document.getElementById("vet-logout").addEventListener("click", function () {
        sessionStorage.removeItem("vetplatform_token");
        sessionStorage.removeItem("vetplatform_role");
        sessionStorage.removeItem("vetplatform_email");
        sessionStorage.removeItem("vetplatform_user_id");

        window.location.replace("login.html");
    });
});
