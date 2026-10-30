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

    function addPatientMedicalRecords(card, appointmentId, appointmentStatus) {
        const section = document.createElement("section");
        section.className = "vet-patient-records";
        const heading = document.createElement("h4");
        heading.textContent = "Pet-wise medical history";
        const message = document.createElement("p");
        message.className = "vet-patient-records-message";
        const list = document.createElement("div");
        list.className = "vet-patient-records-list";
        const button = document.createElement("button");
        button.type = "button";
        button.className = "vet-patient-records-button";
        button.textContent = "Open pet medical history";
        section.append(heading, message, button, list);
        card.appendChild(section);

        button.addEventListener("click", async () => {
            button.disabled = true;
            message.textContent = "Loading this pet's history...";
            list.replaceChildren();
            try {
                const response = await fetch(
                    `${API_BASE_URL}/api/vets/appointments/${encodeURIComponent(appointmentId)}/medical-history`,
                    { headers: authHeaders }
                );
                const data = await readResponse(response);
                const groups = [
                    ["Consultation", data.consultations || [], r => `${r.consultationDate} · ${r.vetName} · ${r.diagnosis || r.chiefComplaint || "Visit"}`, r => [r.chiefComplaint, r.diagnosis, r.clinicalNotes, r.treatmentNotes, r.followUpNotes, ...(r.amendments || []).map(a => `Amendment by ${a.authorName}: ${a.amendment} (${a.reason})`)].filter(Boolean).join("\n")],
                    ["Prescription", data.prescriptions || [], r => `${r.prescriptionNumber} · ${r.issuedAt} · ${r.vetName} · ${r.status}`, r => [r.diagnosis, r.instructions, ...(r.items || []).map(i => `${i.medicineName}: ${i.dosage}, ${i.frequency}, ${i.duration}`)].filter(Boolean).join("\n")],
                    ["Vaccination", data.vaccinations || [], r => `${r.vaccineName} · ${r.administeredAt} · ${r.status}${r.verified ? " · Verified" : " · Unverified"}`, r => [r.dose, r.route, r.nextDueAt && `Next due ${r.nextDueAt}`, r.notes, r.correctionReason].filter(Boolean).join(" · ")],
                    ["Allergy", data.allergies || [], r => `${r.allergen} · ${r.status}${r.severity ? ` · ${r.severity}` : ""}${r.verified ? " · Verified" : " · Unverified"}`, r => [r.reaction, `Recorded by ${r.recordedBy}`].filter(Boolean).join(" · ")],
                    ["Condition", data.conditions || [], r => `${r.conditionName} · ${r.status}${r.verified ? " · Verified" : " · Unverified"}`, r => [r.conditionCode, r.details, `Recorded by ${r.recordedBy}`].filter(Boolean).join(" · ")],
                    ["Observation", data.observations || [], r => `${r.testName} · ${r.observedAt} · ${r.verificationStatus}`, r => `${r.value}${r.unit ? ` ${r.unit}` : ""}`],
                    ["Document", data.documents || [], r => `${r.originalFilename} · ${r.documentType} · ${r.uploadedAt}`, r => `${(r.fileSizeBytes / 1024).toFixed(1)} KB`]
                ];
                const total = groups.reduce((sum, g) => sum + g[1].length, 0);
                message.textContent = `${data.pet.name}: ${total} medical record(s).`;
                const profile = document.createElement("p");
                profile.textContent = [data.pet.name, data.pet.species, data.pet.breed, data.pet.dateOfBirth && `Born ${data.pet.dateOfBirth}`, data.pet.weightKg != null && `${data.pet.weightKg} kg`].filter(Boolean).join(" · ");
                list.appendChild(profile);
                const appointmentConsultation = (data.consultations || []).find(r => String(r.appointmentId) === String(appointmentId));
                if (appointmentStatus === "COMPLETED" && appointmentConsultation) {
                    const consultationForm = card.querySelector("form[data-consultation-form='true']");
                    if (consultationForm) consultationForm.hidden = true;
                    if (!card.querySelector(`form[data-prescription-for='${appointmentConsultation.id}']`)) addPrescriptionForm(card, appointmentConsultation.id);
                    if (!card.querySelector(`form[data-amendment-for='${appointmentConsultation.id}']`)) addAmendmentForm(card, appointmentConsultation.id);
                }
                groups.forEach(([type, records, getTitle, getDetails]) => records.forEach((record) => {
                    const item = document.createElement("article");
                    item.className = "vet-patient-record-item";
                    const name = document.createElement("strong");
                    name.textContent = `${type}: ${getTitle(record)}`;
                    const details = document.createElement("span");
                    details.textContent = getDetails(record) || "No further details";
                    item.append(name, details);
                    if (type === "Document") {
                    const download = document.createElement("button"); download.type = "button"; download.textContent = "Download";
                    download.addEventListener("click", async () => {
                        download.disabled = true;
                        try {
                            const fileResponse = await fetch(
                                `${API_BASE_URL}/api/vets/appointments/${encodeURIComponent(appointmentId)}/medical-documents/${encodeURIComponent(record.id)}/download`,
                                { headers: authHeaders }
                            );
                            if (!fileResponse.ok) await readResponse(fileResponse);
                            const objectUrl = URL.createObjectURL(await fileResponse.blob());
                            const link = document.createElement("a");
                            link.href = objectUrl;
                            link.download = record.originalFilename || "medical-document";
                            link.click();
                            window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
                        } catch (error) {
                            message.textContent = error.message || "Unable to download this document.";
                        } finally {
                            download.disabled = false;
                        }
                    });
                    item.appendChild(download);
                    }
                    if (appointmentStatus === "COMPLETED" && (type === "Allergy" || type === "Condition") && record.status !== "RESOLVED") {
                        const resolve = document.createElement("button"); resolve.type = "button"; resolve.textContent = "Mark resolved";
                        resolve.addEventListener("click", async () => {
                            resolve.disabled = true;
                            try {
                                const recordType = type === "Allergy" ? "allergies" : "conditions";
                                const response = await fetch(`${API_BASE_URL}/api/vets/appointments/${encodeURIComponent(appointmentId)}/${recordType}/${encodeURIComponent(record.id)}/status`, { method: "PATCH", headers: { ...authHeaders, "Content-Type": "application/json" }, body: JSON.stringify({ status: "RESOLVED" }) });
                                await readResponse(response); card.querySelector(".vet-patient-records-button")?.click();
                            } catch (error) { message.textContent = error.message || "Unable to update this record."; resolve.disabled = false; }
                        });
                        item.appendChild(resolve);
                    }
                    if (appointmentStatus === "COMPLETED" && type === "Vaccination" && record.status === "RECORDED") {
                        const voidButton = document.createElement("button"); voidButton.type = "button"; voidButton.textContent = "Correct this record";
                        voidButton.addEventListener("click", async () => {
                            const reason = window.prompt("Explain why this vaccination entry is being voided. Add the corrected vaccination as a new record afterward.");
                            if (!reason?.trim()) return;
                            voidButton.disabled = true;
                            try {
                                const response = await fetch(`${API_BASE_URL}/api/vets/appointments/${encodeURIComponent(appointmentId)}/vaccinations/${encodeURIComponent(record.id)}/status`, { method: "PATCH", headers: { ...authHeaders, "Content-Type": "application/json" }, body: JSON.stringify({ status: "VOIDED", reason }) });
                                await readResponse(response); card.querySelector(".vet-patient-records-button")?.click();
                            } catch (error) { message.textContent = error.message || "Unable to correct this vaccination record."; voidButton.disabled = false; }
                        });
                        item.appendChild(voidButton);
                    }
                    list.appendChild(item);
                }));
            } catch (error) {
                message.textContent = error.message || "Unable to load this pet's history.";
            } finally {
                button.disabled = false;
            }
        });
    }

    function addConsultationForm(card, appointmentId) {
        const form = document.createElement("form");
        form.className = "vet-consultation-form";
        form.dataset.consultationForm = "true";
        const heading = document.createElement("h4"); heading.textContent = "Add consultation record";
        const fields = [
            ["chiefComplaint", "Symptoms and health complaint"],
            ["diagnosis", "Diagnosis"],
            ["clinicalNotes", "Clinical notes"],
            ["treatmentNotes", "Treatment details"],
            ["followUpNotes", "Follow-up plan"]
        ];
        const inputs = {};
        fields.forEach(([name, label]) => {
            const fieldLabel = document.createElement("label"); fieldLabel.textContent = label;
            const input = document.createElement("textarea"); input.name = name; input.maxLength = 10000;
            input.id = `consultation-${appointmentId}-${name}`; fieldLabel.htmlFor = input.id;
            inputs[name] = input; form.append(fieldLabel, input);
        });
        const submit = document.createElement("button"); submit.type = "submit"; submit.textContent = "Save consultation";
        const feedback = document.createElement("p"); feedback.setAttribute("role", "status");
        form.prepend(heading); form.append(submit, feedback); card.appendChild(form);
        form.addEventListener("submit", async event => {
            event.preventDefault(); submit.disabled = true; feedback.textContent = "Saving consultation...";
            try {
                const response = await fetch(`${API_BASE_URL}/api/vets/consultations`, {
                    method: "POST", headers: { ...authHeaders, "Content-Type": "application/json" },
                    body: JSON.stringify({ appointmentId, ...Object.fromEntries(Object.entries(inputs).map(([key, input]) => [key, input.value.trim() || null])) })
                });
                const consultation = await readResponse(response);
                feedback.textContent = "Consultation saved. The pet history is updated.";
                Object.values(inputs).forEach(input => input.value = "");
                addPrescriptionForm(card, consultation.id);
                card.querySelector(".vet-patient-records-button")?.click();
            } catch (error) { feedback.textContent = error.message || "Unable to save consultation."; }
            finally { submit.disabled = false; }
        });
        appendHealthFactForms(card, appointmentId);
    }

    function addPrescriptionForm(card, consultationId) {
        const form = document.createElement("form"); form.className = "vet-consultation-form";
        form.dataset.prescriptionFor = String(consultationId);
        const heading = document.createElement("h4"); heading.textContent = "Issue prescription";
        const fields = [["medicineName", "Medicine"], ["dosage", "Dosage"], ["frequency", "Frequency"], ["duration", "Duration"], ["route", "Route"], ["diagnosis", "Prescription diagnosis"], ["instructions", "Instructions"]];
        const inputs = {};
        fields.forEach(([name, label]) => { const l = document.createElement("label"); l.textContent = label; const input = name === "instructions" ? document.createElement("textarea") : document.createElement("input"); input.name = name; input.id = `prescription-${consultationId}-${name}`; l.htmlFor = input.id; inputs[name] = input; form.append(l, input); });
        const submit = document.createElement("button"); submit.type = "submit"; submit.textContent = "Issue prescription";
        const feedback = document.createElement("p"); feedback.setAttribute("role", "status"); form.prepend(heading); form.append(submit, feedback); card.appendChild(form);
        form.addEventListener("submit", async event => {
            event.preventDefault();
            for (const key of ["medicineName", "dosage", "frequency", "duration"]) if (!inputs[key].value.trim()) { feedback.textContent = "Complete medicine, dosage, frequency and duration."; return; }
            submit.disabled = true;
            try {
                const response = await fetch(`${API_BASE_URL}/api/vets/consultations/${encodeURIComponent(consultationId)}/prescriptions`, { method: "POST", headers: { ...authHeaders, "Content-Type": "application/json" }, body: JSON.stringify({ consultationId, prescriptionNumber: `RX-${Date.now()}`, diagnosis: inputs.diagnosis.value || null, instructions: inputs.instructions.value || null, items: [{ medicineName: inputs.medicineName.value, dosage: inputs.dosage.value, frequency: inputs.frequency.value, duration: inputs.duration.value, route: inputs.route.value || null }] }) });
                await readResponse(response); feedback.textContent = "Prescription issued and saved in this pet's history."; card.querySelector(".vet-patient-records-button")?.click();
            } catch (error) { feedback.textContent = error.message || "Unable to issue prescription."; }
            finally { submit.disabled = false; }
        });
    }

    function addAmendmentForm(card, consultationId) {
        const form = document.createElement("form"); form.className = "vet-consultation-form"; form.dataset.amendmentFor = String(consultationId);
        const heading = document.createElement("h4"); heading.textContent = "Append a correction or addendum";
        const amendment = document.createElement("textarea"); amendment.required = true; amendment.placeholder = "Additional or corrected information";
        const reason = document.createElement("input"); reason.required = true; reason.placeholder = "Reason for amendment";
        const submit = document.createElement("button"); submit.type = "submit"; submit.textContent = "Append amendment";
        const feedback = document.createElement("p"); feedback.setAttribute("role", "status"); form.append(heading, amendment, reason, submit, feedback); card.appendChild(form);
        form.addEventListener("submit", async event => {
            event.preventDefault(); submit.disabled = true;
            try {
                const response = await fetch(`${API_BASE_URL}/api/vets/consultations/${encodeURIComponent(consultationId)}/amendments`, { method: "POST", headers: { ...authHeaders, "Content-Type": "application/json" }, body: JSON.stringify({ amendment: amendment.value, reason: reason.value }) });
                await readResponse(response); feedback.textContent = "Amendment appended. The original consultation is preserved."; amendment.value = ""; reason.value = ""; card.querySelector(".vet-patient-records-button")?.click();
            } catch (error) { feedback.textContent = error.message || "Unable to append amendment."; }
            finally { submit.disabled = false; }
        });
    }

    function appendHealthFactForms(card, appointmentId) {
        const wrap = document.createElement("details"); wrap.className = "vet-consultation-form";
        const summary = document.createElement("summary"); summary.textContent = "Record allergy, condition or vaccination"; wrap.appendChild(summary);
        const kind = document.createElement("select"); [["allergies", "Allergy"], ["conditions", "Existing condition"], ["vaccinations", "Vaccination"]].forEach(([v, t]) => kind.add(new Option(t, v)));
        kind.setAttribute("aria-label", "Medical record type");
        const name = document.createElement("input"); name.placeholder = "Name"; name.required = true; name.setAttribute("aria-label", "Allergy, condition, or vaccine name");
        const details = document.createElement("textarea"); details.placeholder = "Reaction, details or notes"; details.setAttribute("aria-label", "Reaction, condition details, or vaccination notes");
        const dateInput = document.createElement("input"); dateInput.type = "date"; dateInput.setAttribute("aria-label", "Date identified or administered");
        const dueDateInput = document.createElement("input"); dueDateInput.type = "date"; dueDateInput.setAttribute("aria-label", "Next vaccination due date");
        const severity = document.createElement("select"); severity.setAttribute("aria-label", "Allergy severity"); [["", "Severity (optional)"], ["MILD", "Mild"], ["MODERATE", "Moderate"], ["SEVERE", "Severe"], ["UNKNOWN", "Unknown"]].forEach(([v,t]) => severity.add(new Option(t,v)));
        const submit = document.createElement("button"); submit.type = "button"; submit.textContent = "Save medical information";
        const feedback = document.createElement("p"); wrap.append(kind, name, details, dateInput, dueDateInput, severity, submit, feedback); card.appendChild(wrap);
        submit.addEventListener("click", async () => {
            submit.disabled = true;
            const isVax = kind.value === "vaccinations";
            const body = isVax ? { vaccineName: name.value.trim(), administeredAt: dateInput.value ? `${dateInput.value}T12:00:00Z` : new Date().toISOString(), nextDueAt: dueDateInput.value || null, notes: details.value || null } : { name: name.value.trim(), details: details.value || null, date: dateInput.value || null, severity: kind.value === "allergies" ? severity.value || null : null };
            try {
                const response = await fetch(`${API_BASE_URL}/api/vets/appointments/${encodeURIComponent(appointmentId)}/${kind.value}`, { method: "POST", headers: { ...authHeaders, "Content-Type": "application/json" }, body: JSON.stringify(body) });
                await readResponse(response); feedback.textContent = "Saved and added to this pet's history."; name.value = ""; details.value = ""; dateInput.value = ""; dueDateInput.value = ""; card.querySelector(".vet-patient-records-button")?.click();
            } catch (error) { feedback.textContent = error.message || "Unable to save medical information."; }
            finally { submit.disabled = false; }
        });
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
        const rejectionElement = document.getElementById("vet-verification-rejection");
        const reapplyButton = document.getElementById("vet-reapply-button");

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
            reapplyButton.hidden = status !== "REJECTED";
            rejectionElement.hidden = status !== "REJECTED" || !data.rejectionReason;
            rejectionElement.textContent = data.rejectionReason
                ? `Administrator feedback: ${data.rejectionReason}`
                : "";

            if (status === "APPROVED" || status === "VERIFIED") {
                description.textContent =
                    "Your verification has been approved by the platform.";
            } else if (status === "PENDING") {
                description.textContent =
                    "Your application is awaiting review by the administrator.";
            } else if (status === "REJECTED") {
                description.textContent =
                    "Your application was rejected. Please review the information provided by the platform.";
            } else if (status === "SUSPENDED") {
                description.textContent =
                    "Your veterinarian approval is currently suspended. Contact VetConnect support for more information.";
            } else {
                description.textContent =
                    "This is the verification status returned by the server.";
            }
        } catch (error) {
            reapplyButton.hidden = true;
            rejectionElement.hidden = true;
            title.textContent = "Unable to load";
            shortStatus.textContent = "Unavailable";
            description.textContent =
                "Verification status could not be retrieved.";
        }
    }

    document.getElementById("vet-reapply-button").addEventListener("click", async () => {
        const message = document.getElementById("vet-reapply-message");
        const updateSummary = window.prompt(
            "Briefly describe what you changed or improved before resubmitting (required):"
        );
        if (updateSummary === null) return;
        const summary = updateSummary.trim();
        if (!summary || summary.length > 500) {
            message.textContent = "Enter a summary of 1 to 500 characters.";
            return;
        }

        const button = document.getElementById("vet-reapply-button");
        button.disabled = true;
        message.textContent = "Submitting your application...";
        try {
            const response = await fetch(`${API_BASE_URL}/api/vets/reapply`, {
                method: "POST",
                headers: { ...authHeaders, "Content-Type": "application/json" },
                body: JSON.stringify({ updateSummary: summary })
            });
            const result = await readResponse(response);
            message.textContent = result.message || "Application resubmitted for review.";
            await loadVerificationStatus();
        } catch (error) {
            message.textContent = error.message || "Unable to resubmit your application.";
        } finally {
            button.disabled = false;
        }
    });

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
                    "scheduledStart",
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

