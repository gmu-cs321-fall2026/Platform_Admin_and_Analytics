const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { test } = require("node:test");
const source = fs.readFileSync(path.join(__dirname, "../src/notification-templates.js"), "utf8");
const fixtures = [
    { id: "first", name: "Event reminder", message: "First message" },
    { id: "second", name: "Checklist reminder", message: "Second message" }
];

function element() {
    return {
        children: [], listeners: {}, value: "", textContent: "", hidden: false, disabled: false,
        appendChild(child) { this.children.push(child); },
        replaceChildren() { this.children = []; },
        addEventListener(name, handler) { this.listeners[name] = handler; }
    };
}

async function page(save, templates = fixtures, loadError = false) {
    const ids = ["notification-template-select", "notification-template-message", "notification-template-status",
        "notification-template-preview", "notification-preview-message", "notification-template-preview-button",
        "notification-template-save", "notification-template-form", "notification-load-message"];
    const nodes = Object.fromEntries(ids.map(id => [id, element()]));
    nodes["notification-template-form"].hidden = true;
    nodes["notification-template-preview"].hidden = true;
    nodes["notification-template-save"].disabled = true;
    let calls = 0;
    const context = vm.createContext({
        document: { createElement: element, getElementById: id => nodes[id] }, URLSearchParams,
        fetch: async (url, options) => {
            if (options) { calls++; return save(url, options); }
            assert.equal(url, "/api/config/notification-templates");
            return { ok: !loadError, json: async () => ({ templates: structuredClone(templates) }) };
        }
    });
    vm.runInContext(source, context);
    await new Promise(resolve => setImmediate(resolve));
    return {
        nodes, input: nodes["notification-template-message"], status: nodes["notification-template-status"],
        edit(text) { nodes["notification-template-message"].value = text; nodes["notification-template-message"].listeners.input(); },
        select(id) { nodes["notification-template-select"].value = id; nodes["notification-template-select"].listeners.change(); },
        preview: () => nodes["notification-template-preview-button"].listeners.click(),
        submit: () => nodes["notification-template-form"].listeners.submit({ preventDefault() {} }),
        calls: () => calls
    };
}

test("loads template choices and displays the selected message", async () => {
    const ui = await page();
    assert.equal(ui.nodes["notification-template-select"].children.length, 2);
    assert.equal(ui.input.value, "First message");
    ui.select("second");
    assert.equal(ui.input.value, "Second message");
    assert.equal(ui.nodes["notification-template-form"].hidden, false);
});

test("preview shows exact plain text and saving sends the selected template", async () => {
    const message = '  Bring your résumé.\n<strong>Questions & answers</strong>  ';
    const ui = await page(async (url, options) => {
        assert.equal(url, "/api/config/notification-templates/second");
        assert.equal(options.method, "PUT");
        assert.equal(options.body.get("message"), message);
        return { ok: true, json: async () => ({ id: "second", name: "Checklist reminder", message }) };
    });
    ui.select("second");
    ui.edit(message);
    ui.preview();
    assert.equal(ui.nodes["notification-preview-message"].textContent, message);
    assert.equal(ui.nodes["notification-template-preview"].hidden, false);
    await ui.submit();
    assert.match(ui.status.textContent, /saved/);
    ui.select("first");
    ui.select("second");
    assert.equal(ui.input.value, message);
});

test("blank messages are rejected without saving", async () => {
    const ui = await page();
    ui.edit(" \t\n ");
    ui.preview();
    assert.match(ui.status.textContent, /cannot be blank/);
    assert.equal(ui.nodes["notification-template-save"].disabled, true);
    await ui.submit();
    assert.equal(ui.calls(), 0);
});

test("saving requires a preview of the latest edits", async () => {
    const ui = await page();
    ui.edit("First edit");
    await ui.submit();
    assert.equal(ui.calls(), 0);
    ui.preview();
    ui.edit("New wording");
    assert.equal(ui.nodes["notification-template-preview"].hidden, true);
    assert.equal(ui.nodes["notification-template-save"].disabled, true);
    await ui.submit();
    assert.equal(ui.calls(), 0);
    assert.match(ui.status.textContent, /Preview the current message/);
});

for (const failure of ["server", "network", "invalid JSON", "mismatched response"]) {
    test(`${failure} failure keeps edits and preview for a successful retry`, async () => {
        let fail = true;
        const ui = await page(async () => {
            if (!fail) return { ok: true, json: async () => ({ id: "first", message: "My updated wording" }) };
            if (failure === "network") throw new Error("Network unavailable");
            if (failure === "invalid JSON") return { ok: false, json: async () => { throw new Error("Invalid response"); } };
            if (failure === "mismatched response") return { ok: true, json: async () => ({ id: "second", message: "Wrong" }) };
            return { ok: false, json: async () => ({ error: "Could not save the notification template." }) };
        });
        ui.edit("My updated wording");
        ui.preview();
        await ui.submit();
        assert.equal(ui.input.value, "My updated wording");
        assert.equal(ui.nodes["notification-preview-message"].textContent, "My updated wording");
        assert.equal(ui.nodes["notification-template-preview"].hidden, false);
        assert.equal(ui.input.disabled, false);
        assert.equal(ui.nodes["notification-template-select"].disabled, false);
        assert.ok(ui.status.textContent && ui.status.textContent !== "Saving...");
        fail = false;
        await ui.submit();
        assert.equal(ui.calls(), 2);
        assert.match(ui.status.textContent, /saved/);
    });
}

test("switching templates preserves unsaved drafts and resets the preview", async () => {
    const ui = await page();
    ui.edit("Unsaved first message");
    ui.preview();
    ui.select("second");
    ui.edit("Unsaved second message");
    ui.select("first");
    assert.equal(ui.input.value, "Unsaved first message");
    assert.equal(ui.nodes["notification-template-preview"].hidden, true);
    ui.select("second");
    assert.equal(ui.input.value, "Unsaved second message");
});

test("pending save disables controls and prevents duplicate requests", async () => {
    let finish;
    const ui = await page(() => new Promise(resolve => { finish = resolve; }));
    ui.edit("Pending wording");
    ui.preview();
    const pending = ui.submit();
    await ui.submit();
    assert.equal(ui.calls(), 1);
    assert.equal(ui.input.disabled, true);
    assert.equal(ui.nodes["notification-template-select"].disabled, true);
    assert.equal(ui.nodes["notification-template-save"].disabled, true);
    finish({ ok: true, json: async () => ({ id: "first", message: "Pending wording" }) });
    await pending;
    assert.equal(ui.input.disabled, false);
});

test("empty data and load failure display messages without showing the editor", async () => {
    const empty = await page(undefined, []);
    assert.match(empty.nodes["notification-load-message"].textContent, /No notification templates/);
    assert.equal(empty.nodes["notification-template-form"].hidden, true);
    const failed = await page(undefined, [], true);
    assert.match(failed.nodes["notification-load-message"].textContent, /Could not load/);
    assert.equal(failed.nodes["notification-template-form"].hidden, true);
});
