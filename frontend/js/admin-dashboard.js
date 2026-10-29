document.addEventListener("DOMContentLoaded", function () {
    const token = sessionStorage.getItem("vetplatform_token");
    const role = sessionStorage.getItem("vetplatform_role");
    const messageElement = document.getElementById("vet-applications-message");
    const listElement = document.getElementById("vet-applications-list");
    const countElement = document.getElementById("pending-reviews-count");
    const logoutButton = document.getElementById("admin-logout");
    const refreshButton = document.getElementById("admin-refresh-applications");
    const accountSearchForm = document.getElementById("admin-account-search");
    const accountSearchInput = document.getElementById("account-search-query");
    const accountRoleFilter = document.getElementById("account-role-filter");
    const accountMessage = document.getElementById("admin-accounts-message");
    const accountList = document.getElementById("admin-accounts-list");

    if (!token || role !== "ADMIN") {
        window.location.replace("login.html");
        return;
    }

    const emailElement = document.getElementById("admin-email");
    if (emailElement) {
        emailElement.textContent = sessionStorage.getItem("vetplatform_email") || "Admin account";
    }

    logoutButton?.addEventListener("click", function () {
        ["vetplatform_token", "vetplatform_role", "vetplatform_email", "vetplatform_user_id"]
            .forEach((key) => sessionStorage.removeItem(key));
        window.location.replace("login.html");
    });
    refreshButton?.addEventListener("click", loadPendingApplications);
    accountSearchForm?.addEventListener("submit", (event) => {
        event.preventDefault();
        loadAccounts();
    });

    const documentLabels = {
        DEGREE_CERTIFICATE: "Degree certificate",
        REGISTRATION_CERTIFICATE: "Registration certificate",
        ADDITIONAL_CERTIFICATE: "Additional certificate"
    };

    function makeElement(tag, className, text) {
        const element = document.createElement(tag);
        if (className) element.className = className;
        if (text !== undefined) element.textContent = text;
        return element;
    }

    function addDetail(card, label, value) {
        const line = makeElement("p");
        const strong = makeElement("strong", "", `${label}: `);
        line.append(strong, document.createTextNode(
            value === null || value === undefined || value === "" ? "Not provided" : String(value)
        ));
        card.appendChild(line);
    }

    async function readJson(response) {
        let body = {};
        try { body = await response.json(); } catch (_) { /* empty response */ }
        if (!response.ok) throw new Error(body.message || "The request failed.");
        return body;
    }

    async function openCredential(vetUserId, credential) {
        const preview = window.open("about:blank", "_blank");
        if (!preview) {
            window.alert("Allow pop-ups to view credential documents.");
            return;
        }

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/admin/vets/${encodeURIComponent(vetUserId)}/credentials/${encodeURIComponent(credential.id)}/file`,
                { headers: { Authorization: `Bearer ${token}` } }
            );
            if (!response.ok) {
                const body = await readJson(response);
                throw new Error(body.message || "Unable to open this document.");
            }
            const blob = await response.blob();
            const objectUrl = URL.createObjectURL(blob);
            preview.location.replace(objectUrl);
            window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
        } catch (error) {
            preview.close();
            window.alert(error.message || "Unable to open this document.");
        }
    }

    async function reviewCredential(vetUserId, credential, approved, card, actionButtons) {
        let adminNotes = null;
        if (!approved) {
            adminNotes = window.prompt("Enter why this credential is being rejected (required):");
            if (adminNotes === null) return;
            adminNotes = adminNotes.trim();
            if (!adminNotes || adminNotes.length > 500) {
                window.alert("Enter a rejection reason of 1 to 500 characters.");
                return;
            }
        }

        actionButtons.forEach((button) => { button.disabled = true; });
        try {
            const action = approved ? "approve" : "reject";
            const response = await fetch(
                `${API_BASE_URL}/api/admin/vets/${encodeURIComponent(vetUserId)}/credentials/${encodeURIComponent(credential.id)}/${action}`,
                {
                    method: "PUT",
                    headers: {
                        Authorization: `Bearer ${token}`,
                        Accept: "application/json",
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({ adminNotes })
                }
            );
            await readJson(response);
            await loadCredentials(vetUserId, card);
        } catch (error) {
            window.alert(error.message || "Unable to review this credential.");
            actionButtons.forEach((button) => { button.disabled = false; });
        }
    }

    async function loadCredentials(vetUserId, card) {
        const area = card.querySelector(".vet-credentials");
        area.replaceChildren(makeElement("p", "credential-loading", "Loading submitted credentials..."));

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/admin/vets/${encodeURIComponent(vetUserId)}/credentials`,
                { headers: { Authorization: `Bearer ${token}`, Accept: "application/json" } }
            );
            const credentials = await readJson(response);
            area.replaceChildren();

            if (!Array.isArray(credentials) || credentials.length === 0) {
                area.appendChild(makeElement("p", "credential-warning", "No credential documents have been uploaded."));
                updateApprovalButton(card, []);
                return;
            }

            credentials.forEach((credential) => {
                const credentialCard = makeElement("article", "credential-review-card");
                const heading = makeElement("h4", "", documentLabels[credential.documentType] || credential.documentType);
                credentialCard.appendChild(heading);
                addDetail(credentialCard, "File", credential.originalFileName);
                addDetail(credentialCard, "Status", credential.verificationStatus);
                addDetail(credentialCard, "Uploaded", new Date(credential.uploadedAt).toLocaleString());
                if (credential.adminNotes) addDetail(credentialCard, "Reviewer notes", credential.adminNotes);

                const actions = makeElement("div", "vet-application-actions");
                const viewButton = makeElement("button", "credential-view-button", "View document");
                viewButton.type = "button";
                viewButton.addEventListener("click", () => openCredential(vetUserId, credential));
                actions.appendChild(viewButton);

                const decisionButtons = [];
                if (credential.verificationStatus === "PENDING") {
                    const approveButton = makeElement("button", "", "Approve document");
                    approveButton.type = "button";
                    const rejectButton = makeElement("button", "", "Reject document");
                    rejectButton.type = "button";
                    decisionButtons.push(approveButton, rejectButton);
                    approveButton.addEventListener("click", () =>
                        reviewCredential(vetUserId, credential, true, card, decisionButtons)
                    );
                    rejectButton.addEventListener("click", () =>
                        reviewCredential(vetUserId, credential, false, card, decisionButtons)
                    );
                    actions.append(approveButton, rejectButton);
                }

                credentialCard.appendChild(actions);
                area.appendChild(credentialCard);
            });
            updateApprovalButton(card, credentials);
        } catch (error) {
            area.replaceChildren(makeElement("p", "credential-warning", error.message || "Unable to load credentials."));
            updateApprovalButton(card, []);
        }
    }

    function updateApprovalButton(card, credentials) {
        const button = card.querySelector(".approve-vet-button");
        if (!button) return;
        const degreeApproved = credentials.some((item) =>
            item.documentType === "DEGREE_CERTIFICATE" && item.verificationStatus === "APPROVED"
        );
        const registrationApproved = credentials.some((item) =>
            item.documentType === "REGISTRATION_CERTIFICATE" && item.verificationStatus === "APPROVED"
        );
        button.disabled = !(degreeApproved && registrationApproved);
        button.title = button.disabled
            ? "Approve the degree and registration certificates first."
            : "Approve this veterinarian application.";
    }

    async function reviewApplication(userId, approved, card) {
        const action = approved ? "approve" : "reject";
        let rejectionReason = null;
        if (!approved) {
            rejectionReason = window.prompt("Enter the reason for rejecting this application (required):");
            if (rejectionReason === null) return;
            rejectionReason = rejectionReason.trim();
            if (!rejectionReason || rejectionReason.length > 500) {
                window.alert("Enter a rejection reason of 1 to 500 characters.");
                return;
            }
        }

        const buttons = card.querySelectorAll(".vet-application-actions button");
        buttons.forEach((button) => { button.disabled = true; });
        try {
            const response = await fetch(
                `${API_BASE_URL}/api/admin/vets/${encodeURIComponent(userId)}/${action}`,
                {
                    method: "PUT",
                    headers: {
                        Authorization: `Bearer ${token}`,
                        Accept: "application/json",
                        ...(approved ? {} : { "Content-Type": "application/json" })
                    },
                    ...(approved ? {} : { body: JSON.stringify({ rejectionReason }) })
                }
            );
            await readJson(response);
            await loadPendingApplications();
        } catch (error) {
            window.alert(error.message || "Unable to review this application.");
            buttons.forEach((button) => { button.disabled = false; });
        }
    }

    async function loadPendingApplications() {
        listElement.replaceChildren();
        messageElement.textContent = "Loading veterinarian applications...";
        refreshButton.disabled = true;
        try {
            const response = await fetch(`${API_BASE_URL}/api/admin/vets/pending`, {
                headers: { Authorization: `Bearer ${token}`, Accept: "application/json" }
            });
            const body = await readJson(response);
            const applications = Array.isArray(body.applications) ? body.applications : [];
            countElement.textContent = String(applications.length);
            if (applications.length === 0) {
                messageElement.textContent = "There are no pending veterinarian applications.";
                return;
            }

            messageElement.textContent = `${applications.length} application(s) need review.`;
            applications.forEach((application) => {
                const card = makeElement("article", "vet-application-card");
                card.dataset.userId = application.userId;
                card.appendChild(makeElement("h3", "", application.fullName || "Veterinarian application"));
                if (application.submissionNumber > 1) {
                    card.appendChild(makeElement(
                        "p",
                        "vet-reapplication-label",
                        `Resubmitted application #${application.submissionNumber}`
                    ));
                    if (application.submissionSummary) {
                        addDetail(card, "What changed", application.submissionSummary);
                    }
                }
                addDetail(card, "Email", application.email);
                addDetail(card, "Phone", application.phone);
                addDetail(card, "Qualification", application.qualification);
                addDetail(card, "Registration number", application.registrationNumber);
                addDetail(card, "Experience", `${application.experienceYears} years`);
                addDetail(card, "Consultation fee", application.consultationFee);

                const credentialSection = makeElement("section", "credential-review-section");
                credentialSection.appendChild(makeElement("h4", "", "Submitted credentials"));
                const credentialsArea = makeElement("div", "vet-credentials");
                credentialSection.appendChild(credentialsArea);
                card.appendChild(credentialSection);

                const actions = makeElement("div", "vet-application-actions");
                const approveButton = makeElement("button", "approve-vet-button", "Approve veterinarian");
                approveButton.type = "button";
                approveButton.disabled = true;
                approveButton.addEventListener("click", () => {
                    if (window.confirm(`Approve ${application.fullName || "this veterinarian"}?`)) {
                        reviewApplication(application.userId, true, card);
                    }
                });
                const rejectButton = makeElement("button", "", "Reject application");
                rejectButton.type = "button";
                rejectButton.addEventListener("click", () => reviewApplication(application.userId, false, card));
                actions.append(approveButton, rejectButton);
                card.appendChild(actions);
                listElement.appendChild(card);
                loadCredentials(application.userId, card);
            });
        } catch (error) {
            countElement.textContent = "—";
            messageElement.textContent = error.message || "Unable to load veterinarian applications.";
        } finally {
            refreshButton.disabled = false;
        }
    }

    async function changeAccount(userId, action, promptText) {
        const reason = window.prompt(promptText);
        if (reason === null) return;
        const trimmedReason = reason.trim();
        if (!trimmedReason || trimmedReason.length > 500) {
            window.alert("Enter a reason of 1 to 500 characters.");
            return;
        }
        if (!window.confirm("Confirm this account change? Account and medical records will be retained.")) return;

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/admin/${action.startsWith("vet-") ? "vets" : "accounts"}/${encodeURIComponent(userId)}/${action.replace("vet-", "")}`,
                {
                    method: "PUT",
                    headers: {
                        Authorization: `Bearer ${token}`,
                        Accept: "application/json",
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({ reason: trimmedReason })
                }
            );
            await readJson(response);
            await loadAccounts();
        } catch (error) {
            window.alert(error.message || "Unable to change this account.");
        }
    }

    async function approveRejectedVet(userId, fullName) {
        if (!window.confirm(
            `Approve ${fullName || "this veterinarian"}? The required degree and registration documents must already be approved.`
        )) return;
        try {
            const response = await fetch(
                `${API_BASE_URL}/api/admin/vets/${encodeURIComponent(userId)}/approve`,
                {
                    method: "PUT",
                    headers: { Authorization: `Bearer ${token}`, Accept: "application/json" }
                }
            );
            await readJson(response);
            await loadAccounts();
        } catch (error) {
            window.alert(error.message || "Unable to approve this veterinarian.");
        }
    }

    async function loadAccounts() {
        accountList.replaceChildren();
        accountMessage.textContent = "Searching accounts...";
        const params = new URLSearchParams();
        const search = accountSearchInput.value.trim();
        const roleFilter = accountRoleFilter.value;
        if (search) params.set("search", search);
        if (roleFilter) params.set("role", roleFilter);

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/admin/accounts?${params.toString()}`,
                { headers: { Authorization: `Bearer ${token}`, Accept: "application/json" } }
            );
            const body = await readJson(response);
            const accounts = Array.isArray(body.accounts) ? body.accounts : [];
            if (accounts.length === 0) {
                accountMessage.textContent = "No matching customer or veterinarian accounts.";
                return;
            }

            accountMessage.textContent = accounts.length === 100
                ? "Showing the 100 most recently created matching accounts. Refine your search to narrow the results."
                : `${accounts.length} matching account(s).`;

            accounts.forEach((account) => {
                const card = makeElement("article", "admin-account-card");
                card.appendChild(makeElement("h3", "", account.fullName));
                addDetail(card, "Email", account.email);
                addDetail(card, "Role", account.role === "VET" ? "Veterinarian" : "Customer");
                addDetail(card, "Account access", account.accountStatus);
                if (account.role === "VET") {
                    addDetail(card, "Verification", account.verificationStatus || "No profile");
                }

                const actions = makeElement("div", "vet-application-actions");
                const accessButton = makeElement(
                    "button",
                    account.accountStatus === "ACTIVE" ? "account-disable-button" : "account-enable-button",
                    account.accountStatus === "ACTIVE" ? "Disable account" : "Restore account"
                );
                accessButton.type = "button";
                accessButton.addEventListener("click", () => changeAccount(
                    account.userId,
                    account.accountStatus === "ACTIVE" ? "disable" : "enable",
                    account.accountStatus === "ACTIVE"
                        ? "Why should this account be disabled?"
                        : "Why should access be restored?"
                ));
                actions.appendChild(accessButton);

                if (account.role === "VET" && ["APPROVED", "SUSPENDED"].includes(account.verificationStatus)) {
                    const isApproved = account.verificationStatus === "APPROVED";
                    const statusButton = makeElement(
                        "button",
                        isApproved ? "account-suspend-button" : "account-enable-button",
                        isApproved ? "Suspend veterinarian approval" : "Reinstate veterinarian"
                    );
                    statusButton.type = "button";
                    statusButton.addEventListener("click", () => changeAccount(
                        account.userId,
                        isApproved ? "vet-suspend" : "vet-reinstate",
                        isApproved
                            ? "Why is this veterinarian's approval being suspended?"
                            : "Why is this veterinarian being reinstated?"
                    ));
                    actions.appendChild(statusButton);
                }

                if (account.role === "VET" && account.verificationStatus === "REJECTED") {
                    const approveRejectedButton = makeElement(
                        "button",
                        "account-enable-button",
                        "Approve rejected application"
                    );
                    approveRejectedButton.type = "button";
                    approveRejectedButton.addEventListener("click", () =>
                        approveRejectedVet(account.userId, account.fullName)
                    );
                    actions.appendChild(approveRejectedButton);
                }

                card.appendChild(actions);
                accountList.appendChild(card);
            });
        } catch (error) {
            accountMessage.textContent = error.message || "Unable to search accounts.";
        }
    }

    loadPendingApplications();
});
