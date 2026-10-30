document.addEventListener("DOMContentLoaded", async () => {
    const form = document.getElementById("medical-record-form");
    const message = document.getElementById("record-message");
    const list = document.getElementById("medical-record-list");
    const summary = document.getElementById("record-summary");
    const petSelect = document.getElementById("record-pet");
    const fileInput = document.getElementById("record-file");
    const uploadButton = form?.querySelector("button[type=submit]");
    const token = sessionStorage.getItem("vetplatform_token");
    if (!form || !message || !list || !summary || !petSelect || !fileInput) return;
    if (!token || sessionStorage.getItem("vetplatform_role") !== "CUSTOMER") { window.location.replace("login.html"); return; }

    async function request(path, options = {}) {
        const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers: { Authorization: `Bearer ${token}`, ...options.headers } });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || data.message || `Request failed (${response.status})`);
        return data;
    }
    function makeCard(titleText, text) {
        const card = document.createElement("article"); card.className = "medical-record-card";
        const title = document.createElement("h3"); title.textContent = titleText; card.appendChild(title);
        if (text) { const body = document.createElement("p"); body.textContent = text; card.appendChild(body); }
        return card;
    }
    function addSection(title, entries, render) {
        const section = document.createElement("section"); section.className = "medical-history-section";
        const heading = document.createElement("h3"); heading.textContent = title; section.appendChild(heading);
        if (!entries?.length) { const empty = document.createElement("p"); empty.textContent = `No ${title.toLowerCase()} recorded.`; section.appendChild(empty); }
        else entries.forEach(entry => section.appendChild(render(entry)));
        list.appendChild(section);
    }
    function date(value) { return value ? new Date(value).toLocaleString() : "Date not provided"; }
    async function downloadDocument(record) {
        const response = await fetch(`${API_BASE_URL}/api/documents/${encodeURIComponent(record.id)}/download`, { headers: { Authorization: `Bearer ${token}` } });
        if (!response.ok) throw new Error("Could not download the document.");
        const objectUrl = URL.createObjectURL(await response.blob());
        const anchor = document.createElement("a"); anchor.href = objectUrl; anchor.download = record.originalFilename || "medical-document"; anchor.click();
        window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
    }
    async function loadHistory() {
        if (!petSelect.value) return;
        const selectedPet = Array.from(petSelect.options).find(option => option.value === petSelect.value);
        const isArchivedPet = selectedPet?.dataset.archived === "true";
        if (uploadButton) uploadButton.disabled = isArchivedPet;
        if (fileInput) fileInput.disabled = isArchivedPet;
        if (isArchivedPet) message.textContent = "This pet is archived. Its history is available to view, but new documents cannot be added.";
        else message.textContent = "";
        list.replaceChildren(); summary.textContent = "Loading this pet's medical history...";
        try {
            const history = await request(`/api/pets/${encodeURIComponent(petSelect.value)}/medical-history`);
            const pet = history.pet;
            summary.textContent = [pet.name, pet.species, pet.breed, pet.dateOfBirth && `Born ${pet.dateOfBirth}`, pet.weightKg != null && `${pet.weightKg} kg`].filter(Boolean).join(" · ");
            addSection("Consultations", history.consultations, entry => {
                const card = makeCard(`${date(entry.consultationDate)} · ${entry.vetName}`, [entry.chiefComplaint && `Complaint: ${entry.chiefComplaint}`, entry.diagnosis && `Diagnosis: ${entry.diagnosis}`, entry.clinicalNotes, entry.treatmentNotes && `Treatment: ${entry.treatmentNotes}`, entry.followUpNotes && `Follow up: ${entry.followUpNotes}`].filter(Boolean).join("\n"));
                (entry.amendments || []).forEach(a => card.appendChild(makeCard(`Amendment · ${date(a.createdAt)} · ${a.authorName}`, `${a.amendment}\nReason: ${a.reason}`)));
                return card;
            });
            addSection("Prescriptions and medications", history.prescriptions, p => makeCard(`${p.prescriptionNumber} · ${date(p.issuedAt)} · ${p.vetName} · ${p.status}`, [p.diagnosis, p.instructions, ...(p.items || []).map(i => `${i.medicineName}: ${i.dosage}, ${i.frequency}, ${i.duration}${i.route ? ` (${i.route})` : ""}${i.instructions ? ` — ${i.instructions}` : ""}`)].filter(Boolean).join("\n")));
            addSection("Vaccinations", history.vaccinations, v => makeCard(`${v.vaccineName} · ${date(v.administeredAt)}${v.veterinarianName ? ` · ${v.veterinarianName}` : ""} · ${v.status}${v.verified ? " · Verified" : " · Unverified"}`, [v.dose, v.route, v.manufacturer, v.batchNumber && `Batch ${v.batchNumber}`, v.nextDueAt && `Next due ${v.nextDueAt}`, v.notes, v.correctionReason].filter(Boolean).join(" · ")));
            addSection("Allergies", history.allergies, a => makeCard(`${a.allergen} · ${a.status}${a.severity ? ` · ${a.severity}` : ""}${a.verified ? " · Verified" : " · Unverified"}`, [a.reaction, a.identifiedAt && `Identified ${a.identifiedAt}`, `Recorded by ${a.recordedBy}`].filter(Boolean).join(" · ")));
            addSection("Existing conditions", history.conditions, c => makeCard(`${c.conditionName} · ${c.status}${c.verified ? " · Verified" : " · Unverified"}`, [c.conditionCode, c.details, c.diagnosedAt && `Diagnosed ${c.diagnosedAt}`, `Recorded by ${c.recordedBy}`].filter(Boolean).join(" · ")));
            addSection("Medical observations", history.observations, o => makeCard(`${o.testName} · ${date(o.observedAt)}`, `${o.value}${o.unit ? ` ${o.unit}` : ""}${o.verificationStatus ? ` · ${o.verificationStatus}` : ""}`));
            addSection("Reports and attachments", history.documents, d => {
                const card = makeCard(`${d.originalFilename} · ${d.documentType} · ${date(d.uploadedAt)}`, `${(d.fileSizeBytes / 1024).toFixed(1)} KB`);
                const button = document.createElement("button"); button.type = "button"; button.textContent = "Download";
                button.addEventListener("click", async () => { button.disabled = true; try { await downloadDocument(d); } catch (e) { message.textContent = e.message; } finally { button.disabled = false; } });
                card.appendChild(button); return card;
            });
        } catch (error) { summary.textContent = "Medical history is unavailable."; list.replaceChildren(); message.textContent = error.message; }
    }
    try {
        const [activeData, archivedData] = await Promise.all([
            request("/api/pets"),
            request("/api/pets/archived")
        ]);
        const activePets = Array.isArray(activeData.pets) ? activeData.pets : [];
        const archivedPets = (Array.isArray(archivedData.pets) ? archivedData.pets : [])
            .map(pet => ({ ...pet, archived: true }));
        const pets = [...activePets, ...archivedPets];
        petSelect.replaceChildren(new Option(pets.length ? "Select a pet" : "Add a pet first", ""));
        pets.forEach(p => {
            const option = new Option(`${p.name} (${p.species})${p.archived ? " · Archived" : ""}`, p.id);
            option.dataset.archived = String(Boolean(p.archived));
            petSelect.add(option);
        });
        const requestedPet = new URLSearchParams(location.search).get("petId");
        if (requestedPet && pets.some(p => String(p.id) === requestedPet)) petSelect.value = requestedPet;
        if (!pets.length) { message.textContent = "Add a pet before managing its medical records."; if (uploadButton) uploadButton.disabled = true; }
        else await loadHistory();
    } catch (e) { petSelect.replaceChildren(new Option("Could not load pets", "")); message.textContent = e.message; }
    petSelect.addEventListener("change", () => { const url = new URL(location.href); if (petSelect.value) url.searchParams.set("petId", petSelect.value); else url.searchParams.delete("petId"); history.replaceState(null, "", url); loadHistory(); });
    form.addEventListener("submit", async event => {
        event.preventDefault(); message.textContent = ""; if (!form.reportValidity() || !petSelect.value) return;
        const file = fileInput.files[0]; if (!file) return;
        if (file.size > 10 * 1024 * 1024 || !["application/pdf", "image/jpeg", "image/png"].includes(file.type)) { message.textContent = "Choose a PDF, JPG or PNG file no larger than 10 MB."; return; }
        const submit = form.querySelector("button[type=submit]"); submit.disabled = true;
        try {
            const selectedPetId = petSelect.value;
            const body = new FormData(); body.append("documentType", form.elements.recordType.value); body.append("file", file, file.name);
            await request(`/api/pets/${encodeURIComponent(selectedPetId)}/documents`, { method: "POST", body });
            form.reset(); petSelect.value = selectedPetId;
            message.textContent = "Document uploaded to this pet's permanent record."; await loadHistory();
        } catch (e) { message.textContent = e.message; } finally { submit.disabled = false; }
    });
});
