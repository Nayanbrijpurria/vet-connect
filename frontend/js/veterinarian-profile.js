
document.addEventListener("DOMContentLoaded", function () {
    const profileContainer = document.getElementById("veterinarian-profile");
    const reviewForm = document.getElementById("review-form");
    const reviewList = document.getElementById("review-list");
    const reviewSummary = document.getElementById("review-summary");
    const reviewCount = document.getElementById("review-count");
    const messageBox = document.getElementById("profile-message");
    const sidebar = document.getElementById("sidebar");
    const sidebarToggle = document.getElementById("sidebar-toggle");

    if (
        !profileContainer ||
        !reviewForm ||
        !reviewList ||
        !reviewSummary ||
        !reviewCount ||
        !messageBox
    ) {
        return;
    }

    const params = new URLSearchParams(window.location.search);
    const veterinarianId = params.get("id");

    let veterinarian = null;

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

    function showMessage(text, isError = false) {
        messageBox.textContent = text;
        messageBox.classList.remove("hidden");
        messageBox.classList.toggle("error", isError);
    }

    function formatFee(value) {
        return `₹${Number(value).toLocaleString("en-IN")}`;
    }

    function renderNotFound(title, description) {
        profileContainer.replaceChildren();

        profileContainer.append(
            createElement("h2", "", title),
            createElement("p", "", description)
        );

        const backLink = createElement(
            "a",
            "btn btn-primary",
            "Browse veterinarians"
        );

        backLink.href = "veterinarians.html";
        profileContainer.append(backLink);

        reviewForm.classList.add("hidden");
    }

    function renderProfile() {
        if (!veterinarian) {
            return;
        }

        document.title = `${veterinarian.fullName} | VetPlatform`;

        const header = createElement("div", "vet-profile-header");

        const avatar = createElement(
            "div",
            "vet-profile-avatar",
            "🩺"
        );
        avatar.setAttribute("aria-hidden", "true");

        const information = createElement(
            "div",
            "vet-profile-information"
        );

        information.append(
            createElement("h2", "", veterinarian.fullName),
            createElement(
                "p",
                "",
                `${veterinarian.qualification} · ${
                    veterinarian.experienceYears
                } ${
                    veterinarian.experienceYears === 1 ? "year" : "years"
                } of experience`
            )
        );

        const badges = createElement("div", "vet-profile-badges");

        badges.append(
            createElement(
                "span",
                "vet-profile-badge",
                veterinarian.qualification
            ),
            createElement(
                "span",
                "vet-profile-badge",
                "Approved veterinarian"
            )
        );

        information.append(badges);
        header.append(avatar, information);

        const facts = createElement("div", "vet-profile-facts");

        const experienceFact = createElement("div", "vet-profile-fact");
        experienceFact.append(
            createElement("span", "vet-profile-fact-label", "Experience"),
            createElement(
                "span",
                "vet-profile-fact-value",
                `${veterinarian.experienceYears} ${
                    veterinarian.experienceYears === 1 ? "year" : "years"
                }`
            )
        );

        const feeFact = createElement("div", "vet-profile-fact");
        feeFact.append(
            createElement(
                "span",
                "vet-profile-fact-label",
                "Consultation fee"
            ),
            createElement(
                "span",
                "vet-profile-fact-value",
                formatFee(veterinarian.consultationFee)
            )
        );

        facts.append(experienceFact, feeFact);

        const actions = createElement("div", "vet-profile-actions");

        const bookLink = createElement(
            "a",
            "btn btn-primary",
            "Book an appointment"
        );

        bookLink.href =
            `appointments.html?veterinarianId=${encodeURIComponent(
                veterinarian.userId
            )}`;

        const backLink = createElement(
            "a",
            "btn btn-secondary",
            "← Back to veterinarians"
        );

        backLink.href = "veterinarians.html";

        actions.append(bookLink, backLink);

        profileContainer.replaceChildren(header, facts, actions);
    }

    function renderReviewsUnavailable() {
        reviewSummary.replaceChildren(
            createElement("span", "", "Ratings not available yet")
        );

        reviewCount.textContent = "0 verified reviews";

        reviewList.replaceChildren(
            createElement(
                "p",
                "review-empty",
                "Verified customer reviews will appear here when review functionality is available."
            )
        );

        reviewForm.classList.add("hidden");
    }

    async function loadProfile() {
        if (!veterinarianId || !/^\d+$/.test(veterinarianId)) {
            renderNotFound(
                "No veterinarian selected",
                "Please return to the directory and select an approved veterinarian."
            );
            renderReviewsUnavailable();
            return;
        }

        profileContainer.replaceChildren(
            createElement("p", "", "Loading veterinarian profile...")
        );

        try {
            const response = await fetch(
                `${API_BASE_URL}/api/vets/approved`
            );

            if (!response.ok) {
                throw new Error(
                    `Unable to load veterinarian information (HTTP ${response.status}).`
                );
            }

            const veterinarians = await response.json();

            if (!Array.isArray(veterinarians)) {
                throw new Error("The server returned an unexpected response.");
            }

            veterinarian = veterinarians.find(
                vet => String(vet.userId) === veterinarianId
            );

            if (!veterinarian) {
                renderNotFound(
                    "Veterinarian profile not found",
                    "This veterinarian may not be approved or may no longer be listed."
                );
                renderReviewsUnavailable();
                return;
            }

            renderProfile();
            renderReviewsUnavailable();
        } catch (error) {
            console.error("Failed to load veterinarian profile:", error);

            renderNotFound(
                "Unable to load profile",
                "We couldn't retrieve this veterinarian's information. Please try again later."
            );

            renderReviewsUnavailable();
        }
    }

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

    reviewForm.addEventListener("submit", function (event) {
        event.preventDefault();

        showMessage(
            "Verified reviews are not available yet. Review submission will be enabled after the backend is implemented.",
            true
        );
    });

    renderReviewsUnavailable();
    loadProfile();
});
