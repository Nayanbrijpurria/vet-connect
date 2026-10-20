
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

    // Demonstration data only. These are not real or verified veterinarians.
    const veterinarians = [
        {
            id: "demo-101",
            name: "Dr. Aditi Sharma",
            specialization: "General Practice",
            experience: 6,
            fee: 500,
            rating: 4.8,
            reviewCount: 42,
            avatar: "👩‍⚕️"
        },
        {
            id: "demo-102",
            name: "Dr. Rahul Mehta",
            specialization: "Dermatology",
            experience: 8,
            fee: 700,
            rating: 4.7,
            reviewCount: 35,
            avatar: "👨‍⚕️"
        },
        {
            id: "demo-103",
            name: "Dr. Neha Verma",
            specialization: "Surgery",
            experience: 10,
            fee: 900,
            rating: 4.9,
            reviewCount: 58,
            avatar: "👩‍⚕️"
        },
        {
            id: "demo-104",
            name: "Dr. Arjun Patel",
            specialization: "Dentistry",
            experience: 5,
            fee: 600,
            rating: 4.6,
            reviewCount: 27,
            avatar: "👨‍⚕️"
        },
        {
            id: "demo-105",
            name: "Dr. Priya Nair",
            specialization: "General Practice",
            experience: 7,
            fee: 550,
            rating: 4.8,
            reviewCount: 46,
            avatar: "👩‍⚕️"
        },
        {
            id: "demo-106",
            name: "Dr. Karan Singh",
            specialization: "Dermatology",
            experience: 4,
            fee: 650,
            rating: 4.5,
            reviewCount: 19,
            avatar: "👨‍⚕️"
        }
    ];

    function createElement(tag, className, text) {
        const element = document.createElement(tag);

        if (className) {
            element.className = className;
        }

        if (text !== undefined) {
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
        const avatar = createElement("div", "vet-avatar", vet.avatar);
        avatar.setAttribute("aria-hidden", "true");

        const heading = createElement("div", "vet-card-heading");
        heading.append(
            createElement("h3", "", vet.name),
            createElement("span", "vet-specialization", vet.specialization),
            createElement("span", "vet-demo-label", "DEMO PROFILE")
        );

        top.append(avatar, heading);

        const details = createElement("div", "vet-card-details");
        details.append(
            createDetail(
                "Experience",
                `${vet.experience} years (sample)`
            ),
            createDetail(
                "Consultation fee",
                `₹${vet.fee} (sample)`
            )
        );

        const ratingDetail = createElement("div", "vet-detail");
        ratingDetail.append(
            createElement("span", "vet-detail-label", "Sample rating")
        );

        const rating = createElement(
            "span",
            "vet-rating",
            `★ ${vet.rating.toFixed(1)} `
        );

        rating.append(
            createElement(
                "span",
                "vet-rating-note",
                `(${vet.reviewCount} sample reviews)`
            )
        );

        ratingDetail.append(rating);

        const availabilityDetail = createDetail(
            "Profile status",
            "Demonstration only"
        );

        details.append(ratingDetail, availabilityDetail);

        const footer = createElement("div", "vet-card-footer");

        const price = createElement("div", "vet-demo-price", `₹${vet.fee}`);
        price.append(
            createElement("small", "", "Sample fee · Not a real quote")
        );

        const profileLink = createElement("a", "btn btn-primary", "View profile →");
        profileLink.href =
            `veterinarian-profile.html?id=${encodeURIComponent(vet.id)}`;
        profileLink.setAttribute(
            "aria-label",
            `View demo profile for ${vet.name}`
        );

        footer.append(price, profileLink);
        card.append(top, details, footer);

        return card;
    }

    function renderVeterinarians() {
        const searchTerm = searchInput.value.trim().toLowerCase();
        const selectedSpecialization = specializationSelect.value;

        const filteredVeterinarians = veterinarians.filter(function (vet) {
            const matchesSearch =
                vet.name.toLowerCase().includes(searchTerm) ||
                vet.specialization.toLowerCase().includes(searchTerm);

            const matchesSpecialization =
                selectedSpecialization === "all" ||
                vet.specialization === selectedSpecialization;

            return matchesSearch && matchesSpecialization;
        });

        veterinarianList.replaceChildren();

        resultsSummary.textContent =
            `${filteredVeterinarians.length} sample profile` +
            `${filteredVeterinarians.length === 1 ? "" : "s"} found`;

        if (filteredVeterinarians.length === 0) {
            const empty = createElement("div", "vet-empty-state");

            empty.append(
                createElement("h3", "", "No matching veterinarians"),
                createElement(
                    "p",
                    "",
                    "Try another name or specialization, or reset your filters."
                )
            );

            veterinarianList.append(empty);
            return;
        }

        filteredVeterinarians.forEach(function (vet) {
            veterinarianList.append(createVeterinarianCard(vet));
        });
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

    renderVeterinarians();
});
