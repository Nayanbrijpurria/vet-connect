import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";

const source = await readFile(new URL("../js/google-auth.js", import.meta.url), "utf8");

function createElement(value = "") {
    const classes = new Set();
    return {
        value,
        clientWidth: 320,
        textContent: "",
        classList: {
            add: (name) => classes.add(name),
            remove: (name) => classes.delete(name),
            contains: (name) => classes.has(name),
            toggle(name, force) {
                if (force === undefined) force = !classes.has(name);
                if (force) classes.add(name);
                else classes.delete(name);
                return force;
            }
        }
    };
}

function boot({ mode, authResponse, initialPassword = "" }) {
    const ids = new Map();
    const buttonId = mode === "signin" ? "google-signin-button" : "google-link-button";
    const sectionId = mode === "signin" ? "google-signin-section" : "google-link-section";
    const messageId = mode === "signin" ? "google-auth-message" : "google-link-message";
    ids.set(buttonId, createElement());
    ids.set(sectionId, createElement());
    ids.set(messageId, createElement());
    ids.set("google-link-password", createElement(initialPassword));

    const storage = new Map();
    const fetchRequests = [];
    const redirects = [];
    let callback;
    let accessToken = null;
    let gisButton;
    let gisOptions;
    const window = {
        location: { assign: (path) => redirects.push(path) },
        vetApi: {
            async fetch(url, options = {}) {
                const request = { url, options };
                fetchRequests.push(request);
                if (new URL(url).pathname === "/api/auth/google/config") {
                    return Response.json({ clientId: "public-client-id" });
                }
                return Response.json(authResponse);
            },
            async ensureSession() { return true; },
            getAccessToken() { return "customer-access-token"; },
            setAccessToken(token) { accessToken = token; }
        }
    };
    const document = {
        getElementById: (id) => ids.get(id) || null,
        createElement() { return {}; },
        head: {
            appendChild(script) {
                window.google = {
                    accounts: {
                        id: {
                            initialize(options) { callback = options.callback; },
                            renderButton(element, options) {
                                gisButton = element;
                                gisOptions = options;
                            }
                        }
                    }
                };
                script.onload();
            }
        }
    };
    const context = {
        window,
        document,
        API_BASE_URL: "https://api.example",
        sessionStorage: {
            setItem: (key, value) => storage.set(key, String(value)),
            getItem: (key) => storage.get(key) ?? null
        },
        Response,
        Promise,
        Math
    };
    vm.runInNewContext(source, context);

    return {
        ids,
        storage,
        fetchRequests,
        redirects,
        window,
        get callback() { return callback; },
        get gisButton() { return gisButton; },
        get gisOptions() { return gisOptions; },
        get accessToken() { return accessToken; }
    };
}

async function letInitializationFinish() {
    await new Promise((resolve) => setImmediate(resolve));
}

test("customer Google sign-in sends the ID token to VetConnect and keeps it out of browser storage", async () => {
    const app = boot({
        mode: "signin",
        authResponse: {
            message: "Google Sign-In successful",
            token: "vetconnect-access-token",
            role: "CUSTOMER",
            email: "customer@example.test",
            userId: 17
        }
    });
    await letInitializationFinish();

    assert.equal(app.gisButton, app.ids.get("google-signin-button"));
    assert.equal(app.gisOptions.text, "continue_with");
    await app.callback({ credential: "google-id-token" });

    const request = app.fetchRequests.find((entry) => new URL(entry.url).pathname === "/api/auth/google");
    assert.deepEqual(JSON.parse(request.options.body), { credential: "google-id-token" });
    assert.equal(app.accessToken, "vetconnect-access-token");
    assert.equal(app.storage.get("vetplatform_role"), "CUSTOMER");
    assert.equal([...app.storage.values()].includes("google-id-token"), false);
    assert.deepEqual(app.redirects, ["/pages/dashboard.html"]);
});

test("Google linking sends the current session, password and identity assertion, then clears the password field", async () => {
    const app = boot({ mode: "link", authResponse: { message: "Google account linked" }, initialPassword: "current-password" });
    app.storage.set("vetplatform_role", "CUSTOMER");
    await letInitializationFinish();

    assert.equal(app.gisButton, app.ids.get("google-link-button"));
    await app.callback({ credential: "google-id-token" });

    const request = app.fetchRequests.find((entry) => new URL(entry.url).pathname === "/api/auth/google/link");
    assert.equal(new Headers(request.options.headers).get("Authorization"), "Bearer customer-access-token");
    assert.equal(new Headers(request.options.headers).get("X-VetConnect-CSRF"), "1");
    assert.deepEqual(JSON.parse(request.options.body), {
        credential: "google-id-token",
        currentPassword: "current-password"
    });
    assert.equal(app.ids.get("google-link-password").value, "");
    assert.equal(app.storage.has("google-id-token"), false);
});

test("Google account linking does not initialize for a missing customer session", async () => {
    const app = boot({ mode: "link", authResponse: { message: "Google account linked" } });
    app.window.vetApi.ensureSession = async () => false;
    await letInitializationFinish();

    assert.equal(app.gisButton, undefined);
    assert.equal(app.fetchRequests.some((entry) => new URL(entry.url).pathname === "/api/auth/google/link"), false);
});
