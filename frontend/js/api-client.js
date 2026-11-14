/* Shared authenticated transport. Business scripts can keep their existing
   fetch calls; this layer supplies memory-only access tokens and refresh. */
(function () {
    "use strict";

    const nativeFetch = window.fetch.bind(window);
    const apiOrigin = new URL(API_BASE_URL, window.location.href).origin;
    const csrfHeaders = { "X-VetConnect-CSRF": "1" };
    let accessToken = null;
    let refreshPromise = null;
    let redirecting = false;

    // Remove access tokens left by the previous sessionStorage-based client.
    sessionStorage.removeItem("vetplatform_token");

    function clearMetadata() {
        ["vetplatform_token", "vetplatform_role", "vetplatform_email", "vetplatform_user_id"]
            .forEach((key) => sessionStorage.removeItem(key));
    }

    function updateSession(data) {
        accessToken = data.token;
        if (data.role) sessionStorage.setItem("vetplatform_role", data.role);
        if (data.email) sessionStorage.setItem("vetplatform_email", data.email);
        if (data.userId !== undefined && data.userId !== null) {
            sessionStorage.setItem("vetplatform_user_id", String(data.userId));
        }
    }

    async function refresh() {
        if (!refreshPromise) {
            refreshPromise = (async () => {
                try {
                    const response = await nativeFetch(`${API_BASE_URL}/api/auth/refresh`, {
                        method: "POST",
                        credentials: "include",
                        headers: csrfHeaders
                    });
                    const data = await response.json().catch(() => ({}));
                    if (!response.ok || !data.token) throw new Error("Refresh rejected");
                    updateSession(data);
                    return true;
                } catch (_) {
                    accessToken = null;
                    clearMetadata();
                    return false;
                } finally {
                    refreshPromise = null;
                }
            })();
        }
        return refreshPromise;
    }

    function redirectToLogin() {
        if (redirecting) return;
        redirecting = true;
        sessionStorage.setItem(
            "vetplatform_auth_notice",
            "Your session expired or is no longer valid. Please sign in again."
        );
        window.location.replace("/pages/login.html");
    }

    function resolveRequest(input, init) {
        const request = input instanceof Request ? input : null;
        const urlValue = request ? request.url : input;
        const url = new URL(urlValue, window.location.href);
        const headers = new Headers(request ? request.headers : undefined);
        new Headers(init?.headers).forEach((value, key) => headers.set(key, value));
        return { request, url, headers };
    }

    async function apiFetch(input, init = {}) {
        const { request, url, headers } = resolveRequest(input, init);
        const isApi = url.origin === apiOrigin;
        const path = url.pathname;
        const publicAuthPath = [
            "/api/auth/login", "/api/auth/register", "/api/vets/register",
            "/api/auth/refresh", "/api/auth/logout"
        ].includes(path);
        const protectedRequest = isApi && !publicAuthPath && headers.has("Authorization");
        const options = { ...init, headers };
        let fetchInput = input;
        if (isApi) options.credentials = "include";

        if (protectedRequest) {
            if (!accessToken && !(await refresh())) {
                redirectToLogin();
                return new Response(JSON.stringify({ message: "Authentication required" }), {
                    status: 401, headers: { "Content-Type": "application/json" }
                });
            }
            headers.set("Authorization", `Bearer ${accessToken}`);
        }

        if (request) {
            fetchInput = new Request(request, options);
            Object.keys(options).forEach((key) => delete options[key]);
        }

        const retryInput = request ? fetchInput.clone() : input;
        let response = await nativeFetch(fetchInput, options);
        if (protectedRequest && response.status === 401) {
            if (await refresh()) {
                headers.set("Authorization", `Bearer ${accessToken}`);
                const retryRequest = request
                    ? new Request(retryInput, { headers, credentials: "include" })
                    : input;
                response = await nativeFetch(retryRequest, request ? {} : options);
            }
            if (response.status === 401) redirectToLogin();
        }
        return response;
    }

    async function logout() {
        try {
            await nativeFetch(`${API_BASE_URL}/api/auth/logout`, {
                method: "POST", credentials: "include", headers: csrfHeaders
            });
        } finally {
            accessToken = null;
            clearMetadata();
            window.location.replace("/pages/login.html");
        }
    }

    window.vetApi = {
        fetch: apiFetch,
        setAccessToken(token) { accessToken = token || null; },
        getAccessToken() { return accessToken; },
        ensureSession() { return accessToken ? Promise.resolve(true) : refresh(); },
        logout,
        refresh
    };
    window.fetch = apiFetch;
})();
