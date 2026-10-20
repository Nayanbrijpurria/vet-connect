
document.addEventListener("DOMContentLoaded", function () {
    const petList = document.getElementById("pet-list");
    const petFormSection = document.getElementById("pet-form-section");
    const petForm = document.getElementById("pet-form");
    const petSearch = document.getElementById("pet-search");
    const petSummary = document.getElementById("pet-summary");
    const petMessage = document.getElementById("pet-message");

    const openFormButton = document.getElementById("open-pet-form");
    const emptyAddButton = document.getElementById("empty-add-pet");
    const cancelFormButton = document.getElementById("cancel-pet-form");

    const sidebar = document.getElementById("sidebar");
    const sidebarToggle = document.getElementById("sidebar-toggle");

    const PET_STORAGE_KEY = "vetplatform_demo_pets";

    function loadPets() {
        try {
            const savedPets = JSON.parse(
                localStorage.getItem(PET_STORAGE_KEY) || "[]"
            );

            if (!Array.isArray(savedPets)) {
                return [];
            }

            return savedPets.filter(pet =>
                pet &&
                Number.isInteger(pet.id) &&
                typeof pet.name === "string" &&
                typeof pet.species === "string"
            );
        } catch (error) {
            console.error("Could not load saved pets:", error);
            return [];
        }
    }

    function savePets() {
        try {
            localStorage.setItem(
                PET_STORAGE_KEY,
                JSON.stringify(pets)
            );
            return true;
        } catch (error) {
            console.error("Could not save pets:", error);
            return false;
        }
    }

    let pets = loadPets();

    let nextId = pets.reduce(
        (maximumId, pet) => Math.max(maximumId, pet.id),
        0
    ) + 1;

    function showMessage(message) {
        petMessage.textContent = message;
        petMessage.classList.remove("hidden");
    }

    function hideMessage() {
        petMessage.textContent = "";
        petMessage.classList.add("hidden");
    }

    function openPetForm() {
        hideMessage();
        petFormSection.hidden = false;
        petFormSection.scrollIntoView({
            behavior: "smooth",
            block: "start"
        });

        document.getElementById("pet-name").focus();
    }

    function closePetForm() {
        petFormSection.hidden = true;
        petForm.reset();
    }

    function getPetEmoji(species) {
        const emojis = {
            Dog: "🐕",
            Cat: "🐈",
            Bird: "🐦",
            Rabbit: "🐇",
            Other: "🐾"
        };

        return emojis[species] || "🐾";
    }

    function formatDate(value) {
        if (!value) {
            return "Not provided";
        }

        const parts = value.split("-");

        if (parts.length !== 3) {
            return "Not provided";
        }

        return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }

    function createDetail(label, value) {
        const row = document.createElement("div");
        row.className = "pet-detail-row";

        const labelElement = document.createElement("span");
        labelElement.className = "pet-detail-label";
        labelElement.textContent = label;

        const valueElement = document.createElement("span");
        valueElement.className = "pet-detail-value";
        valueElement.textContent = value || "Not provided";

        row.append(labelElement, valueElement);

        return row;
    }

    function createPetCard(pet) {
        const card = document.createElement("article");
        card.className = "pet-card";

        const top = document.createElement("div");
        top.className = "pet-card-top";

        const avatar = document.createElement("div");
        avatar.className = "pet-card-avatar";
        avatar.setAttribute("aria-hidden", "true");
        avatar.textContent = getPetEmoji(pet.species);

        const headingContainer = document.createElement("div");

        const heading = document.createElement("h3");
        heading.textContent = pet.name;

        const species = document.createElement("p");
        species.textContent = pet.species;

        headingContainer.append(heading, species);
        top.append(avatar, headingContainer);

        const body = document.createElement("div");
        body.className = "pet-card-body";

        body.append(
            createDetail("Breed", pet.breed || "Not provided"),
            createDetail("Gender", pet.gender || "Not provided"),
            createDetail("Date of birth", formatDate(pet.birthDate)),
            createDetail(
                "Weight",
                pet.weight ? `${pet.weight} kg` : "Not provided"
            )
        );

        const actions = document.createElement("div");
        actions.className = "pet-card-actions";

        const recordsLink = document.createElement("a");
        recordsLink.className = "btn btn-secondary";
        recordsLink.href =
            `medical-records.html?petId=${encodeURIComponent(pet.id)}`;
        recordsLink.textContent = "Medical records";

        const removeButton = document.createElement("button");
        removeButton.type = "button";
        removeButton.className = "btn btn-outline";
        removeButton.textContent = "Remove";
        removeButton.setAttribute(
            "aria-label",
            `Remove ${pet.name} from this temporary list`
        );

        removeButton.addEventListener("click", function () {
            const confirmed = window.confirm(
                `Remove ${pet.name} from this temporary list?`
            );

            if (!confirmed) {
                return;
            }

            const previousPets = pets;

            pets = pets.filter(function (item) {
                return item.id !== pet.id;
            });

            if (!savePets()) {
                pets = previousPets;
                showMessage("Could not remove the pet. Browser storage may be full.");
                return;
            }

            renderPets();
            showMessage(`${pet.name} was removed from your saved demo list.`);
        });

        actions.append(recordsLink, removeButton);
        body.append(actions);
        card.append(top, body);

        return card;
    }

    function renderPets() {
        const searchTerm = petSearch.value.trim().toLowerCase();

        const filteredPets = pets.filter(function (pet) {
            return (
                pet.name.toLowerCase().includes(searchTerm) ||
                pet.species.toLowerCase().includes(searchTerm) ||
                pet.breed.toLowerCase().includes(searchTerm)
            );
        });

        petList.replaceChildren();

        petSummary.textContent =
            `${pets.length} ${pets.length === 1 ? "pet" : "pets"} saved in this browser`;

        if (filteredPets.length === 0) {
            const emptyState = document.createElement("div");
            emptyState.className = "empty-state";

            const icon = document.createElement("div");
            icon.className = "empty-state-icon";
            icon.textContent = "🐾";

            const heading = document.createElement("h3");
            const description = document.createElement("p");

            if (pets.length === 0) {
                heading.textContent = "No pets added yet";
                description.textContent =
                    "Add your first pet to start organizing their information.";

                const button = document.createElement("button");
                button.type = "button";
                button.className = "btn btn-primary";
                button.textContent = "+ Add your first pet";
                button.addEventListener("click", openPetForm);

                emptyState.append(icon, heading, description, button);
            } else {
                heading.textContent = "No matching pets";
                description.textContent =
                    "Try another name, species, or breed.";

                emptyState.append(icon, heading, description);
            }

            petList.append(emptyState);
            return;
        }

        filteredPets.forEach(function (pet) {
            petList.append(createPetCard(pet));
        });
    }

    openFormButton.addEventListener("click", openPetForm);
    emptyAddButton.addEventListener("click", openPetForm);
    cancelFormButton.addEventListener("click", closePetForm);
    petSearch.addEventListener("input", renderPets);

    petForm.addEventListener("submit", function (event) {
        event.preventDefault();
        hideMessage();

        const name = document.getElementById("pet-name").value.trim();
        const species = document.getElementById("pet-species").value;
        const breed = document.getElementById("pet-breed").value.trim();
        const gender = document.getElementById("pet-gender").value;
        const birthDate =
            document.getElementById("pet-birth-date").value;
        const weight =
            document.getElementById("pet-weight").value.trim();
        const notes = document.getElementById("pet-notes").value.trim();

        if (!name || !species) {
            showMessage("Please enter a pet name and select a species.");
            return;
        }

        if (birthDate) {
            const selectedDate = new Date(`${birthDate}T00:00:00`);
            const today = new Date();
            today.setHours(23, 59, 59, 999);

            if (Number.isNaN(selectedDate.getTime()) ||
                selectedDate > today) {
                showMessage("Please enter a valid date of birth.");
                return;
            }
        }

        const pet = {
            id: nextId++,
            name,
            species,
            breed,
            gender,
            birthDate,
            weight,
            notes
        };

        pets.push(pet);

        if (!savePets()) {
            pets.pop();
            showMessage(
                "Could not save this pet. Please check your browser storage."
            );
            return;
        }

        closePetForm();
        petSearch.value = "";
        renderPets();

        showMessage(
            `${name} was added successfully and saved in this browser.`
        );

        window.scrollTo({
            top: 0,
            behavior: "smooth"
        });
    });

    if (sidebar && sidebarToggle) {
        sidebarToggle.addEventListener("click", function () {
            const isOpen = sidebar.classList.toggle("open");

            sidebarToggle.setAttribute(
                "aria-expanded",
                String(isOpen)
            );
        });

        document.addEventListener("click", function (event) {
            if (
                window.innerWidth <= 850 &&
                sidebar.classList.contains("open") &&
                !sidebar.contains(event.target) &&
                !sidebarToggle.contains(event.target)
            ) {
                sidebar.classList.remove("open");
                sidebarToggle.setAttribute("aria-expanded", "false");
            }
        });
    }

    renderPets();
});
