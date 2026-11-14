const localApiHosts = new Set(["localhost", "127.0.0.1"]);
const API_BASE_URL = localApiHosts.has(window.location.hostname)
    ? `http://${window.location.hostname}:8080`
    : "";
