
document.addEventListener("DOMContentLoaded", function () {
    const sidebar = document.getElementById("sidebar");
    const toggleButton = document.getElementById("sidebar-toggle");

    if (sidebar && toggleButton) {
        toggleButton.addEventListener("click", function () {
            const isOpen = sidebar.classList.toggle("open");

            toggleButton.setAttribute(
                "aria-expanded",
                String(isOpen)
            );
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
});
