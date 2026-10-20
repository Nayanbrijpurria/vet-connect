
document.addEventListener("DOMContentLoaded", function () {
    const button = document.getElementById("notification-button");
    const panel = document.getElementById("notification-panel");
    const countBadge = document.getElementById("notification-count");
    const summary = document.getElementById("notification-summary");
    const list = document.getElementById("notification-list");
    const markAllReadButton = document.getElementById("mark-all-read");
    const clearButton = document.getElementById("clear-notifications");

    if (
        !button ||
        !panel ||
        !countBadge ||
        !summary ||
        !list ||
        !markAllReadButton ||
        !clearButton
    ) {
        return;
    }

    const STORAGE_KEY = "vetplatform_demo_notifications";

    const starterNotifications = [
        {
            id: "welcome",
            title: "Welcome to VetPlatform",
            message: "Keep your pets' profiles, appointments, and medical records organized in one place.",
            type: "general",
            createdAt: new Date().toISOString(),
            read: false
        },
        {
            id: "appointment-reminder",
            title: "Appointment reminders",
            message: "Your upcoming appointment reminders will appear here when appointment data is connected.",
            type: "appointment",
            createdAt: new Date().toISOString(),
            read: false
        },
        {
            id: "medical-records",
            title: "Medical records",
            message: "You can access your pets' medical documents from the Medical Records page.",
            type: "medical",
            createdAt: new Date().toISOString(),
            read: true
        }
    ];

    function loadNotifications() {
        try {
            const saved = localStorage.getItem(STORAGE_KEY);

            if (saved === null) {
                localStorage.setItem(
                    STORAGE_KEY,
                    JSON.stringify(starterNotifications)
                );

                return starterNotifications.map(item => ({ ...item }));
            }

            const parsed = JSON.parse(saved);

            if (!Array.isArray(parsed)) {
                return [];
            }

            return parsed.filter(item =>
                item &&
                typeof item.id === "string" &&
                typeof item.title === "string" &&
                typeof item.message === "string" &&
                typeof item.read === "boolean"
            );
        } catch (error) {
            console.error("Could not load notifications:", error);
            return [];
        }
    }

    let notifications = loadNotifications();

    function saveNotifications() {
        try {
            localStorage.setItem(
                STORAGE_KEY,
                JSON.stringify(notifications)
            );

            return true;
        } catch (error) {
            console.error("Could not save notifications:", error);
            return false;
        }
    }

    function getIcon(type) {
        const icons = {
            appointment: "📅",
            medical: "📋",
            general: "🐾"
        };

        return icons[type] || "🔔";
    }

    function formatTime(value) {
        const date = new Date(value);

        if (Number.isNaN(date.getTime())) {
            return "Date unavailable";
        }

        return date.toLocaleString([], {
            dateStyle: "medium",
            timeStyle: "short"
        });
    }

    function renderNotifications() {
        const unreadCount = notifications.filter(
            item => !item.read
        ).length;

        countBadge.textContent = unreadCount > 99
            ? "99+"
            : String(unreadCount);

        countBadge.classList.toggle("hidden", unreadCount === 0);

        button.setAttribute(
            "aria-label",
            unreadCount > 0
                ? `Notifications, ${unreadCount} unread`
                : "Notifications, none unread"
        );

        summary.textContent = unreadCount > 0
            ? `${unreadCount} unread notification${unreadCount === 1 ? "" : "s"}`
            : "You're all caught up.";

        list.replaceChildren();

        if (notifications.length === 0) {
            const empty = document.createElement("div");
            empty.className = "notification-empty";

            const icon = document.createElement("span");
            icon.textContent = "🔔";

            const heading = document.createElement("h3");
            heading.textContent = "No notifications";

            const description = document.createElement("p");
            description.textContent = "New updates will appear here.";

            empty.append(icon, heading, description);
            list.append(empty);
            return;
        }

        const sorted = [...notifications].sort(
            (a, b) =>
                new Date(b.createdAt || 0) -
                new Date(a.createdAt || 0)
        );

        sorted.forEach(function (item) {
            const row = document.createElement("article");
            row.className = "notification-item";

            if (!item.read) {
                row.classList.add("unread");
            }

            const icon = document.createElement("div");
            icon.className = "notification-item-icon";
            icon.setAttribute("aria-hidden", "true");
            icon.textContent = getIcon(item.type);

            const content = document.createElement("div");
            content.className = "notification-item-content";

            const heading = document.createElement("h3");
            heading.textContent = item.title;

            const message = document.createElement("p");
            message.textContent = item.message;

            const time = document.createElement("time");
            time.textContent = formatTime(item.createdAt);

            content.append(heading, message, time);
            row.append(icon, content);
            list.append(row);
        });
    }

    function setPanelOpen(isOpen) {
        panel.classList.toggle("hidden", !isOpen);
        button.setAttribute("aria-expanded", String(isOpen));
    }

    button.addEventListener("click", function () {
        const isCurrentlyOpen =
            button.getAttribute("aria-expanded") === "true";

        setPanelOpen(!isCurrentlyOpen);
    });

    markAllReadButton.addEventListener("click", function () {
        const previous = notifications;
        notifications = notifications.map(item => ({
            ...item,
            read: true
        }));

        if (!saveNotifications()) {
            notifications = previous;
            window.alert("Could not save this change in browser storage.");
        }

        renderNotifications();
    });

    clearButton.addEventListener("click", function () {
        if (notifications.length === 0) {
            return;
        }

        const confirmed = window.confirm(
            "Clear all notifications? This cannot be undone."
        );

        if (!confirmed) {
            return;
        }

        const previous = notifications;
        notifications = [];

        if (!saveNotifications()) {
            notifications = previous;
            window.alert("Could not clear notifications. Please try again.");
        }

        renderNotifications();
    });

    document.addEventListener("click", function (event) {
        if (
            !panel.contains(event.target) &&
            !button.contains(event.target)
        ) {
            setPanelOpen(false);
        }
    });

    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape") {
            setPanelOpen(false);
        }
    });

    renderNotifications();
});
