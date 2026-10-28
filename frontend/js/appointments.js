
document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("appointment-form");
    const message = document.getElementById("appointment-message");
    const list = document.getElementById("appointment-list");
    const petSelect = document.getElementById("pet-name");
    const vetSelect = document.getElementById("veterinarian-id");
    const dateInput = document.getElementById("appointment-date");
    const timeSelect = document.getElementById("appointment-time");
    const token = sessionStorage.getItem("vetplatform_token");
    const role = sessionStorage.getItem("vetplatform_role");

    if (!token || role !== "CUSTOMER") {
        window.location.replace("login.html");
        return;
    }

    if (!form || !message || !list || !petSelect || !vetSelect || !dateInput) {
        return;
    }

    const headers = {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json"
    };

    const submitButton = form.querySelector('button[type="submit"]');

    // Booking remains disabled until availability-aware scheduling is implemented.
    // if (submitButton) {
    //     submitButton.disabled = true;
    //     submitButton.title =
    //         "Appointment booking will be enabled after availability scheduling is implemented.";
    // }

    const today = new Date();
    dateInput.min = [
        today.getFullYear(),
        String(today.getMonth() + 1).padStart(2, "0"),
        String(today.getDate()).padStart(2, "0")
    ].join("-");

    async function fetchJson(url, options = {}) {
        const response = await fetch(url, options);

        if (response.status === 401) {
            sessionStorage.clear();
            window.location.replace("login.html");
            throw new Error("Your session has expired. Please sign in again.");
        }

        if (!response.ok) {
            throw new Error(`Request failed (HTTP ${response.status}).`);
        }

        return response.json();
    }

    async function loadPets() {
        petSelect.replaceChildren(new Option("Loading your pets…", ""));

        try {
            const data = await fetchJson(`${API_BASE_URL}/api/pets`, {
                headers
            });

            const pets = Array.isArray(data) ? data : (data.pets || []);

            petSelect.replaceChildren();

            if (pets.length === 0) {
                petSelect.add(new Option("Add a pet first", ""));
                return;
            }

            petSelect.add(new Option("Choose your pet", ""));

            pets.forEach(pet => {
                petSelect.add(
                    new Option(
                        `${pet.name} (${pet.species})`,
                        String(pet.id)
                    )
                );
            });
        } catch (error) {
            petSelect.replaceChildren(
                new Option("Unable to load pets", "")
            );
            message.textContent = error.message;
        }
    }

    async function loadVeterinarians() {
        vetSelect.replaceChildren(
            new Option("Loading approved veterinarians…", "")
        );

        try {
            // This endpoint is public, so no login token is required.
            const data = await fetchJson(
                `${API_BASE_URL}/api/vets/approved`
            );

            if (!Array.isArray(data)) {
                throw new Error("The server returned an unexpected response.");
            }

            vetSelect.replaceChildren();

            if (data.length === 0) {
                vetSelect.add(
                    new Option("No approved veterinarians available", "")
                );
                return;
            }

            vetSelect.add(new Option("Choose a veterinarian", ""));

            data.forEach(vet => {
                if (
                    !Number.isInteger(vet.userId) ||
                    typeof vet.fullName !== "string"
                ) {
                    return;
                }

                const fee = Number(vet.consultationFee);

                const label = Number.isFinite(fee)
                    ? `${vet.fullName} — ${vet.qualification} — ₹${fee.toLocaleString("en-IN")}`
                    : `${vet.fullName} — ${vet.qualification}`;

                vetSelect.add(
                    new Option(label, String(vet.userId))
                );
            });

            // Preselect the veterinarian when arriving from their profile page.
            const params = new URLSearchParams(window.location.search);
            const requestedVetId = params.get("veterinarianId");

            if (
                requestedVetId &&
                [...vetSelect.options].some(
                    option => option.value === requestedVetId
                )
            ) {
                vetSelect.value = requestedVetId;
            }
            if (dateInput.value) {
                loadAvailableSlots();
            }
        } catch (error) {
            vetSelect.replaceChildren(
                new Option("Unable to load veterinarians", "")
            );
            message.textContent = error.message;
        }
    }

    let availabilityRequestId = 0;

    async function loadAvailableSlots() {
        const requestId = ++availabilityRequestId;

        timeSelect.disabled = true;
        timeSelect.replaceChildren(
            new Option("Select a veterinarian and date first", "")
        );

        const vetUserId = vetSelect.value;
        const selectedDate = dateInput.value;

        if (!vetUserId || !selectedDate) {
            return;
        }

        timeSelect.replaceChildren(
            new Option("Loading available times…", "")
        );

        try {
            const url =
                `${API_BASE_URL}/api/vets/${encodeURIComponent(vetUserId)}` +
                `/available-slots?date=${encodeURIComponent(selectedDate)}`;

            const data = await fetchJson(url);

            // Ignore an outdated response if the user changed their selection.
            if (requestId !== availabilityRequestId) {
                return;
            }

            if (!Array.isArray(data.slots)) {
                throw new Error("Invalid availability response.");
            }

            timeSelect.replaceChildren();

            if (data.slots.length === 0) {
                timeSelect.add(
                    new Option("No available times for this date", "")
                );
                return;
            }

            timeSelect.add(new Option("Choose an available time", ""));

            data.slots.forEach(slot => {
                if (!slot.start || !slot.end) {
                    return;
                }

                const startDate = new Date(slot.start);
                const endDate = new Date(slot.end);

                if (
                    Number.isNaN(startDate.getTime()) ||
                    Number.isNaN(endDate.getTime())
                ) {
                    return;
                }

                const formatter = new Intl.DateTimeFormat("en-IN", {
                    hour: "2-digit",
                    minute: "2-digit",
                    hour12: true,
                    timeZone: data.timezone
                });

                const label =
                    `${formatter.format(startDate)} – ${formatter.format(endDate)}`;

                // Store the complete timestamps for the future booking request.
                const option = new Option(label, slot.start);
                option.dataset.end = slot.end;

                timeSelect.add(option);
            });

            timeSelect.disabled = timeSelect.options.length <= 1;

            if (timeSelect.disabled) {
                timeSelect.replaceChildren(
                    new Option("No valid available times", "")
                );
            }
        } catch (error) {
            if (requestId !== availabilityRequestId) {
                return;
            }

            timeSelect.disabled = true;
            timeSelect.replaceChildren(
                new Option("Unable to load available times", "")
            );

            message.textContent = error.message;
        }
    }

    vetSelect.addEventListener("change", loadAvailableSlots);
    dateInput.addEventListener("change", loadAvailableSlots);

    //---------------------

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        if (!form.reportValidity()) {
            return;
        }

        const selectedOption = timeSelect.selectedOptions[0];
        const petId = Number(petSelect.value);
        const vetUserId = Number(vetSelect.value);
        const scheduledStart = timeSelect.value;
        const scheduledEnd = selectedOption?.dataset.end;

        if (
            !Number.isSafeInteger(petId) ||
            petId <= 0 ||
            !Number.isSafeInteger(vetUserId) ||
            vetUserId <= 0 ||
            !scheduledStart ||
            !scheduledEnd
        ) {
            message.textContent =
                "Please select your pet, veterinarian, date, and an available time.";
            return;
        }

        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent = "Booking…";
        }

        message.textContent = "";

        try {
            const appointment = await fetchJson(
                `${API_BASE_URL}/api/appointments`,
                {
                    method: "POST",
                    headers,
                    body: JSON.stringify({
                        petId,
                        vetUserId,
                        scheduledStart,
                        scheduledEnd
                    })
                }
            );

            message.textContent =
                `Appointment #${appointment.id} booked successfully.`;

            form.reset();
            timeSelect.disabled = true;
            timeSelect.replaceChildren(
                new Option("Select a veterinarian and date first", "")
            );

            await loadAppointments();
        } catch (error) {
            message.textContent =
                `${error.message} Please refresh available times before trying again.`;

            // A previously available slot may have been booked by someone else.
            await loadAvailableSlots();
        } finally {
            if (submitButton) {
                submitButton.textContent = "Book appointment";
                submitButton.disabled = false;
                submitButton.title = "";
            }
        }
    });



    //----------------------

    async function loadAppointments() {
        list.replaceChildren();

        try {
            const data = await fetchJson(
                `${API_BASE_URL}/api/appointments`,
                { headers }
            );

            const appointments = Array.isArray(data)
                ? data
                : (data.appointments || []);

            if (appointments.length === 0) {
                list.appendChild(
                    document.createElement("p")
                ).textContent = "You have no appointments yet.";

                return;
            }

            appointments.forEach(appointment => {
                const card = document.createElement("article");
                card.className = "appointment-entry";

                const title = document.createElement("h3");
                title.textContent = `Appointment #${appointment.id}`;

                const date = document.createElement("p");
                const parsedDate = new Date(appointment.scheduledStart);

                date.textContent = Number.isNaN(parsedDate.getTime())
                    ? "Scheduled date: Not available"
                    : `Scheduled: ${parsedDate.toLocaleString()}`;

                const status = document.createElement("p");
                status.textContent = `Status: ${appointment.status}`;

                const pet = document.createElement("p");
                pet.textContent = `Pet ID: ${appointment.petId}`;

                const fee = document.createElement("p");
                fee.textContent = appointment.consultationFee != null
                    ? `Consultation fee: ₹${appointment.consultationFee}`
                    : "Consultation fee: Not available";

                card.append(title, date, status, pet, fee);

                if (appointment.meetingUrl) {
                    const meeting = document.createElement("a");
                    meeting.href = appointment.meetingUrl;
                    meeting.textContent = "Join online consultation";
                    meeting.target = "_blank";
                    meeting.rel = "noopener noreferrer";
                    card.appendChild(meeting);
                }

                list.appendChild(card);
            });
        } catch (error) {
            const errorText = document.createElement("p");
            errorText.textContent = error.message;
            list.appendChild(errorText);
        }
    }

    message.textContent =
        "Your pets, approved veterinarians, and appointment history load from your account.";

    loadPets();
    loadVeterinarians();
    loadAppointments();
});
