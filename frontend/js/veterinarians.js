
document.addEventListener("DOMContentLoaded", function () {
    const veterinarianList = document.getElementById("veterinarian-list");
    const searchInput = document.getElementById("vet-search");
    const specializationSelect = document.getElementById("vet-specialization");
    const resetButton = document.getElementById("reset-vet-filters");
    const resultsSummary = document.getElementById("vet-results-summary");
    const sidebar = document.getElementById("sidebar");
    const sidebarToggle = document.getElementById("sidebar-toggle");

    if (
        !veterinarianList ||
        !searchInput ||
        !specializationSelect ||
        !resetButton ||
        !resultsSummary
    ) {
        return;
    }

    let veterinarians = [];

    function createElement(tag, className, text) {
        const element = document.createElement(tag);

        if (className) {
            element.className = className;
        }

        if (text !== undefined && text !== null) {
            element.textContent = text;
        }

        return element;
    }

    function createDetail(label, value) {
        const wrapper = createElement("div", "vet-detail");

        wrapper.append(
            createElement("span", "vet-detail-label", label),
            createElement("span", "vet-detail-value", value)
        );

        return wrapper;
    }

    function createVeterinarianCard(vet) {
        const card = createElement("article", "vet-card");
        const top = createElement("div", "vet-card-top");

        const avatar = createElement("div", "vet-avatar", "🩺");
        avatar.setAttribute("aria-hidden", "true");

        const heading = createElement("div", "vet-card-heading");

        heading.append(
            createElement("h3", "", vet.fullName),
            createElement("span", "vet-specialization", vet.qualification),
            createElement("span", "vet-demo-label", "APPROVED VETERINARIAN")
        );

        top.append(avatar, heading);

        const details = createElement("div", "vet-card-details");

        details.append(
            createDetail(
                "Experience",
                `${vet.experienceYears} ${
                    vet.experienceYears === 1 ? "year" : "years"
                }`
            ),
            createDetail(
                "Consultation fee",
                `₹${Number(vet.consultationFee).toLocaleString("en-IN")}`
            ),
            createDetail("Profile status", "Approved")
        );

        const footer = createElement("div", "vet-card-footer");

        const price = createElement(
            "div",
            "vet-demo-price",
            `₹${Number(vet.consultationFee).toLocaleString("en-IN")}`
        );

        price.append(createElement("small", "", "Consultation fee"));

        const profileLink = createElement(
            "a",
            "btn btn-primary",
            "View profile →"
        );

        profileLink.href =
            `veterinarian-profile.html?id=${encodeURIComponent(vet.userId)}`;

        footer.append(price, profileLink);
        card.append(top, details, footer);

        return card;
    }

    function renderVeterinarians() {
        const searchTerm = searchInput.value.trim().toLowerCase();
        const selectedQualification = specializationSelect.value;

        const filtered = veterinarians.filter(function (vet) {
            const searchableText =
                `${vet.fullName} ${vet.qualification}`.toLowerCase();

            const matchesSearch = searchableText.includes(searchTerm);

            const matchesQualification =
                selectedQualification === "all" ||
                vet.qualification === selectedQualification;

            return matchesSearch && matchesQualification;
        });

        veterinarianList.replaceChildren();

        resultsSummary.textContent =
            `${filtered.length} approved veterinarian${
                filtered.length === 1 ? "" : "s"
            } found`;

        if (filtered.length === 0) {
            const empty = createElement("div", "vet-empty-state");

            empty.append(
                createElement("h3", "", "No veterinarians found"),
                createElement(
                    "p",
                    "",
                    veterinarians.length === 0
                        ? "No approved veterinarian profiles are available yet."
                        : "Try another search or reset your filters."
                )
            );

            veterinarianList.append(empty);
            return;
        }

        filtered.forEach(function (vet) {
            veterinarianList.append(createVeterinarianCard(vet));
        });
    }

    function populateQualificationFilter() {
        const currentValue = specializationSelect.value;

        specializationSelect.replaceChildren();

        specializationSelect.append(
            new Option("All qualifications", "all")
        );

        const qualifications = [
            ...new Set(
                veterinarians
                    .map(vet => vet.qualification)
                    .filter(Boolean)
            )
        ].sort((a, b) => a.localeCompare(b));

        qualifications.forEach(function (qualification) {
            specializationSelect.append(
                new Option(qualification, qualification)
            );
        });

        specializationSelect.value =
            qualifications.includes(currentValue) ? currentValue : "all";
    }

    async function loadVeterinarians() {
        veterinarianList.replaceChildren();

        veterinarianList.append(
            createElement(
                "p",
                "vet-loading",
                "Loading approved veterinarians..."
            )
        );

        resultsSummary.textContent = "Loading veterinarian directory...";

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/vets/approved`
            );

            if (!response.ok) {
                throw new Error(
                    `Unable to load veterinarians (HTTP ${response.status}).`
                );
            }

            const data = await response.json();

            if (!Array.isArray(data)) {
                throw new Error("The server returned an unexpected response.");
            }

            veterinarians = data.filter(function (vet) {
                return (
                    Number.isInteger(vet.userId) &&
                    typeof vet.fullName === "string" &&
                    typeof vet.qualification === "string" &&
                    Number.isFinite(Number(vet.experienceYears)) &&
                    Number.isFinite(Number(vet.consultationFee))
                );
            });

            populateQualificationFilter();
            renderVeterinarians();
        } catch (error) {
            console.error("Failed to load veterinarian directory:", error);

            veterinarianList.replaceChildren();

            const errorState = createElement("div", "vet-empty-state");

            errorState.append(
                createElement("h3", "", "Unable to load veterinarians"),
                createElement(
                    "p",
                    "",
                    "The veterinarian directory could not be loaded. Please try again later."
                )
            );

            veterinarianList.append(errorState);
            resultsSummary.textContent = "Veterinarian directory unavailable";
        }
    }

    searchInput.addEventListener("input", renderVeterinarians);
    specializationSelect.addEventListener("change", renderVeterinarians);

    resetButton.addEventListener("click", function () {
        searchInput.value = "";
        specializationSelect.value = "all";
        renderVeterinarians();
        searchInput.focus();
    });

    if (sidebar && sidebarToggle) {
        sidebarToggle.addEventListener("click", function () {
            const isOpen = sidebar.classList.toggle("open");
            sidebarToggle.setAttribute("aria-expanded", String(isOpen));
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

    loadVeterinarians();
});
