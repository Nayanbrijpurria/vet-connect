
document.addEventListener("DOMContentLoaded", function () {
    const token = sessionStorage.getItem("vetplatform_token");
    const role = sessionStorage.getItem("vetplatform_role");
    const email = sessionStorage.getItem("vetplatform_email");

    const messageElement = document.getElementById("vet-applications-message");
    const listElement = document.getElementById("vet-applications-list");
    const logoutButton = document.getElementById("admin-logout");

    // Frontend navigation check only. The backend must enforce authorization.
    if (!token || role !== "ADMIN") {
        window.location.replace("login.html");
        return;
    }

    const emailElement = document.getElementById("admin-email");
    if (emailElement) {
        emailElement.textContent = email || "Admin account";
    }

    if (logoutButton) {
        logoutButton.addEventListener("click", function () {
            sessionStorage.removeItem("vetplatform_token");
            sessionStorage.removeItem("vetplatform_role");
            sessionStorage.removeItem("vetplatform_email");
            sessionStorage.removeItem("vetplatform_user_id");

            window.location.replace("login.html");
        });
    }

    function showMessage(message) {
        if (messageElement) {
            messageElement.textContent = message;
        }
    }

    function addDetail(card, label, value) {
        const paragraph = document.createElement("p");
        const strong = document.createElement("strong");

        strong.textContent = label + ": ";
        paragraph.appendChild(strong);
        paragraph.appendChild(
            document.createTextNode(
                value === null || value === undefined || value === ""
                    ? "Not provided"
                    : String(value)
            )
        );

        card.appendChild(paragraph);
    }

    function getValue(application, possibleKeys) {
        for (const key of possibleKeys) {
            if (
                application[key] !== undefined &&
                application[key] !== null &&
                application[key] !== ""
            ) {
                return application[key];
            }
        }

        return "";
    }

    async function loadPendingApplications() {
        if (!messageElement || !listElement) {
            console.error("The veterinarian applications HTML elements are missing.");
            return;
        }

        listElement.replaceChildren();
        showMessage("Loading pending veterinarian applications...");

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/admin/vets/pending`,
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
                    data.message || "Unable to load veterinarian applications."
                );
            }

            // The backend returns PendingVetsListResponse(applications).
            const applications = Array.isArray(data.applications)
                ? data.applications
                : [];

            if (applications.length === 0) {
                showMessage("There are currently no pending veterinarian applications.");
                return;
            }

            showMessage(
                `${applications.length} pending veterinarian application(s).`
            );

            applications.forEach(function (application) {
                const card = document.createElement("article");
                card.className = "vet-application-card";

                const name = getValue(application, [
                    "fullName", "name", "vetName"
                ]);
                const userId = getValue(application, [
                    "userId", "id"
                ]);

                const heading = document.createElement("h3");
                heading.textContent = name || "Veterinarian application";
                card.appendChild(heading);

                addDetail(card, "User ID", userId);
                addDetail(card, "Email", getValue(application, ["email"]));
                addDetail(card, "Phone", getValue(application, ["phone"]));
                addDetail(
                    card,
                    "Qualification",
                    getValue(application, ["qualification"])
                );
                addDetail(
                    card,
                    "Registration number",
                    getValue(application, [
                        "registrationNumber", "registrationNo"
                    ])
                );
                addDetail(
                    card,
                    "Experience (years)",
                    getValue(application, ["experienceYears"])
                );
                addDetail(
                    card,
                    "Consultation fee",
                    getValue(application, ["consultationFee"])
                );

                const actions = document.createElement("div");
                actions.className = "vet-application-actions";

                const approveButton = document.createElement("button");
                approveButton.type = "button";
                approveButton.textContent = "Approve";
                approveButton.disabled = userId === "";

                approveButton.addEventListener("click", async function () {
                    if (!window.confirm(
                        `Approve the veterinarian application for ${name || userId}?`
                    )) {
                        return;
                    }

                    await reviewApplication(userId, "approve");
                });

                const rejectButton = document.createElement("button");
                rejectButton.type = "button";
                rejectButton.textContent = "Reject";
                rejectButton.disabled = userId === "";

                rejectButton.addEventListener("click", async function () {
                    const reason = window.prompt(
                        "Enter the reason for rejecting this application (required):"
                    );

                    if (reason === null) {
                        return;
                    }

                    const rejectionReason = reason.trim();

                    if (!rejectionReason) {
                        window.alert("Please enter a rejection reason.");
                        return;
                    }

                    if (rejectionReason.length > 500) {
                        window.alert("The rejection reason must be 500 characters or fewer.");
                        return;
                    }

                    await reviewApplication(
                        userId,
                        "reject",
                        rejectionReason
                    );
                });

                actions.appendChild(approveButton);
                actions.appendChild(rejectButton);
                card.appendChild(actions);
                listElement.appendChild(card);
            });
        } catch (error) {
            console.error("Pending applications error:", error);
            showMessage(
                "Unable to load applications. The backend may be unavailable, or your administrator session may need attention."
            );
        }
    }

    async function reviewApplication(userId, action, rejectionReason) {
        if (userId === "" || userId === null || userId === undefined) {
            window.alert("This application has no user ID, so it cannot be reviewed.");
            return;
        }

        const url =
            `${API_BASE_URL}/api/admin/vets/${encodeURIComponent(userId)}/${action}`;

        try {
            const options = {
                method: "PUT",
                headers: {
                    "Authorization": `Bearer ${token}`,
                    "Accept": "application/json"
                }
            };

            if (action === "reject") {
                options.headers["Content-Type"] = "application/json";
                options.body = JSON.stringify({
                    rejectionReason: rejectionReason
                });
            }

            const response = await fetch(url, options);
            const data = await response.json();

            if (!response.ok) {
                throw new Error(
                    data.message || `Unable to ${action} this application.`
                );
            }

            window.alert(
                data.message ||
                `The veterinarian application was ${action === "approve" ? "approved" : "rejected"}.`
            );

            await loadPendingApplications();
        } catch (error) {
            console.error("Application review error:", error);
            window.alert(
                error.message || "The application could not be reviewed."
            );
        }
    }

    // Prepare the page and load applications when the dashboard opens.
    loadPendingApplications();
});
