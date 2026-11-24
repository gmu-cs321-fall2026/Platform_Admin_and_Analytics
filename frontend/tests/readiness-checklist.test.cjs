const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { test } = require("node:test");
const source = fs.readFileSync(path.join(__dirname, "../src/readiness-checklist.js"), "utf8");

function element(tag) {
    return {
        tag, children: [], listeners: {}, value: "", textContent: "", hidden: false, disabled: false,
        setAttribute(name, value) { this[name] = value; },
        append(...children) { children.forEach(child => this.appendChild(child)); },
        appendChild(child) { child.parent = this; this.children.push(child); },
        replaceChildren() { this.children.forEach(child => { child.parent = null; }); this.children = []; },
        remove() { this.parent.children = this.parent.children.filter(child => child !== this); this.parent = null; },
        querySelectorAll(tag) {
            return this.children.flatMap(child => [...(child.tag === tag ? [child] : []), ...child.querySelectorAll(tag)]);
        },
        querySelector(tag) { return this.querySelectorAll(tag)[0]; },
        addEventListener(name, handler) { this.listeners[name] = handler; },
        focus() { this.focused = true; }
    };
}

async function page(items = [], save = async () => { throw new Error("Unexpected save"); }, loadError = false) {
    const ids = ["checklist-items", "checklist-empty", "checklist-add", "checklist-fields",
        "checklist-form", "checklist-message", "checklist-load-message"];
    const nodes = Object.fromEntries(ids.map(id => [id, element("div")]));
    nodes["checklist-form"].hidden = true;
    let calls = 0;
    const context = vm.createContext({
        document: { createElement: element, getElementById: id => nodes[id] }, URLSearchParams,
        fetch: async (url, options) => {
            assert.equal(url, "/api/config/readiness-checklist-templates/default");
            if (options) { calls++; return save(options); }
            return { ok: !loadError, json: async () => ({ id: "default", items }) };
        }
    });
    vm.runInContext(source, context);
    await new Promise(resolve => setImmediate(resolve));
    return {
        nodes,
        inputs: () => nodes["checklist-items"].querySelectorAll("textarea"),
        add: () => nodes["checklist-add"].listeners.click(),
        remove: index => nodes["checklist-items"].children[index].querySelector("button").listeners.click(),
        submit: () => nodes["checklist-form"].listeners.submit({ preventDefault() {} }),
        calls: () => calls
    };
}

test("loads saved checklist items in order", async () => {
    const ui = await page(["First", "Second"]);
    assert.deepEqual(ui.inputs().map(input => input.value), ["First", "Second"]);
    assert.equal(ui.nodes["checklist-form"].hidden, false);
    assert.equal(ui.nodes["checklist-empty"].hidden, true);
});

test("adds, edits, and removes items before saving in their displayed order", async () => {
    const ui = await page(["First", "Remove me", "Third"], async options => {
        assert.equal(options.method, "PUT");
        assert.equal(options.body.get("itemCount"), "3");
        assert.equal(options.body.get("item0"), "Updated first");
        assert.equal(options.body.get("item1"), "Third");
        assert.equal(options.body.get("item2"), "New last item");
        return { ok: true, json: async () => ({ id: "default", items: ["Updated first", "Third", "New last item"] }) };
    });
    ui.inputs()[0].value = "Updated first";
    ui.remove(1);
    ui.add();
    ui.inputs()[2].value = "New last item";
    const labels = ui.nodes["checklist-items"].querySelectorAll("label").map(label => label.textContent);
    assert.deepEqual(labels, ["Item 1", "Item 2", "Item 3"]);
    await ui.submit();
    assert.match(ui.nodes["checklist-message"].textContent, /saved/);
});

test("rejects blank items without a save request", async () => {
    const ui = await page(["First"]);
    ui.add();
    ui.inputs()[1].value = " \t\n ";
    await ui.submit();
    assert.equal(ui.calls(), 0);
    assert.match(ui.nodes["checklist-message"].textContent, /cannot be blank/);
});

test("allows removing all items and saving the empty template", async () => {
    const ui = await page(["First"], async options => {
        assert.equal(options.body.get("itemCount"), "0");
        return { ok: true, json: async () => ({ id: "default", items: [] }) };
    });
    ui.remove(0);
    assert.equal(ui.nodes["checklist-empty"].hidden, false);
    await ui.submit();
    assert.match(ui.nodes["checklist-message"].textContent, /saved/);
});

for (const failure of ["server", "network", "invalid response"]) {
    test(`${failure} failure retains additions, edits, and removals for retry`, async () => {
        let fail = true;
        const ui = await page(["Original", "Remove me"], async () => {
            if (!fail) return { ok: true, json: async () => ({ id: "default", items: ["Edited", "Added"] }) };
            if (failure === "network") throw new Error("Network unavailable");
            if (failure === "invalid response") return { ok: true, json: async () => ({ id: "default", items: [] }) };
            return { ok: false, json: async () => ({ error: "Could not save the checklist template." }) };
        });
        ui.inputs()[0].value = "Edited";
        ui.remove(1);
        ui.add();
        ui.inputs()[1].value = "Added";
        await ui.submit();
        assert.deepEqual(ui.inputs().map(input => input.value), ["Edited", "Added"]);
        assert.equal(ui.nodes["checklist-fields"].disabled, false);
        assert.match(ui.nodes["checklist-message"].textContent, /Could not|Network/);
        fail = false;
        await ui.submit();
        assert.equal(ui.calls(), 2);
        assert.match(ui.nodes["checklist-message"].textContent, /saved/);
    });
}

test("pending save blocks duplicate requests and list changes", async () => {
    let finish;
    const ui = await page(["First"], () => new Promise(resolve => { finish = resolve; }));
    const pending = ui.submit();
    await ui.submit();
    ui.add();
    ui.remove(0);
    assert.equal(ui.calls(), 1);
    assert.equal(ui.nodes["checklist-fields"].disabled, true);
    assert.deepEqual(ui.inputs().map(input => input.value), ["First"]);
    finish({ ok: true, json: async () => ({ id: "default", items: ["First"] }) });
    await pending;
    assert.equal(ui.nodes["checklist-fields"].disabled, false);
});

test("load failure displays an error and keeps the editor hidden", async () => {
    const ui = await page([], undefined, true);
    assert.equal(ui.nodes["checklist-form"].hidden, true);
    assert.match(ui.nodes["checklist-load-message"].textContent, /Could not load/);
});
