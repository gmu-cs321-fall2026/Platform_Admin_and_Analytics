const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { test } = require("node:test");
const source = fs.readFileSync(path.join(__dirname, "../src/event-categories.js"), "utf8");

function element(tag) {
    return {
        tag, children: [], listeners: {}, value: "", textContent: "", disabled: false,
        setAttribute() {},
        append(...children) { this.children.push(...children); },
        appendChild(child) { this.children.push(child); },
        addEventListener(name, handler) { this.listeners[name] = handler; }
    };
}

function page(fetch) {
    const nodes = { "categories-message": element("p"), "category-editors": element("div") };
    const context = vm.createContext({
        document: { createElement: element, getElementById: id => nodes[id] },
        fetch, URLSearchParams
    });
    vm.runInContext(source, context);
    return { nodes, context };
}

function editor(save) {
    let calls = 0;
    const { context } = page((url, options) => {
        if (!options) return new Promise(() => {});
        calls++;
        return save(url, options);
    });
    const form = vm.runInContext('categoryEditor({id:"category-1",name:"Workshop",description:"Original"})', context);
    const [heading, , name, , description, button, message] = form.children;
    const submit = () => form.listeners.submit({ preventDefault() {} });
    return { heading, name, description, button, message, submit, calls: () => calls };
}

test("blank category name is rejected without saving", async () => {
    const ui = editor(() => { throw new Error("Should not save"); });
    ui.name.value = " \t ";
    await ui.submit();
    assert.equal(ui.calls(), 0);
    assert.match(ui.message.textContent, /Enter a category name/);
});

test("successful update sends the existing ID and shows saved values", async () => {
    const ui = editor(async (url, options) => {
        assert.equal(url, "/api/config/event-categories/category-1");
        assert.equal(options.method, "PUT");
        assert.equal(options.body.get("name"), " New name ");
        assert.equal(options.body.get("description"), "Updated description");
        return { ok: true, json: async () => ({ id: "category-1", name: "New name", description: "Updated description" }) };
    });
    ui.name.value = " New name ";
    ui.description.value = "Updated description";
    await ui.submit();
    assert.equal(ui.name.value, "New name");
    assert.equal(ui.heading.textContent, "New name");
    assert.match(ui.message.textContent, /saved successfully/);
    assert.equal(ui.button.disabled, false);
});

for (const failure of ["duplicate", "server", "network", "invalid JSON"]) {
    test(`${failure} save failure keeps both edits and allows a retry`, async () => {
        let fail = true;
        const ui = editor(async () => {
            if (!fail) return { ok: true, json: async () => ({ id: "category-1", name: "My edit", description: "Keep me" }) };
            if (failure === "network") throw new Error("Network unavailable");
            return {
                ok: false,
                json: async () => {
                    if (failure === "invalid JSON") throw new Error("Invalid response");
                    return { error: failure === "duplicate" ? "A category with that name already exists." : "Could not save." };
                }
            };
        });
        ui.name.value = "My edit";
        ui.description.value = "Keep me";
        await ui.submit();
        assert.equal(ui.name.value, "My edit");
        assert.equal(ui.description.value, "Keep me");
        assert.equal(ui.heading.textContent, "Workshop");
        assert.ok(ui.message.textContent && ui.message.textContent !== "Saving...");
        assert.equal(ui.button.disabled, false);
        assert.equal(ui.name.disabled, false);
        assert.equal(ui.description.disabled, false);
        fail = false;
        await ui.submit();
        assert.equal(ui.calls(), 2);
        assert.match(ui.message.textContent, /saved successfully/);
    });
}

test("pending save prevents duplicate submissions", async () => {
    let finish;
    const ui = editor(() => new Promise(resolve => { finish = resolve; }));
    const pending = ui.submit();
    await ui.submit();
    assert.equal(ui.calls(), 1);
    assert.equal(ui.name.disabled, true);
    finish({ ok: true, json: async () => ({ id: "category-1", name: "Workshop", description: "Original" }) });
    await pending;
    assert.equal(ui.button.disabled, false);
});

test("empty category list and load failure display messages", async () => {
    const empty = page(async () => ({ ok: true, json: async () => ({ categories: [] }) }));
    await new Promise(resolve => setImmediate(resolve));
    assert.match(empty.nodes["categories-message"].textContent, /No event categories/);
    const failed = page(async () => ({ ok: false }));
    await new Promise(resolve => setImmediate(resolve));
    assert.match(failed.nodes["categories-message"].textContent, /Could not load/);
});
