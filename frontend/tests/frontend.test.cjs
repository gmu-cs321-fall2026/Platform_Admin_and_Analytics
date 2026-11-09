const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { test } = require("node:test");
const vm = require("node:vm");

const app = fs.readFileSync(path.join(__dirname, "../src/app.js"), "utf8");

function search(query) {
    const searchField = { value: query, addEventListener() {} };
    const unusedElement = {
        classList: { toggle() {} }, 
        addEventListener() {},
        setAttribute() {},
        removeAttribute() {}
    };
    const context = vm.createContext({
        window: { location: { pathname: "/admin/users" } },
        document: {
            getElementById(id) {
                return id === "search" ? searchField : unusedElement;
            }
        },
        // Leave the page's automatic initial load pending; this test
        // supplies known users and calls the real search function.
        fetch: () => new Promise(() => {}),
        fixtures: [
            { id: 1, name: "Maya Patel", email: "harbor@example.com", role: "STUDENT", status: "ACTIVE" },
            { id: 11, name: "Jordan Lee", email: "forest@example.com", role: "STAFF", status: "ACTIVE" }
        ]
    });
    vm.runInContext(app, context);
    const matches = vm.runInContext(
        "allUsers = fixtures; displayUsers = users => { globalThis.matches = users; }; searchUsers(); matches;",
        context
    );
    return Array.from(matches, user => user.id);
}

test("searching by ID matches the exact account", () => {
    assert.deepEqual(search("1"), [1]);
});

test("searching by name ignores case and surrounding spaces", () => {
    assert.deepEqual(search("  MAYA "), [1]);
});

test("searching by email finds the account independently of its name", () => {
    assert.deepEqual(search("forest@example.com"), [11]);
});

test("an unmatched search returns no accounts", () => {
    assert.deepEqual(search("no-matching-account"), []);
});
