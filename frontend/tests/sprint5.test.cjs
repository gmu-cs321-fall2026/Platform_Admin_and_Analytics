const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { test } = require("node:test");
const vm = require("node:vm");
const read = name => fs.readFileSync(path.join(__dirname, "../src", name), "utf8");

test("every page keeps all navigation destinations and unique element IDs", () => {
    const routes = ["/admin", "/admin/users", "/admin/moderation", "/admin/audit-log",
        "/admin/announcements", "/admin/settings", "/admin/event-categories",
        "/admin/readiness-checklist", "/admin/notification-templates"];
    for (const file of fs.readdirSync(path.join(__dirname, "../src")).filter(name => name.endsWith(".html"))) {
        const html = read(file);
        const nav = html.match(/<nav\b[^>]*>([\s\S]*?)<\/nav>/)[1];
        for (const route of routes) assert.ok(nav.includes(`href="${route}"`), `${file} lost ${route}`);
        const ids = Array.from(html.matchAll(/\bid="([^"]+)"/g), match => match[1]);
        assert.equal(new Set(ids).size, ids.length, `${file} has duplicate IDs`);
    }
});

test("date controls refresh both panels, reject reversed ranges, and clear the filter", () => {
    const nodes = {
        "statistics-start-date": { value: "2026-11-01", validity: { badInput: false, valid: true } },
        "statistics-end-date": { value: "2026-11-23", validity: { badInput: false, valid: true } },
        "statistics-filter-error": {}, "statistics-date-filter": { reset() {} }
    };
    const calls = [];
    const context = vm.createContext({ document: { getElementById: id => nodes[id] }, window: {
        loadUsageStatistics: range => calls.push({ panel: "totals", ...range }),
        loadWeeklyRegistrations: range => calls.push({ panel: "weekly", ...range })
    } });
    const app = read("app.js");
    vm.runInContext(app.slice(app.indexOf("let appliedStatisticsDates"), app.indexOf('\ndocument.getElementById("statistics-date-filter")')), context);
    vm.runInContext("applyStatisticsDateFilter({preventDefault(){}})", context);
    assert.deepEqual(calls, [
        { panel: "totals", startDate: "2026-11-01", endDate: "2026-11-23" },
        { panel: "weekly", startDate: "2026-11-01", endDate: "2026-11-23" }
    ]);
    nodes["statistics-start-date"].value = "2026-12-01";
    vm.runInContext("applyStatisticsDateFilter({preventDefault(){}})", context);
    assert.equal(calls.length, 2);
    assert.match(nodes["statistics-filter-error"].textContent, /after/);
    vm.runInContext("clearStatisticsDateFilter()", context);
    assert.equal(calls[2].startDate, "");
    assert.equal(calls[3].endDate, "");
});

test("statistics requests forward dates and explain missing weekly data", async () => {
    const nodes = Object.fromEntries(["usage-new-accounts", "usage-events-created", "usage-event-registrations",
        "usage-moderation-decisions", "weekly-registrations-message", "statistics-source-note", "statistics-range-description"]
        .map(id => [id, {}]));
    const calls = [];
    const context = vm.createContext({ URLSearchParams, window: { location: { pathname: "/admin/users" } },
        document: { getElementById: id => nodes[id] }, fetch: async url => {
            calls.push(url);
            return url.includes("weekly") ? { ok: false, json: async () => ({error: "Shared Core registration history unavailable."}) }
                : { ok: true, json: async () => ({newAccounts:null, eventsCreated:null, eventRegistrations:null, moderationDecisions:2}) };
        } });
    vm.runInContext(read("analytics.js"), context);
    await vm.runInContext('Promise.all([loadUsageStatistics({startDate:"2026-11-01",endDate:"2026-11-23"}),loadWeeklyRegistrations({startDate:"2026-11-01"})])', context);
    assert.ok(calls[0].endsWith("?startDate=2026-11-01&endDate=2026-11-23"));
    assert.equal(nodes["usage-moderation-decisions"].textContent, "2");
    assert.equal(nodes["usage-new-accounts"].textContent, "Unavailable");
    assert.match(nodes["weekly-registrations-message"].textContent, /Shared Core/);
});

test("saved moderation decision still reports a subsequent queue refresh failure", async () => {
    const nodes = Object.fromEntries(["detail-queue-id", "moderation-decision-error", "moderation-save-decision",
        "moderation-back", "moderation-decision-reason", "moderation-action", "moderation-message"].map(id => [id, {}]));
    nodes["detail-queue-id"].textContent = "MOD-003";
    nodes["moderation-decision-reason"].value = "Reviewed";
    nodes["moderation-action"].value = "RESOURCE_APPROVED";
    const context = vm.createContext({ URLSearchParams, document: { getElementById: id => nodes[id] },
        backToModerationQueue() {}, loadModerationQueue: async () => false,
        fetch: async () => ({ ok: true, json: async () => ({status:"RESOLVED"}) }) });
    const source = read("moderation.js");
    vm.runInContext(source.slice(source.indexOf("async function saveModerationDecision"), source.indexOf("async function loadModerationQueue")), context);
    await vm.runInContext("saveModerationDecision({preventDefault(){}})", context);
    assert.match(nodes["moderation-message"].textContent, /Decision saved, but/);
    assert.equal(nodes["moderation-save-decision"].disabled, false);
});
