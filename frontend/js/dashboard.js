
document.addEventListener("DOMContentLoaded", function () {
    // 1. Mobile sidebar
    const sidebar = document.getElementById("sidebar");
    const toggleButton = document.getElementById("sidebar-toggle");

    if (sidebar && toggleButton) {
        toggleButton.addEventListener("click", function () {
            const isOpen = sidebar.classList.toggle("open");
            toggleButton.setAttribute("aria-expanded", String(isOpen));
        });

        document.addEventListener("click", function (event) {
            if (
                window.innerWidth <= 850 &&
                sidebar.classList.contains("open") &&
                !sidebar.contains(event.target) &&
                !toggleButton.contains(event.target)
            ) {
                sidebar.classList.remove("open");
                toggleButton.setAttribute("aria-expanded", "false");
            }
        });
    }

    // 2. Check customer session
    const token = sessionStorage.getItem("vetplatform_token");
    const role = sessionStorage.getItem("vetplatform_role");
    const email = sessionStorage.getItem("vetplatform_email");

    if (!token || role !== "CUSTOMER") {
        window.location.replace("login.html");
        return;
    }

    // 3. Display customer email
    const profileName = document.getElementById("customer-name");

    if (profileName) {
        profileName.textContent = email || "Pet Parent";
    }


    // 4. Welcome heading
    const welcomeHeading = document.getElementById("dashboard-welcome-heading");

    if (welcomeHeading) {
        welcomeHeading.textContent = "Welcome back! 🐾";
    }


    // 5. Load real pet information from the backend
    const petCountElement = document.getElementById("pet-count");
    const dashboardPetsElement = document.getElementById("dashboard-pets");
    const dashboardAppointmentsElement =
        document.getElementById("dashboard-appointments");
    const appointmentCountElement = document.getElementById("appointment-count");

    const dashboardRecordsElement =
        document.getElementById("dashboard-records");

    const recordCountElement =
        document.getElementById("record-count");

    async function loadDashboardPets() {
        if (!petCountElement || !dashboardPetsElement) {
            return;
        }

        petCountElement.textContent = "…";
        dashboardPetsElement.textContent = "Loading your pets…";

        try {
            const response = await fetch(`${API_BASE_URL}/api/pets`, {
                method: "GET",
                headers: {
                    "Authorization": `Bearer ${token}`,
                    "Accept": "application/json"
                }
            });

            const data = await response.json();

            if (response.status === 401) {
                sessionStorage.removeItem("vetplatform_token");
                sessionStorage.removeItem("vetplatform_role");
                sessionStorage.removeItem("vetplatform_email");
                sessionStorage.removeItem("vetplatform_user_id");

                window.location.replace("login.html");
                return;
            }

            if (!response.ok) {
                throw new Error(data.message || "Could not load pets.");
            }

            const pets = Array.isArray(data.pets) ? data.pets : [];

            petCountElement.textContent = String(pets.length);
            dashboardPetsElement.replaceChildren();

            if (pets.length === 0) {
                const emptyCard = document.createElement("article");
                emptyCard.className = "empty-state";

                const icon = document.createElement("div");
                icon.className = "empty-state-icon";
                icon.textContent = "🐾";

                const heading = document.createElement("h3");
                heading.textContent = "No pets added yet";

                const description = document.createElement("p");
                description.textContent =
                    "Add your pet to start organizing their care.";

                const link = document.createElement("a");
                link.href = "pets.html";
                link.className = "btn btn-secondary";
                link.textContent = "Add your first pet";

                emptyCard.append(icon, heading, description, link);
                dashboardPetsElement.appendChild(emptyCard);
                return;
            }

            pets.slice(0, 3).forEach(function (pet) {
                if (
                    !pet ||
                    typeof pet.name !== "string" ||
                    typeof pet.species !== "string"
                ) {
                    return;
                }

                const card = document.createElement("article");
                card.className = "pet-card";

                const heading = document.createElement("h3");
                heading.textContent = pet.name;


                const species = document.createElement("p");
                species.className = "pet-card-species";
                species.textContent = pet.species;

                const breed = document.createElement("p");
                breed.className = "pet-card-breed";
                breed.textContent = pet.breed
                    ? `Breed: ${pet.breed}`
                    : "Breed not provided";


                const link = document.createElement("a");
                link.href = "pets.html";
                link.className = "btn btn-secondary";
                link.textContent = "View pet";

                card.append(heading, species, breed, link);
                dashboardPetsElement.appendChild(card);
            });

            if (dashboardPetsElement.childElementCount === 0) {
                dashboardPetsElement.textContent =
                    "Pet details could not be displayed.";
            }
        } catch (error) {
            console.error("Could not load dashboard pets:", error);

            petCountElement.textContent = "—";
            dashboardPetsElement.textContent =
                "Pet information is currently unavailable. Please try again later.";
        }
    }

    loadDashboardPets();



    async function loadDashboardAppointments() {
        if (!appointmentCountElement || !dashboardAppointmentsElement) {
            return;
        }

        appointmentCountElement.textContent = "…";
        dashboardAppointmentsElement.textContent =
            "Loading your upcoming appointments…";

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/appointments`,
                {
                    method: "GET",
                    headers: {
                        "Authorization": `Bearer ${token}`,
                        "Accept": "application/json"
                    }
                }
            );

            if (response.status === 401) {
                sessionStorage.removeItem("vetplatform_token");
                sessionStorage.removeItem("vetplatform_role");
                sessionStorage.removeItem("vetplatform_email");
                sessionStorage.removeItem("vetplatform_user_id");

                window.location.replace("login.html");
                return;
            }

            if (!response.ok) {
                throw new Error("Could not load appointments.");
            }

            const data = await response.json();

            const appointments = Array.isArray(data)
                ? data
                : (Array.isArray(data.appointments)
                    ? data.appointments
                    : []);

            appointmentCountElement.textContent = String(appointments.length);
            dashboardAppointmentsElement.replaceChildren();

            const now = new Date();

            const upcomingAppointments = appointments
                .filter(appointment => {
                    if (!appointment || !appointment.scheduledStart) {
                        return false;
                    }

                    const start = new Date(appointment.scheduledStart);

                    return (
                        !Number.isNaN(start.getTime()) &&
                        start >= now &&
                        appointment.status !== "CANCELLED"
                    );
                })
                .sort((a, b) =>
                    new Date(a.scheduledStart) - new Date(b.scheduledStart)
                )
                .slice(0, 3);

            if (upcomingAppointments.length === 0) {
                const emptyMessage = document.createElement("p");
                emptyMessage.className = "empty-state";
                emptyMessage.textContent = "You have no upcoming appointments.";

                const link = document.createElement("a");
                link.href = "appointments.html";
                link.className = "btn btn-secondary";
                link.textContent = "View appointments";

                dashboardAppointmentsElement.append(emptyMessage, link);
                return;
            }

            upcomingAppointments.forEach(appointment => {
                const card = document.createElement("article");
                card.className = "appointment-entry";

                const heading = document.createElement("h3");
                heading.textContent = `Appointment #${appointment.id}`;

                const date = document.createElement("p");
                date.textContent =
                    new Date(appointment.scheduledStart).toLocaleString(
                        "en-IN",
                        {
                            dateStyle: "medium",
                            timeStyle: "short"
                        }
                    );


                const status = document.createElement("p");
                status.className = "appointment-status";

                const appointmentStatus = appointment.status || "UNKNOWN";

                status.textContent =
                    `Status: ${appointmentStatus.replaceAll("_", " ")}`;

                status.dataset.status = appointmentStatus.toLowerCase();


                const pet = document.createElement("p");
                pet.textContent = appointment.petName
                    ? `Pet: ${appointment.petName}`
                    : appointment.petId != null
                        ? `Pet ID: ${appointment.petId}`
                        : "Pet details unavailable";

                card.append(heading, date, status, pet);
                dashboardAppointmentsElement.appendChild(card);
            });

            const viewAllLink = document.createElement("a");
            viewAllLink.href = "appointments.html";
            viewAllLink.className = "btn btn-secondary";
            viewAllLink.textContent = "View all appointments";

            dashboardAppointmentsElement.appendChild(viewAllLink);

        } catch (error) {
            console.error("Could not load dashboard appointments:", error);

            appointmentCountElement.textContent = "—";
            dashboardAppointmentsElement.textContent =
                "Upcoming appointments are currently unavailable.";
        }
    }


    loadDashboardAppointments();


    async function loadDashboardRecords() {
        if (!recordCountElement || !dashboardRecordsElement) {
            return;
        }

        recordCountElement.textContent = "…";
        dashboardRecordsElement.textContent =
            "Loading your medical records…";

        try {
            // First, retrieve the customer's pets.
            const petsResponse = await fetch(
                `${API_BASE_URL}/api/pets`,
                {
                    method: "GET",
                    headers: {
                        "Authorization": `Bearer ${token}`,
                        "Accept": "application/json"
                    }
                }
            );

            if (petsResponse.status === 401) {
                sessionStorage.removeItem("vetplatform_token");
                sessionStorage.removeItem("vetplatform_role");
                sessionStorage.removeItem("vetplatform_email");
                sessionStorage.removeItem("vetplatform_user_id");

                window.location.replace("login.html");
                return;
            }

            if (!petsResponse.ok) {
                throw new Error("Could not load your pets.");
            }

            const petsData = await petsResponse.json();

            const pets = Array.isArray(petsData)
                ? petsData
                : (Array.isArray(petsData.pets) ? petsData.pets : []);

            // Retrieve documents for each pet.
            const documentGroups = await Promise.all(
                pets.map(async pet => {
                    const response = await fetch(
                        `${API_BASE_URL}/api/pets/${encodeURIComponent(pet.id)}/documents`,
                        {
                            method: "GET",
                            headers: {
                                "Authorization": `Bearer ${token}`,
                                "Accept": "application/json"
                            }
                        }
                    );

                    if (response.status === 401) {
                        throw new Error("SESSION_EXPIRED");
                    }

                    if (!response.ok) {
                        throw new Error(
                            `Could not load documents for pet ${pet.id}.`
                        );
                    }

                    const data = await response.json();

                    const documents = Array.isArray(data.documents)
                        ? data.documents
                        : [];

                    return documents.map(document => ({
                        ...document,
                        petName: pet.name
                    }));
                })
            );

            const documents = documentGroups
                .flat()
                .sort((a, b) =>
                    new Date(b.uploadedAt) - new Date(a.uploadedAt)
                );

            recordCountElement.textContent = String(documents.length);
            dashboardRecordsElement.replaceChildren();

            if (documents.length === 0) {
                const emptyMessage = document.createElement("p");
                emptyMessage.className = "empty-state";
                emptyMessage.textContent =
                    "No medical documents have been uploaded yet.";

                const link = document.createElement("a");
                link.href = "medical-records.html";
                link.className = "btn btn-secondary";
                link.textContent = "View medical records";

                dashboardRecordsElement.append(emptyMessage, link);
                return;
            }

            documents.slice(0, 3).forEach(document => {
                const card = document.createElement("article");
                card.className = "record-entry";

                const heading = document.createElement("h3");
                heading.textContent =
                    document.originalFilename || "Medical document";

                const type = document.createElement("span");
                type.className = "record-type-badge";

                type.textContent =
                    (document.documentType || "OTHER").replaceAll("_", " ");
                const pet = document.createElement("p");
                pet.textContent = `Pet: ${document.petName || "Unknown"}`;

                const uploaded = document.createElement("p");
                const uploadedDate = new Date(document.uploadedAt);

                uploaded.textContent = Number.isNaN(uploadedDate.getTime())
                    ? "Upload date unavailable"
                    : `Uploaded: ${uploadedDate.toLocaleDateString("en-IN")}`;


                const status = document.createElement("p");
                status.className = "record-processing-status";

                const processingStatus = document.processingStatus || "PENDING";

                status.textContent =
                    `Processing: ${processingStatus.replaceAll("_", " ")}`;

                status.dataset.status = processingStatus.toLowerCase();


                card.append(heading, type, pet, uploaded, status);
                dashboardRecordsElement.appendChild(card);
            });

            const viewAllLink = document.createElement("a");
            viewAllLink.href = "medical-records.html";
            viewAllLink.className = "btn btn-secondary";
            viewAllLink.textContent = "View all medical records";

            dashboardRecordsElement.appendChild(viewAllLink);

        } catch (error) {
            if (error.message === "SESSION_EXPIRED") {
                sessionStorage.removeItem("vetplatform_token");
                sessionStorage.removeItem("vetplatform_role");
                sessionStorage.removeItem("vetplatform_email");
                sessionStorage.removeItem("vetplatform_user_id");

                window.location.replace("login.html");
                return;
            }

            console.error("Could not load dashboard records:", error);

            recordCountElement.textContent = "—";
            dashboardRecordsElement.textContent =
                "Medical records are currently unavailable.";
        }
    }

    loadDashboardRecords();

    // 6. Logout
    const logoutButton = document.getElementById("logout-button");

    if (logoutButton) {
        logoutButton.addEventListener("click", function () {
            sessionStorage.removeItem("vetplatform_token");
            sessionStorage.removeItem("vetplatform_role");
            sessionStorage.removeItem("vetplatform_email");
            sessionStorage.removeItem("vetplatform_user_id");

            window.location.replace("login.html");
        });
    }
});

