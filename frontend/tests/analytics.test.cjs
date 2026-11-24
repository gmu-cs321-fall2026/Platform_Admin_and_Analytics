const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { test } = require("node:test");
const vm = require("node:vm");

const script = fs.readFileSync(path.join(__dirname, "../src/analytics.js"), "utf8");
const ids = ["usage-new-accounts", "usage-events-created", "usage-event-registrations", "usage-moderation-decisions"];

async function render(response, pathname = "/admin") {
    const elements = Object.fromEntries(ids.map(id => [id, { textContent: "Loading..." }]));
    const calls = [];
    const context = vm.createContext({
        URLSearchParams,
        window: { location: { pathname } },
        document: { getElementById: id => elements[id] },
        fetch: url => {
            calls.push(url);
            return response;
        }
    });
    await vm.runInContext(script, context);
    return { elements, calls };
}

test("usage cards display zero, known counts, and unavailable independently", async () => {
    const response = Promise.resolve({ ok: true, json: async () => ({
        newAccounts: null, eventsCreated: 0, eventRegistrations: 102, moderationDecisions: 7
    }) });
    const { elements, calls } = await render(response);
    assert.deepEqual(calls, ["/api/admin/analytics/overview"]);
    assert.deepEqual(ids.map(id => elements[id].textContent), ["Unavailable", "0", "102", "7"]);
});

test("a failed overview request displays Unavailable", async () => {
    const { elements } = await render(Promise.resolve({ ok: false }));
    assert.ok(ids.every(id => elements[id].textContent === "Unavailable"));
});

test("invalid metric values display Unavailable", async () => {
    const { elements } = await render(Promise.resolve({ ok: true, json: async () => ({
        newAccounts: -1, eventsCreated: "4", eventRegistrations: 1.5
    }) }));
    assert.ok(ids.every(id => elements[id].textContent === "Unavailable"));
});

test("other admin pages do not request usage statistics", async () => {
    const { calls } = await render(Promise.resolve({ ok: false }), "/admin/users");
    assert.deepEqual(calls, []);
});
