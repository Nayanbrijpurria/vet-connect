import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";

const source = await readFile(new URL("../js/appointments.js", import.meta.url), "utf8");

class FakeOption {
    constructor(text, value) {
        this.textContent = text;
        this.value = value;
        this.dataset = {};
    }
}

class FakeElement {
    constructor(id) {
        this.id = id;
        this.children = [];
        this.listeners = new Map();
        this.value = "";
        this.disabled = false;
        this.textContent = "";
        this.options = this.children;
        this.selectedOptions = [];
    }

    addEventListener(type, callback) {
        this.listeners.set(type, callback);
    }

    async dispatch(type) {
        return this.listeners.get(type)?.({ preventDefault() {} });
    }

    replaceChildren(...children) {
        this.children = children;
        this.options = this.children;
    }

    add(option) {
        this.children.push(option);
    }

    appendChild(child) {
        this.children.push(child);
        return child;
    }

    querySelector() {
        return new FakeElement("submit");
    }

    reportValidity() {
        return true;
    }

    reset() {}
}

function boot({ ignoreAborts = false } = {}) {
    const ids = [
        "appointment-form", "appointment-message", "appointment-list", "pet-name",
        "veterinarian-id", "appointment-date", "appointment-time", "availability-timezone"
    ];
    const elements = new Map(ids.map(id => [id, new FakeElement(id)]));
    const availabilityRequests = [];
    let ready;

    const document = {
        addEventListener(type, callback) {
            if (type === "DOMContentLoaded") ready = callback;
        },
        getElementById(id) {
            return elements.get(id);
        },
        createElement(tag) {
            return new FakeElement(tag);
        }
    };

    const window = {
        location: { search: "", replace() {} },
        vetApi: { async ensureSession() { return true; } }
    };

    async function fetch(url, options = {}) {
        if (url.endsWith("/api/pets")) {
            return { ok: true, async json() { return []; } };
        }
        if (url.endsWith("/api/vets/approved")) {
            return {
                ok: true,
                async json() {
                    return [{ userId: 8, fullName: "Vet", qualification: "DVM" }];
                }
            };
        }
        if (url.endsWith("/api/appointments")) {
            return { ok: true, async json() { return []; } };
        }
        if (url.includes("/available-slots?")) {
            return new Promise((resolve, reject) => {
                const request = {
                    url,
                    signal: options.signal,
                    resolve(data = { slots: [], timezone: "UTC" }) {
                        resolve({
                            ok: true,
                            async json() {
                                return data;
                            }
                        });
                    },
                    fail(status = 503) {
                        resolve({
                            ok: false,
                            status,
                            async json() {
                                return {};
                            }
                        });
                    }
                };
                options.signal?.addEventListener("abort", () => {
                    if (ignoreAborts) {
                        return;
                    }
                    const error = new Error("Aborted");
                    error.name = "AbortError";
                    reject(error);
                }, { once: true });
                availabilityRequests.push(request);
            });
        }
        throw new Error(`Unexpected request: ${url}`);
    }

    vm.runInNewContext(source, {
        window,
        document,
        sessionStorage: { getItem: key => key === "vetplatform_role" ? "CUSTOMER" : null },
        URLSearchParams,
        API_BASE_URL: "",
        Option: FakeOption,
        AbortController,
        fetch,
        Number,
        String,
        Intl,
        Date,
        console
    });

    return {
        elements,
        availabilityRequests,
        async ready() {
            await ready();
            await new Promise(resolve => setImmediate(resolve));
        }
    };
}

test("repeated availability loads for the same vet and date share one request", async () => {
    const app = boot();
    await app.ready();

    app.elements.get("veterinarian-id").value = "8";
    app.elements.get("appointment-date").value = "2026-11-23";
    const vetChange = app.elements.get("veterinarian-id").dispatch("change");
    const dateChange = app.elements.get("appointment-date").dispatch("change");
    await new Promise(resolve => setImmediate(resolve));

    assert.equal(app.availabilityRequests.length, 1);
    app.availabilityRequests[0].resolve();
    await Promise.all([vetChange, dateChange]);
});

