import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";

const source = await readFile(new URL("../js/api-client.js", import.meta.url), "utf8");

function createClient(nativeFetch, initialValues = {}) {
    const values = new Map();
    Object.entries(initialValues).forEach(([key, value]) => values.set(key, value));
    const replacements = [];
    const window = {
        fetch: nativeFetch,
        location: {
            href: "https://frontend.example/pages/dashboard.html",
            replace: (url) => replacements.push(url)
        }
    };
    const context = {
        window,
        API_BASE_URL: "https://api.example",
        URL,
        Headers,
        Request,
        Response,
        sessionStorage: {
            getItem: (key) => values.get(key) ?? null,
            setItem: (key, value) => values.set(key, String(value)),
            removeItem: (key) => values.delete(key)
        }
    };
    vm.runInNewContext(source, context);
    return { window, values, replacements };
}

test("simultaneous expired requests share refresh and retry once", async () => {
    let refreshCalls = 0;
    const nativeFetch = async (input, init = {}) => {
        const url = new URL(input instanceof Request ? input.url : input);
        if (url.pathname === "/api/auth/refresh") {
            refreshCalls += 1;
            await new Promise((resolve) => setTimeout(resolve, 10));
            return Response.json({ token: "fresh-access", userId: 4, role: "CUSTOMER", email: "a@example.test" });
        }
        const headers = new Headers(input instanceof Request ? input.headers : init.headers);
        return headers.get("Authorization") === "Bearer fresh-access"
            ? Response.json({ ok: true })
            : Response.json({ message: "expired" }, { status: 401 });
    };
    const client = createClient(nativeFetch);
    client.window.vetApi.setAccessToken("expired-access");

    const [first, second] = await Promise.all([
        client.window.fetch("https://api.example/api/protected/a", { headers: { Authorization: "Bearer" } }),
        client.window.fetch("https://api.example/api/protected/b", { headers: { Authorization: "Bearer" } })
    ]);

    assert.equal(first.status, 200);
    assert.equal(second.status, 200);
    assert.equal(refreshCalls, 1);
    assert.equal(client.values.get("vetplatform_role"), "CUSTOMER");
    assert.deepEqual(client.replacements, []);
});

test("refresh failure clears only authentication metadata", async () => {
    const nativeFetch = async () => Response.json({ message: "invalid" }, { status: 401 });
    const client = createClient(nativeFetch);
    client.values.set("vetplatform_role", "ADMIN");
    client.values.set("vetplatform_email", "a@example.test");
    client.values.set("unrelated", "keep");

    assert.equal(await client.window.vetApi.ensureSession(), false);
    assert.equal(client.values.has("vetplatform_role"), false);
    assert.equal(client.values.has("vetplatform_email"), false);
    assert.equal(client.values.get("unrelated"), "keep");
});

test("a new page restores its in-memory token through the cookie endpoint", async () => {
    let refreshRequest;
    const nativeFetch = async (input, init = {}) => {
        refreshRequest = { url: new URL(input), init };
        return Response.json({ token: "restored-access", userId: 8, role: "VET", email: "vet@example.test" });
    };
    const client = createClient(nativeFetch, { vetplatform_token: "legacy-persisted-access" });

    assert.equal(client.values.has("vetplatform_token"), false);
    assert.equal(await client.window.vetApi.ensureSession(), true);
    assert.equal(client.window.vetApi.getAccessToken(), "restored-access");
    assert.equal(client.values.get("vetplatform_role"), "VET");
    assert.equal(refreshRequest.url.pathname, "/api/auth/refresh");
    assert.equal(refreshRequest.init.credentials, "include");
    assert.equal(new Headers(refreshRequest.init.headers).get("X-VetConnect-CSRF"), "1");
});

test("FormData and binary responses pass through the shared transport", async () => {
    let observedContentType;
    let observedBody;
    const nativeFetch = async (input, init = {}) => {
        observedContentType = new Headers(input instanceof Request ? input.headers : init.headers).get("Content-Type");
        observedBody = input instanceof Request ? undefined : init.body;
        return new Response(new Uint8Array([1, 2, 3]), { status: 200 });
    };
    const client = createClient(nativeFetch);
    const form = new FormData();
    form.append("document", new Blob(["record"]), "record.txt");

    const response = await client.window.fetch("https://api.example/api/public-upload", {
        method: "POST",
        body: form
    });

    assert.equal(response.status, 200);
    assert.equal(observedContentType, null);
    assert.equal(observedBody, form);
    assert.deepEqual(Array.from(new Uint8Array(await response.arrayBuffer())), [1, 2, 3]);
});
