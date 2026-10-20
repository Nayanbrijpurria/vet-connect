
document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("appointment-form");
    const message = document.getElementById("appointment-message");
    const list = document.getElementById("appointment-list");
    const dateInput = document.getElementById("appointment-date");

    if (!form || !message || !list || !dateInput) return;

    // Do not allow dates in the past.
    const today = new Date();
    const localToday = [
        today.getFullYear(),
        String(today.getMonth() + 1).padStart(2, "0"),
        String(today.getDate()).padStart(2, "0")
    ].join("-");

    dateInput.min = localToday;

    const storageKey = "vetplatform_demo_appointments";

    function getAppointments() {
        try {
            return JSON.parse(localStorage.getItem(storageKey) || "[]");
        } catch {
            return [];
        }
    }

    function renderAppointments() {
        const appointments = getAppointments();
        list.replaceChildren();

        if (appointments.length === 0) {
            const empty = document.createElement("p");
            empty.textContent = "No appointment requests saved yet.";
            list.appendChild(empty);
            return;
        }

        appointments.slice().reverse().forEach((appointment) => {
            const card = document.createElement("article");
            card.className = "appointment-entry";

            const title = document.createElement("h3");
            title.textContent = appointment.petName;

            const details = document.createElement("p");
            details.textContent =
                `${appointment.date} at ${appointment.time} · ` +
                appointment.type;

            const reason = document.createElement("p");
            reason.textContent = `Reason: ${appointment.reason}`;

            const status = document.createElement("span");
            status.className = "appointment-status";
            status.textContent = "Demo request · Not yet booked";

            card.append(title, details, reason, status);
            list.appendChild(card);
        });
    }

    form.addEventListener("submit", (event) => {
        event.preventDefault();

        if (!form.reportValidity()) return;

        const appointment = {
            petName: form.elements.petName.value.trim(),
            type: form.elements.consultationType.value,
            date: form.elements.appointmentDate.value,
            time: form.elements.appointmentTime.value,
            reason: form.elements.reason.value.trim()
        };

        if (appointment.date < localToday) {
            message.textContent = "Please select today or a future date.";
            return;
        }

        try {
            const appointments = getAppointments();
            appointments.push(appointment);
            localStorage.setItem(storageKey, JSON.stringify(appointments));

            message.textContent =
                "Demo request saved in this browser. It has not been sent to a veterinarian.";

            form.reset();
            dateInput.min = localToday;
            renderAppointments();
        } catch {
            message.textContent =
                "Could not save the request in this browser. Please try again.";
        }
    });

    renderAppointments();
});
