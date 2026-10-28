const API_BASE_URL = "http://127.0.0.1:8080";

document.addEventListener("DOMContentLoaded", function () {
    const email = sessionStorage.getItem("vetplatform_email");
    const role = sessionStorage.getItem("vetplatform_role");
    const token = sessionStorage.getItem("vetplatform_token");

    // Frontend navigation check only; backend authorization remains essential.
    if (!token || role !== "VET") {
        window.location.replace("login.html");
        return;
    }

    const emailDisplay = email || "Veterinarian";

    document.getElementById("vet-email").textContent = emailDisplay;
    document.getElementById("vet-profile-email").textContent = emailDisplay;

    const authHeaders = {
        Authorization: `Bearer ${token}`,
        Accept: "application/json"
    };

    async function readResponse(response) {
        let data = {};

        try {
            data = await response.json();
        } catch (error) {
            data = {};
        }

        if (!response.ok) {
            throw new Error(
                data.message ||
                data.error ||
                `Request failed (${response.status})`
            );
        }

        return data;
    }

    // Load the veterinarian's verification status.
    async function loadVerificationStatus() {
        const title = document.getElementById("vet-verification-title");
        const description = document.getElementById(
            "vet-verification-description"
        );
        const shortStatus = document.getElementById(
            "vet-verification-short"
        );

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/vets/verification-status`,
                {
                    method: "GET",
                    headers: authHeaders
                }
            );

            const data = await readResponse(response);

            const status = String(
                data.verificationStatus || data.status || ""
            ).toUpperCase();

            if (!status) {
                title.textContent = "Status unavailable";
                shortStatus.textContent = "Unavailable";
                description.textContent =
                    "The server did not return a verification status.";
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
                    "Your application is awaiting review by the administrator.";
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
                "Verification status could not be retrieved.";
        }
    }

    // Display a value safely without interpreting it as HTML.
    function addDetail(container, label, value) {
        const paragraph = document.createElement("p");
        const strong = document.createElement("strong");

        strong.textContent = `${label}: `;
        paragraph.appendChild(strong);
        paragraph.appendChild(
            document.createTextNode(
                value === null || value === undefined || value === ""
                    ? "Not provided"
                    : String(value)
            )
        );

        container.appendChild(paragraph);
    }

    function getValue(item, keys) {
        for (const key of keys) {
            if (
                item[key] !== null &&
                item[key] !== undefined &&
                item[key] !== ""
            ) {
                return item[key];
            }
        }

        return null;
    }

    function formatDate(value) {
        if (!value) {
            return "Not provided";
        }

        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return String(value);
        }

        return date.toLocaleString(undefined, {
            dateStyle: "medium",
            timeStyle: "short"
        });
    }

    // Load appointments assigned to this veterinarian.
    async function loadAppointments() {
        const list = document.getElementById("vet-appointments-list");
        const empty = document.getElementById("vet-appointments-empty");
        const statusLabel = document.getElementById(
            "vet-appointments-status"
        );
        const count = document.getElementById("vet-appointments-count");

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/vets/appointments`,
                {
                    method: "GET",
                    headers: authHeaders
                }
            );

            const data = await readResponse(response);

            // Support either a raw array or an object containing appointments.
            const appointments = Array.isArray(data)
                ? data
                : Array.isArray(data.appointments)
                    ? data.appointments
                    : null;

            if (!appointments) {
                throw new Error(
                    "The server returned an unexpected appointments format."
                );
            }

            count.textContent = String(appointments.length);
            list.replaceChildren();

            if (appointments.length === 0) {
                empty.hidden = false;
                empty.querySelector("h3").textContent =
                    "No appointments yet";
                empty.querySelector("p").textContent =
                    "Appointments assigned to you will appear here.";
                statusLabel.textContent = "No appointments";
                return;
            }

            empty.hidden = true;
            statusLabel.textContent =
                `${appointments.length} appointment(s)`;

            appointments.forEach(function (appointment) {
                const card = document.createElement("article");
                card.className = "vet-appointment-card";

                const heading = document.createElement("h3");
                heading.textContent =
                    `Appointment #${
                        getValue(appointment, ["id", "appointmentId"]) ??
                        "—"
                    }`;

                card.appendChild(heading);

                addDetail(
                    card,
                    "Pet",
                    getValue(appointment, ["petName", "pet_name"])
                );

                addDetail(
                    card,
                    "Pet parent",
                    getValue(appointment, [
                        "customerName",
                        "customerFullName",
                        "ownerName"
                    ])
                );

                const dateValue = getValue(appointment, [
                    "appointmentDateTime",
                    "scheduledAt",
                    "appointmentDate",
                    "appointment_date",
                    "date"
                ]);

                addDetail(
                    card,
                    "Date and time",
                    formatDate(dateValue)
                );

                addDetail(
                    card,
                    "Status",
                    getValue(appointment, ["status", "appointmentStatus"])
                );

                addDetail(
                    card,
                    "Reason",
                    getValue(appointment, [
                        "reason",
                        "chiefComplaint",
                        "notes"
                    ])
                );

                list.appendChild(card);
            });
        } catch (error) {
            count.textContent = "—";
            statusLabel.textContent = "Unable to load";
            empty.hidden = false;
            empty.querySelector("h3").textContent =
                "Appointments unavailable";
            empty.querySelector("p").textContent =
                "Your appointments could not be retrieved. This can happen if verification is still pending or the server is unavailable.";
            list.replaceChildren();
        }
    }

    loadVerificationStatus();


    async function loadVetCredentials() {
        const messageElement = document.getElementById("vet-credentials-message");
        const listElement = document.getElementById("vet-credentials-list");

        if (!messageElement || !listElement) {
            return;
        }

        messageElement.textContent = "Loading your uploaded documents...";
        listElement.replaceChildren();

        try {

            const response = await fetch(
                `${API_BASE_URL}/api/vets/credentials`,
                {
                    method: "GET",
                    headers: authHeaders
                }
            );


            const documents = await readResponse(response);

            if (!Array.isArray(documents)) {
                throw new Error("Unexpected response from the server.");
            }

            if (documents.length === 0) {
                messageElement.textContent =
                    "You have not uploaded any credential documents yet.";
                return;
            }

            messageElement.textContent = "";

            const documentLabels = {
                DEGREE_CERTIFICATE: "Degree Certificate",
                REGISTRATION_CERTIFICATE: "Registration Certificate",
                ADDITIONAL_CERTIFICATE: "Additional Certificate"
            };

            documents.forEach((document) => {
                const card = document.createElement("article");
                card.className = "vet-credential-card";

                const title = document.createElement("h4");
                title.textContent =
                    documentLabels[document.documentType] ||
                    document.documentType;

                const fileName = document.createElement("p");
                fileName.textContent =
                    `File: ${document.originalFileName}`;

                const status = document.createElement("p");
                status.textContent =
                    `Verification status: ${document.verificationStatus}`;

                const uploadedAt = document.createElement("p");
                const date = new Date(document.uploadedAt);

                uploadedAt.textContent =
                    `Uploaded: ${
                        Number.isNaN(date.getTime())
                            ? document.uploadedAt
                            : date.toLocaleString()
                    }`;

                const fileSize = document.createElement("p");
                fileSize.textContent =
                    `File size: ${(document.fileSize / 1024).toFixed(1)} KB`;

                card.append(title, fileName, status, uploadedAt, fileSize);
                listElement.appendChild(card);
            });
        } catch (error) {
            console.error("Failed to load veterinarian credentials:", error);

            messageElement.textContent =
                error.message || "Unable to load your credential documents.";
        }
    }



    let currentVetProfile = null;
    async function loadVetProfile() {
        const fields = {
            name: document.getElementById("vet-profile-name"),
            email: document.getElementById("vet-profile-email"),
            phone: document.getElementById("vet-profile-phone"),
            qualification: document.getElementById("vet-profile-qualification"),
            registration: document.getElementById("vet-profile-registration"),
            experience: document.getElementById("vet-profile-experience"),
            fee: document.getElementById("vet-profile-fee"),
            status: document.getElementById("vet-profile-status")
        };

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/vets/profile`,
                {
                    method: "GET",
                    headers: authHeaders
                }
            );

            const profile = await readResponse(response);
            currentVetProfile = profile;

// Fill the edit form using the profile returned by the backend.
            document.getElementById("edit-vet-name").value =
                profile.fullName || "";

            document.getElementById("edit-vet-email").value =
                profile.email || emailDisplay;

            document.getElementById("edit-vet-phone").value =
                profile.phone || "";

            document.getElementById("edit-vet-qualification").value =
                profile.qualification || "";

            document.getElementById("edit-vet-registration").value =
                profile.registrationNumber || "";

            document.getElementById("edit-vet-experience").value =
                profile.experienceYears ?? 0;

            document.getElementById("edit-vet-fee").value =
                profile.consultationFee ?? "";

            fields.name.textContent = profile.fullName || "Not provided";
            fields.email.textContent = profile.email || emailDisplay;
            fields.phone.textContent = profile.phone || "Not provided";
            fields.qualification.textContent =
                profile.qualification || "Not provided";
            fields.registration.textContent =
                profile.registrationNumber || "Not provided";
            fields.experience.textContent =
                `${profile.experienceYears ?? 0} years`;
            fields.fee.textContent =
                profile.consultationFee == null
                    ? "Not provided"
                    : new Intl.NumberFormat(undefined, {
                        style: "currency",
                        currency: "INR",
                        maximumFractionDigits: 2
                    }).format(profile.consultationFee);
            fields.status.textContent =
                profile.verificationStatus || "Unknown";

            // Use the real name in the dashboard header as well.
            document.getElementById("vet-email").textContent =
                profile.fullName || emailDisplay;
        } catch (error) {
            console.error("Failed to load veterinarian profile:", error);

            Object.values(fields).forEach(function (field) {
                field.textContent = "Unable to load";
            });

            document.getElementById("vet-profile-message").textContent =
                error.message || "Unable to load your profile.";
        }
    }

    loadVetProfile();
    loadVetCredentials();
    loadAppointments();

    // Save veterinarian profile changes.
    document.getElementById("vet-edit-profile-form")
        .addEventListener("submit", async function (event) {
            event.preventDefault();

            const message = document.getElementById("vet-profile-message");
            const submitButton = this.querySelector('button[type="submit"]');

            if (!currentVetProfile) {
                message.textContent =
                    "Your profile has not loaded. Please try again later.";
                return;
            }

            const requestBody = {
                fullName: document.getElementById("edit-vet-name").value.trim(),
                phone: document.getElementById("edit-vet-phone").value.trim(),
                qualification: document.getElementById("edit-vet-qualification").value.trim(),
                registrationNumber: document.getElementById("edit-vet-registration").value.trim(),
                experienceYears: Number(
                    document.getElementById("edit-vet-experience").value
                ),
                consultationFee: Number(
                    document.getElementById("edit-vet-fee").value
                )
            };

            if (
                !requestBody.fullName ||
                !requestBody.phone ||
                !requestBody.qualification ||
                !requestBody.registrationNumber ||
                !Number.isInteger(requestBody.experienceYears) ||
                requestBody.experienceYears < 0 ||
                requestBody.experienceYears > 70 ||
                !Number.isFinite(requestBody.consultationFee) ||
                requestBody.consultationFee < 0 ||
                requestBody.consultationFee > 1000000
            ) {
                message.textContent =
                    "Please enter valid information in all required fields.";
                return;
            }

            const originalButtonText = submitButton
                ? submitButton.textContent
                : "";

            if (submitButton) {
                submitButton.disabled = true;
                submitButton.textContent = "Saving...";
            }

            message.textContent = "";

            try {
                const response = await fetch(
                    `${API_BASE_URL}/api/vets/profile`,
                    {
                        method: "PUT",
                        headers: {
                            ...authHeaders,
                            "Content-Type": "application/json"
                        },
                        body: JSON.stringify(requestBody)
                    }
                );

                const updatedProfile = await readResponse(response);

                currentVetProfile = updatedProfile;

                document.getElementById("vet-profile-name").textContent =
                    updatedProfile.fullName || "Not provided";

                document.getElementById("vet-profile-email").textContent =
                    updatedProfile.email || emailDisplay;

                document.getElementById("vet-profile-phone").textContent =
                    updatedProfile.phone || "Not provided";

                document.getElementById("vet-profile-qualification").textContent =
                    updatedProfile.qualification || "Not provided";

                document.getElementById("vet-profile-registration").textContent =
                    updatedProfile.registrationNumber || "Not provided";

                document.getElementById("vet-profile-experience").textContent =
                    `${updatedProfile.experienceYears ?? 0} years`;

                document.getElementById("vet-profile-fee").textContent =
                    updatedProfile.consultationFee == null
                        ? "Not provided"
                        : new Intl.NumberFormat(undefined, {
                            style: "currency",
                            currency: "INR",
                            maximumFractionDigits: 2
                        }).format(updatedProfile.consultationFee);

                document.getElementById("vet-profile-status").textContent =
                    updatedProfile.verificationStatus || "Unknown";

                document.getElementById("vet-email").textContent =
                    updatedProfile.fullName || emailDisplay;

                document.getElementById("edit-vet-name").value =
                    updatedProfile.fullName || "";

                document.getElementById("edit-vet-email").value =
                    updatedProfile.email || emailDisplay;

                document.getElementById("edit-vet-phone").value =
                    updatedProfile.phone || "";

                document.getElementById("edit-vet-qualification").value =
                    updatedProfile.qualification || "";

                document.getElementById("edit-vet-registration").value =
                    updatedProfile.registrationNumber || "";

                document.getElementById("edit-vet-experience").value =
                    updatedProfile.experienceYears ?? 0;

                document.getElementById("edit-vet-fee").value =
                    updatedProfile.consultationFee ?? "";

                message.textContent =
                    "Your profile has been saved successfully.";

                loadVerificationStatus();

            } catch (error) {
                message.textContent =
                    error.message || "Unable to save your profile. Please try again.";
            } finally {
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.textContent = originalButtonText;
                }
            }
        });
// Document uploading will be connected after secure backend storage is ready.

    document.getElementById("vet-documents-form")
        .addEventListener("submit", async function (event) {
            event.preventDefault();

            const form = event.currentTarget;
            const message = document.getElementById("vet-documents-message");

            const degreeInput = document.getElementById("vet-degree-document");
            const registrationInput = document.getElementById("vet-registration-document");
            const additionalInput = document.getElementById("vet-additional-documents");

            const degreeFile = degreeInput.files[0];
            const registrationFile = registrationInput.files[0];
            const additionalFiles = Array.from(additionalInput.files);

            const submitButton = form.querySelector('button[type="submit"]');

            if (!degreeFile || !registrationFile) {
                message.textContent =
                    "Please select both your degree certificate and registration certificate.";
                return;
            }

            const allFiles = [
                degreeFile,
                registrationFile,
                ...additionalFiles
            ];

            if (allFiles.length > 5) {
                message.textContent =
                    "You can upload a maximum of 5 documents at a time.";
                return;
            }

            const maxFileSize = 10 * 1024 * 1024;

            if (allFiles.some(file => file.size > maxFileSize)) {
                message.textContent =
                    "Each document must be no larger than 10 MB.";
                return;
            }

            const formData = new FormData();

            formData.append("degreeCertificate", degreeFile);
            formData.append("registrationCertificate", registrationFile);

            additionalFiles.forEach(file => {
                formData.append("additionalCertificates", file);
            });

            message.textContent = "Uploading your documents...";

            if (submitButton) {
                submitButton.disabled = true;
            }

            try {
                const response = await fetch(
                    `${API_BASE_URL}/api/vets/credentials`,
                    {
                        method: "POST",
                        headers: {
                            Authorization: `Bearer ${token}`,
                            Accept: "application/json"
                        },
                        body: formData
                    }
                );

                const data = await readResponse(response);

                if (!response.ok) {
                    throw new Error(
                        data.message || "Unable to upload your documents."
                    );
                }

                message.textContent =
                    data.message ||
                    "Your documents have been uploaded successfully.";
                await loadVetCredentials();
                form.reset();

            } catch (error) {
                console.error("Credential document upload failed:", error);

                message.textContent =
                    error.message ||
                    "Something went wrong while uploading your documents.";
            } finally {
                if (submitButton) {
                    submitButton.disabled = false;
                }
            }
        });

    document.getElementById("vet-logout").addEventListener("click", function () {
        sessionStorage.removeItem("vetplatform_token");
        sessionStorage.removeItem("vetplatform_role");
        sessionStorage.removeItem("vetplatform_email");
        sessionStorage.removeItem("vetplatform_user_id");

        window.location.replace("login.html");
    });


    // Weekly availability management
    const availabilityForm =
        document.getElementById("vet-availability-form");
    const availabilityIdInput =
        document.getElementById("availability-id");
    const availabilityDayInput =
        document.getElementById("availability-day");
    const availabilityStartInput =
        document.getElementById("availability-start");
    const availabilityEndInput =
        document.getElementById("availability-end");
    const availabilityDurationInput =
        document.getElementById("availability-duration");
    const availabilitySubmitButton =
        document.getElementById("availability-submit-button");
    const availabilityCancelButton =
        document.getElementById("availability-cancel-button");
    const availabilityMessage =
        document.getElementById("availability-form-message");
    const availabilityList =
        document.getElementById("vet-availability-list");

    const availabilityDays = {
        1: "Monday",
        2: "Tuesday",
        3: "Wednesday",
        4: "Thursday",
        5: "Friday",
        6: "Saturday",
        7: "Sunday"
    };

    function resetAvailabilityForm() {
        availabilityForm.reset();
        availabilityIdInput.value = "";
        availabilityDurationInput.value = "30";
        availabilitySubmitButton.textContent = "Add schedule";
        availabilityCancelButton.hidden = true;
    }

    async function loadVetAvailability() {
        availabilityList.textContent = "Loading schedules...";

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/vets/availability`,
                {
                    method: "GET",
                    headers: authHeaders
                }
            );

            const result = await readResponse(response);
            const schedules = result.availability;

            if (!Array.isArray(schedules)) {
                throw new Error(
                    "The server returned an unexpected availability format."
                );
            }

            availabilityList.replaceChildren();

            if (schedules.length === 0) {
                availabilityList.textContent =
                    "No schedules yet. Add your first schedule above.";
                return;
            }

            schedules.forEach(function (schedule) {
                const card = document.createElement("article");
                card.className = "availability-card";

                const heading = document.createElement("h3");
                heading.textContent =
                    availabilityDays[schedule.dayOfWeek] || "Unknown day";

                const details = document.createElement("p");
                details.textContent =
                    `${String(schedule.startTime).slice(0, 5)}–` +
                    `${String(schedule.endTime).slice(0, 5)} · ` +
                    `${schedule.slotDurationMinutes}-minute appointments`;

                const status = document.createElement("p");
                status.textContent = schedule.active ? "Active" : "Inactive";

                card.append(heading, details, status);

                if (schedule.active) {
                    const editButton = document.createElement("button");
                    editButton.type = "button";
                    editButton.textContent = "Edit";

                    editButton.addEventListener("click", function () {
                        availabilityIdInput.value = schedule.id;
                        availabilityDayInput.value =
                            String(schedule.dayOfWeek);
                        availabilityStartInput.value =
                            String(schedule.startTime).slice(0, 5);
                        availabilityEndInput.value =
                            String(schedule.endTime).slice(0, 5);
                        availabilityDurationInput.value =
                            String(schedule.slotDurationMinutes);

                        availabilitySubmitButton.textContent = "Save changes";
                        availabilityCancelButton.hidden = false;
                        availabilityMessage.textContent =
                            "Edit the schedule above, then save your changes.";

                        availabilityForm.scrollIntoView({
                            behavior: "smooth",
                            block: "center"
                        });
                    });

                    const deactivateButton = document.createElement("button");
                    deactivateButton.type = "button";
                    deactivateButton.textContent = "Deactivate";

                    deactivateButton.addEventListener(
                        "click",
                        async function () {
                            if (!window.confirm(
                                "Deactivate this schedule?"
                            )) {
                                return;
                            }

                            deactivateButton.disabled = true;

                            try {
                                const deleteResponse = await fetch(
                                    `${API_BASE_URL}/api/vets/availability/${schedule.id}`,
                                    {
                                        method: "DELETE",
                                        headers: authHeaders
                                    }
                                );

                                await readResponse(deleteResponse);

                                availabilityMessage.textContent =
                                    "Schedule deactivated.";
                                await loadVetAvailability();
                            } catch (error) {
                                availabilityMessage.textContent =
                                    error.message ||
                                    "Could not deactivate the schedule.";
                                deactivateButton.disabled = false;
                            }
                        }
                    );

                    card.append(editButton, deactivateButton);
                }

                availabilityList.appendChild(card);
            });
        } catch (error) {
            availabilityList.textContent =
                error.message || "Could not load availability.";
        }
    }

    availabilityForm.addEventListener("submit", async function (event) {
        event.preventDefault();

        const availabilityId = availabilityIdInput.value;

        const requestBody = {
            dayOfWeek: Number(availabilityDayInput.value),
            startTime: availabilityStartInput.value,
            endTime: availabilityEndInput.value,
            slotDurationMinutes: Number(availabilityDurationInput.value)
        };

        if (
            !requestBody.dayOfWeek ||
            !requestBody.startTime ||
            !requestBody.endTime ||
            requestBody.endTime <= requestBody.startTime
        ) {
            availabilityMessage.textContent =
                "Select a day and enter a valid start and end time.";
            return;
        }

        availabilitySubmitButton.disabled = true;
        availabilityMessage.textContent = "Saving schedule...";

        try {
            const response = await fetch(
                availabilityId
                    ? `${API_BASE_URL}/api/vets/availability/${availabilityId}`
                    : `${API_BASE_URL}/api/vets/availability`,
                {
                    method: availabilityId ? "PUT" : "POST",
                    headers: {
                        ...authHeaders,
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify(requestBody)
                }
            );

            await readResponse(response);

            availabilityMessage.textContent = availabilityId
                ? "Schedule updated successfully."
                : "Schedule added successfully.";

            resetAvailabilityForm();
            await loadVetAvailability();
        } catch (error) {
            availabilityMessage.textContent =
                error.message || "Could not save the schedule.";
        } finally {
            availabilitySubmitButton.disabled = false;
        }
    });

    availabilityCancelButton.addEventListener("click", function () {
        resetAvailabilityForm();
        availabilityMessage.textContent = "Edit cancelled.";
    });

    loadVetAvailability();


});