// Allow cancellation only for confirmed appointments
// whose scheduled start time is still in the future.
                const appointmentStatus = String(
                    getValue(appointment, ["status", "appointmentStatus"]) || ""
                ).toUpperCase();

                const appointmentId = getValue(appointment, ["id", "appointmentId"]);
                if (["CONFIRMED", "COMPLETED"].includes(appointmentStatus) && appointmentId != null) {
                    addPatientMedicalRecords(card, appointmentId, appointmentStatus);
                }
                if (appointmentStatus === "COMPLETED" && appointmentId != null) {
                    addConsultationForm(card, appointmentId);
                }
                const scheduledStart = getValue(appointment, ["scheduledStart"]);
                const startDate = scheduledStart ? new Date(scheduledStart) : null;

                if (
                    appointmentStatus === "CONFIRMED" &&
                    startDate &&
                    !Number.isNaN(startDate.getTime()) &&
                    startDate.getTime() > Date.now() &&
                    appointmentId != null
                ) {
                    const cancelButton = document.createElement("button");
                    cancelButton.type = "button";
                    cancelButton.className = "vet-cancel-appointment-button";
                    cancelButton.textContent = "Cancel appointment";

                    cancelButton.addEventListener("click", async function () {
                        const confirmed = window.confirm(
                            `Are you sure you want to cancel appointment #${appointmentId}?`
                        );

                        if (!confirmed) {
                            return;
                        }

                        cancelButton.disabled = true;
                        cancelButton.textContent = "Cancelling...";
                        statusLabel.textContent = `Cancelling appointment #${appointmentId}...`;

                        try {
                            const response = await fetch(
                                `${API_BASE_URL}/api/vets/appointments/${encodeURIComponent(appointmentId)}/status`,
                                {
                                    method: "PATCH",
                                    headers: {
                                        ...authHeaders,
                                        "Content-Type": "application/json"
                                    },
                                    body: JSON.stringify({
                                        action: "CANCELLED"
                                    })
                                }
                            );

                            await readResponse(response);

                            statusLabel.textContent =
                                `Appointment #${appointmentId} cancelled successfully.`;

                            // Reload from the backend so the displayed status is current.
                            await loadAppointments();
                        } catch (error) {
                            statusLabel.textContent =
                                error.message || "Unable to cancel this appointment.";

                            cancelButton.disabled = false;
                            cancelButton.textContent = "Cancel appointment";
                        }
                    });

                    card.appendChild(cancelButton);
                }

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

            documents.forEach((credential) => {
                const card = document.createElement("article");
                card.className = "vet-credential-card";

                const title = document.createElement("h4");
                title.textContent =
                    documentLabels[credential.documentType] ||
                    credential.documentType;

                const fileName = document.createElement("p");
                fileName.textContent =
                    `File: ${credential.originalFileName}`;

                const status = document.createElement("p");
                status.textContent =
                    `Verification status: ${credential.verificationStatus}`;

                const uploadedAt = document.createElement("p");
                const date = new Date(credential.uploadedAt);

                uploadedAt.textContent =
                    `Uploaded: ${
                        Number.isNaN(date.getTime())
                            ? credential.uploadedAt
                            : date.toLocaleString()
                    }`;

                const fileSize = document.createElement("p");
                fileSize.textContent =
                    `File size: ${(credential.fileSize / 1024).toFixed(1)} KB`;

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
