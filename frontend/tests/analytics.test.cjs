const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { test } = require("node:test");
const vm = require("node:vm");

const script = fs.readFileSync(
    path.join(__dirname, "../src/analytics.js"), "utf8");

const ids = [
    "usage-new-accounts",
    "usage-events-created",
    "usage-event-registrations",
    "usage-moderation-decisions"
];

async function render(response, pathname = "/admin") {
    const elements = Object.fromEntries(
        ids.map(id => [id, { textContent: "Loading..." }])
    );

    const calls = [];
    const handlers = {};

    elements["statistics-start-date"] = {
        value: "",
        validity: { valid: true }
    };

    elements["statistics-end-date"] = {
        value: "",
        validity: { valid: true }
    };

    elements["statistics-filter-error"] = {
        hidden: true,
        textContent: ""
    };

    elements["statistics-date-filter"] = {
        addEventListener: (event, handler) => {
            handlers[event] = handler;
        },
        reset: () => {
            elements["statistics-start-date"].value = "";
            elements["statistics-end-date"].value = "";
        }
    };

    elements["clear-statistics-filter"] = {
        addEventListener: (event, handler) => {
            handlers[event] = handler;
        }
    };

    const context = vm.createContext({
        window: { location: { pathname } },
        document: { getElementById: id => elements[id] },
        URLSearchParams,
        fetch: url => {
            calls.push(url);
            return response;
        }
    });

    await vm.runInContext(script, context);

    return { elements, calls, handlers };
}

// Original usage statistics tests

test("usage cards display zero, known counts, and unavailable independently", async () => {
    const response = Promise.resolve({
        ok: true,
        json: async () => ({
            newAccounts: null,
            eventsCreated: 0,
            eventRegistrations: 102,
            moderationDecisions: 7
        })
    });

    const { elements, calls } = await render(response);

    assert.deepEqual(calls, [
        "/api/admin/analytics/overview"
    ]);

    assert.deepEqual(
        ids.map(id => elements[id].textContent),
        ["Unavailable", "0", "102", "7"]
    );
});

test("a failed overview request displays Unavailable", async () => {
    const { elements } = await render(
        Promise.resolve({ ok: false })
    );

    assert.ok(ids.every(
        id => elements[id].textContent === "Unavailable"
    ));
});

test("invalid metric values display Unavailable", async () => {
    const { elements } = await render(
        Promise.resolve({
            ok: true,
            json: async () => ({
                newAccounts: -1,
                eventsCreated: "4",
                eventRegistrations: 1.5
            })
        })
    );

    assert.ok(ids.every(
        id => elements[id].textContent === "Unavailable"
    ));
});

test("other admin pages do not request usage statistics", async () => {
    const { calls } = await render(
        Promise.resolve({ ok: false }),
        "/admin/users"
    );

    assert.deepEqual(calls, []);
});

// Date filter tests

test("apply filter sends selected dates to the API", async () => {
    const { elements, calls, handlers } = await render(
        Promise.resolve({ ok: false })
    );

    elements["statistics-start-date"].value = "2026-11-01";
    elements["statistics-end-date"].value = "2026-11-20";

    handlers.submit({ preventDefault() {} });

    assert.equal(
        calls[calls.length - 1],
        "/api/admin/analytics/overview?startDate=2026-11-01&endDate=2026-11-20"
    );

    assert.equal(
        elements["statistics-filter-error"].hidden,
        true
    );
});

test("reversed dates display an error", async () => {
    const { elements, calls, handlers } = await render(
        Promise.resolve({ ok: false })
    );

    elements["statistics-start-date"].value = "2026-11-20";
    elements["statistics-end-date"].value = "2026-11-01";

    handlers.submit({ preventDefault() {} });

    assert.equal(
        elements["statistics-filter-error"].hidden,
        false
    );

    assert.equal(
        elements["statistics-filter-error"].textContent,
        "Start date cannot be after the end date."
    );

    assert.equal(calls.length, 1);
});

test("invalid dates display an error", async () => {
    const { elements, calls, handlers } = await render(
        Promise.resolve({ ok: false })
    );

    elements["statistics-start-date"].validity.valid = false;

    handlers.submit({ preventDefault() {} });

    assert.equal(
        elements["statistics-filter-error"].hidden,
        false
    );

    assert.equal(
        elements["statistics-filter-error"].textContent,
        "Enter valid start and end dates."
    );

    assert.equal(calls.length, 1);
});

test("clear filter resets dates and reloads default statistics", async () => {
    const { elements, calls, handlers } = await render(
        Promise.resolve({ ok: false })
    );

    elements["statistics-start-date"].value = "2026-11-01";
    elements["statistics-end-date"].value = "2026-11-20";

    handlers.submit({ preventDefault() {} });

    elements["statistics-filter-error"].hidden = false;
    elements["statistics-filter-error"].textContent = "Test error";

    handlers.click();

    assert.equal(
        elements["statistics-start-date"].value, ""
    );

    assert.equal(
        elements["statistics-end-date"].value, ""
    );

    assert.equal(
        elements["statistics-filter-error"].hidden, true
    );

    assert.equal(
        elements["statistics-filter-error"].textContent, ""
    );

    assert.equal(
        calls[calls.length - 1],
        "/api/admin/analytics/overview"
    );

    assert.equal(calls.length, 3);
});
