
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
    const welcomeHeading = document.querySelector(".welcome-banner h2");

    if (welcomeHeading) {
        welcomeHeading.textContent = "Welcome back! 🐾";
    }

    // 5. Load real pet information from the backend
    const petCountElement = document.getElementById("pet-count");
    const dashboardPetsElement = document.getElementById("dashboard-pets");

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
                species.textContent = pet.species;

                const breed = document.createElement("p");
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
