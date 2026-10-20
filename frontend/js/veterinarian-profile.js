
document.addEventListener("DOMContentLoaded", function () {
    const profileContainer =
        document.getElementById("veterinarian-profile");
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

    const veterinarians = {
        "demo-101": {
            name: "Dr. Aditi Sharma",
            specialization: "General Practice",
            experience: 6,
            fee: 500,
            rating: 4.8,
            reviewCount: 42,
            avatar: "👩‍⚕️"
        },
        "demo-102": {
            name: "Dr. Rahul Mehta",
            specialization: "Dermatology",
            experience: 8,
            fee: 700,
            rating: 4.7,
            reviewCount: 35,
            avatar: "👨‍⚕️"
        },
        "demo-103": {
            name: "Dr. Neha Verma",
            specialization: "Surgery",
            experience: 10,
            fee: 900,
            rating: 4.9,
            reviewCount: 58,
            avatar: "👩‍⚕️"
        },
        "demo-104": {
            name: "Dr. Arjun Patel",
            specialization: "Dentistry",
            experience: 5,
            fee: 600,
            rating: 4.6,
            reviewCount: 27,
            avatar: "👨‍⚕️"
        },
        "demo-105": {
            name: "Dr. Priya Nair",
            specialization: "General Practice",
            experience: 7,
            fee: 550,
            rating: 4.8,
            reviewCount: 46,
            avatar: "👩‍⚕️"
        },
        "demo-106": {
            name: "Dr. Karan Singh",
            specialization: "Dermatology",
            experience: 4,
            fee: 650,
            rating: 4.5,
            reviewCount: 19,
            avatar: "👨‍⚕️"
        }
    };

    const params = new URLSearchParams(window.location.search);
    const veterinarianId = params.get("id");
    const vet = veterinarians[veterinarianId];

    const STORAGE_KEY =
        `vetplatform_demo_reviews_${veterinarianId || "unknown"}`;

    const sampleReviews = [
        {
            id: "sample-review-1",
            title: "Clear and helpful explanation",
            comment: "The sample profile demonstrates how customer feedback will appear here.",
            rating: 5,
            createdAt: "2026-09-10T10:00:00.000Z",
            demo: true
        },
        {
            id: "sample-review-2",
            title: "Helpful consultation experience",
            comment: "This is placeholder review content for the frontend preview, not a real customer testimonial.",
            rating: 4,
            createdAt: "2026-09-05T10:00:00.000Z",
            demo: true
        }
    ];

    function showMessage(text, isError) {
        messageBox.textContent = text;
        messageBox.classList.remove("hidden");
        messageBox.classList.toggle("error", Boolean(isError));
    }

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

    function loadReviews() {
        try {
            const saved = localStorage.getItem(STORAGE_KEY);

            if (saved === null) {
                return sampleReviews.map(review => ({ ...review }));
            }

            const parsed = JSON.parse(saved);

            if (!Array.isArray(parsed)) {
                return [];
            }

            return parsed.filter(review =>
                review &&
                typeof review.id === "string" &&
                typeof review.title === "string" &&
                typeof review.comment === "string" &&
                Number.isInteger(review.rating) &&
                review.rating >= 1 &&
                review.rating <= 5 &&
                typeof review.createdAt === "string"
            );
        } catch (error) {
            console.error("Could not load reviews:", error);
            return [];
        }
    }

    let reviews = loadReviews();

    function saveReviews() {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(reviews));
            return true;
        } catch (error) {
            console.error("Could not save reviews:", error);
            return false;
        }
    }

    function renderProfile() {
        if (!vet) {
            profileContainer.replaceChildren();

            const heading = createElement(
                "h2",
                "",
                "Veterinarian profile not found"
            );

            const description = createElement(
                "p",
                "",
                "Please return to the veterinarian listing and select a sample profile."
            );

            const link = createElement(
                "a",
                "btn btn-primary",
                "Browse veterinarians"
            );
            link.href = "veterinarians.html";

            profileContainer.append(heading, description, link);
            reviewForm.classList.add("hidden");
            return;
        }

        document.title = `${vet.name} | VetPlatform`;

        const header = createElement("div", "vet-profile-header");

        const avatar = createElement(
            "div",
            "vet-profile-avatar",
            vet.avatar
        );
        avatar.setAttribute("aria-hidden", "true");

        const information = createElement(
            "div",
            "vet-profile-information"
        );

        information.append(
            createElement("h2", "", vet.name),
            createElement(
                "p",
                "",
                `${vet.specialization} · ${vet.experience} years of sample experience`
            )
        );

        const badges = createElement("div", "vet-profile-badges");
        badges.append(
            createElement(
                "span",
                "vet-profile-badge",
                vet.specialization
            ),
            createElement(
                "span",
                "vet-profile-badge vet-profile-demo-badge",
                "Demo profile · Not verified"
            )
        );

        information.append(badges);
        header.append(avatar, information);

        const facts = createElement("div", "vet-profile-facts");

        const ratingFact = createElement("div", "vet-profile-fact");
        ratingFact.append(
            createElement(
                "span",
                "vet-profile-fact-label",
                "Sample listing rating"
            ),
            createElement(
                "span",
                "vet-profile-fact-value",
                `★ ${vet.rating.toFixed(1)} (${vet.reviewCount} sample ratings)`
            )
        );

        const experienceFact = createElement("div", "vet-profile-fact");
        experienceFact.append(
            createElement(
                "span",
                "vet-profile-fact-label",
                "Sample experience"
            ),
            createElement(
                "span",
                "vet-profile-fact-value",
                `${vet.experience} years`
            )
        );

        const feeFact = createElement("div", "vet-profile-fact");
        feeFact.append(
            createElement(
                "span",
                "vet-profile-fact-label",
                "Sample consultation fee"
            ),
            createElement(
                "span",
                "vet-profile-fact-value",
                `₹${vet.fee}`
            )
        );

        facts.append(ratingFact, experienceFact, feeFact);

        const actions = createElement("div", "vet-profile-actions");

        const bookLink = createElement(
            "a",
            "btn btn-primary",
            "Book an appointment"
        );
        bookLink.href =
            `appointments.html?veterinarianId=${encodeURIComponent(veterinarianId)}` +
            `&veterinarianName=${encodeURIComponent(vet.name)}`;

        const backLink = createElement(
            "a",
            "btn btn-secondary",
            "← Back to veterinarians"
        );
        backLink.href = "veterinarians.html";

        actions.append(bookLink, backLink);

        profileContainer.replaceChildren(header, facts, actions);
    }

    function formatDate(value) {
        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return "Date unavailable";
        }

        return date.toLocaleDateString(undefined, {
            day: "numeric",
            month: "short",
            year: "numeric"
        });
    }

    function renderReviews() {
        reviewList.replaceChildren();

        if (!vet) {
            reviewSummary.replaceChildren(
                createElement("span", "", "No profile selected")
            );
            reviewCount.textContent = "0 reviews";
            return;
        }

        const sampleRatingTotal = vet.rating * vet.reviewCount;
        const userReviews = reviews.filter(review => !review.demo);
        const userRatingTotal = userReviews.reduce(
            (total, review) => total + review.rating,
            0
        );

        const totalRatingCount = vet.reviewCount + userReviews.length;
        const totalRating = totalRatingCount > 0
            ? (sampleRatingTotal + userRatingTotal) / totalRatingCount
            : 0;

        reviewSummary.replaceChildren(
            createElement(
                "span",
                "review-summary-score",
                `★ ${totalRating.toFixed(1)}`
            ),
            createElement(
                "span",
                "",
                `${totalRatingCount} sample and demo ratings`
            )
        );

        reviewCount.textContent =
            `${reviews.length} displayed review${reviews.length === 1 ? "" : "s"}`;

        if (reviews.length === 0) {
            reviewList.append(
                createElement(
                    "p",
                    "review-empty",
                    "No reviews yet. You can submit the first demo review."
                )
            );
            return;
        }

        const sortedReviews = [...reviews].sort(
            (a, b) =>
                new Date(b.createdAt).getTime() -
                new Date(a.createdAt).getTime()
        );

        sortedReviews.forEach(function (review) {
            const item = createElement("article", "review-item");

            const header = createElement("div", "review-item-header");
            const titleBlock = document.createElement("div");

            titleBlock.append(
                createElement("h4", "", review.title),
                createElement(
                    "span",
                    "review-item-date",
                    formatDate(review.createdAt)
                )
            );

            const stars = createElement(
                "span",
                "review-item-stars",
                `${"★".repeat(review.rating)}${"☆".repeat(5 - review.rating)}`
            );
            stars.setAttribute(
                "aria-label",
                `${review.rating} out of 5 stars`
            );

            header.append(titleBlock, stars);

            const comment = createElement(
                "p",
                "review-item-comment",
                review.comment
            );

            item.append(header, comment);

            item.append(
                createElement(
                    "span",
                    "review-demo-label",
                    "DEMO REVIEW · Not a verified testimonial"
                )
            );

            reviewList.append(item);
        });
    }

    reviewForm.addEventListener("submit", function (event) {
        event.preventDefault();

        if (!vet) {
            showMessage(
                "Select a veterinarian from the listing before submitting a review.",
                true
            );
            return;
        }

        const formData = new FormData(reviewForm);
        const rating = Number(formData.get("rating"));
        const title = String(formData.get("title") || "").trim();
        const comment = String(formData.get("comment") || "").trim();

        if (
            !Number.isInteger(rating) ||
            rating < 1 ||
            rating > 5
        ) {
            showMessage("Please select a rating from 1 to 5 stars.", true);
            return;
        }

        if (!title || comment.length < 10 || comment.length > 1000) {
            showMessage(
                "Enter a title and a review between 10 and 1000 characters.",
                true
            );
            return;
        }

        const previousReviews = reviews;

        reviews = [
            ...reviews,
            {
                id: `review-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
                title,
                comment,
                rating,
                createdAt: new Date().toISOString(),
                demo: true
            }
        ];

        if (!saveReviews()) {
            reviews = previousReviews;
            showMessage(
                "Could not save your review in this browser. Please try again.",
                true
            );
            return;
        }

        reviewForm.reset();
        showMessage(
            "Your demo review was saved in this browser. It has not been sent to a veterinarian.",
            false
        );

        renderReviews();
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

    renderProfile();
    renderReviews();
});