test("changing the requested date aborts an obsolete availability request", async () => {
    const app = boot();
    await app.ready();

    app.elements.get("veterinarian-id").value = "8";
    app.elements.get("appointment-date").value = "2026-11-23";
    const oldRequest = app.elements.get("appointment-date").dispatch("change");
    await new Promise(resolve => setImmediate(resolve));

    app.elements.get("appointment-date").value = "2026-11-24";
    const currentRequest = app.elements.get("appointment-date").dispatch("change");
    await new Promise(resolve => setImmediate(resolve));

    assert.equal(app.availabilityRequests.length, 2);
    assert.equal(app.availabilityRequests[0].signal.aborted, true);
    app.availabilityRequests[1].resolve();
    await Promise.all([oldRequest, currentRequest]);
    assert.equal(app.elements.get("appointment-message").textContent.includes("Aborted"), false);
});

test("a failed request can be retried and success clears only its stale error", async () => {
    const app = boot();
    await app.ready();

    app.elements.get("veterinarian-id").value = "8";
    app.elements.get("appointment-date").value = "2026-11-23";
    const firstRequest = app.elements.get("appointment-date").dispatch("change");
    await new Promise(resolve => setImmediate(resolve));
    app.availabilityRequests[0].fail(503);
    await firstRequest;

    assert.equal(app.elements.get("appointment-message").textContent, "Request failed (HTTP 503).");
    assert.equal(app.elements.get("appointment-time").disabled, true);

    const retry = app.elements.get("appointment-date").dispatch("change");
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(app.availabilityRequests.length, 2);
    assert.equal(app.elements.get("appointment-time").children[0].textContent, "Loading available times…");

    app.availabilityRequests[1].resolve({
        slots: [],
        timezone: "America/New_York"
    });
    await retry;

    assert.equal(app.elements.get("appointment-message").textContent, "");
    assert.match(
        app.elements.get("availability-timezone").textContent,
        /America\/New_York/
    );
});

test("a late obsolete response cannot replace current slots or timezone", async () => {
    const app = boot({ ignoreAborts: true });
    await app.ready();

    app.elements.get("veterinarian-id").value = "8";
    app.elements.get("appointment-date").value = "2026-11-23";
    const oldRequest = app.elements.get("appointment-date").dispatch("change");
    await new Promise(resolve => setImmediate(resolve));

    app.elements.get("appointment-date").value = "2026-11-24";
    const currentRequest = app.elements.get("appointment-date").dispatch("change");
    await new Promise(resolve => setImmediate(resolve));

    app.availabilityRequests[1].resolve({
        slots: [{ start: "2026-11-24T09:00:00Z", end: "2026-11-24T09:30:00Z" }],
        timezone: "Europe/London"
    });
    await currentRequest;
    app.availabilityRequests[0].resolve({
        slots: [{ start: "2026-11-23T10:00:00Z", end: "2026-11-23T10:30:00Z" }],
        timezone: "Asia/Kolkata"
    });
    await oldRequest;

    assert.equal(
        app.elements.get("appointment-time").children[1].value,
        "2026-11-24T09:00:00Z"
    );
    assert.match(
        app.elements.get("availability-timezone").textContent,
        /Europe\/London/
    );
});

test("repeated local times are labeled with timezone abbreviations", async () => {
    const app = boot();
    await app.ready();

    app.elements.get("veterinarian-id").value = "8";
    app.elements.get("appointment-date").value = "2026-11-01";
    const request = app.elements.get("appointment-date").dispatch("change");
    await new Promise(resolve => setImmediate(resolve));
    app.availabilityRequests[0].resolve({
        slots: [
            { start: "2026-11-01T01:00:00-04:00", end: "2026-11-01T01:30:00-04:00" },
            { start: "2026-11-01T01:00:00-05:00", end: "2026-11-01T01:30:00-05:00" }
        ],
        timezone: "America/New_York"
    });
    await request;

    assert.match(app.elements.get("appointment-time").children[1].textContent, /EDT|GMT-4/);
    assert.match(app.elements.get("appointment-time").children[2].textContent, /EST|GMT-5/);
});
