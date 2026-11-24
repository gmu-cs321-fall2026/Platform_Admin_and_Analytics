let statisticsRequestNumber = 0;
let weeklyRequestNumber = 0;

function statisticsQuery(range = {}) {
    const params = new URLSearchParams();
    if (range.startDate) params.set("startDate", range.startDate);
    if (range.endDate) params.set("endDate", range.endDate);
    return params.size ? `?${params}` : "";
}

async function loadWeeklyRegistrations(range = {}) {
    const message = document.getElementById("weekly-registrations-message");
    if (!message) return;
    const requestNumber = ++weeklyRequestNumber;
    message.textContent = "Loading registration data...";
    try {
        const response = await fetch("/api/admin/analytics/weekly-registrations" + statisticsQuery(range));
        const result = await response.json();
        if (requestNumber !== weeklyRequestNumber) return;
        if (!response.ok) throw new Error(result.error || "Weekly registration data could not be loaded.");
        // This route deliberately returns 503 until a real registration source exists.
        throw new Error("Weekly registration data is unavailable.");
    } catch (error) {
        if (requestNumber === weeklyRequestNumber) message.textContent = error.message || "Weekly registration data could not be loaded.";
    }
}

async function loadUsageStatistics(range = {}) {
    const requestNumber = ++statisticsRequestNumber;
    const description = document.getElementById("statistics-range-description");
    if (description) description.textContent = range.startDate || range.endDate
        ? `Activity from ${range.startDate || "the beginning"} through ${range.endDate || "the latest available record"}, inclusive (Eastern time).`
        : "Totals for all available records. Event registrations reflect current registrations.";
    const note = document.getElementById("statistics-source-note");
    if (note) note.textContent = range.startDate || range.endDate
        ? "The selected range applies to saved moderation decisions. Account and Events totals are Unavailable until their APIs support the required dates."
        : "Account totals need Shared Core. Events totals need a configured Events API.";
    const fields = {
        newAccounts: "usage-new-accounts",
        eventsCreated: "usage-events-created",
        eventRegistrations: "usage-event-registrations",
        moderationDecisions: "usage-moderation-decisions"
    };

    try {
        const response = await fetch("/api/admin/analytics/overview" + statisticsQuery(range));
        if (!response.ok) throw new Error("Unable to load usage statistics.");
        const totals = await response.json();
        if (requestNumber !== statisticsRequestNumber) return;
        for (const [metric, id] of Object.entries(fields)) {
            const value = totals[metric];
            document.getElementById(id).textContent = Number.isSafeInteger(value) && value >= 0
                ? value.toLocaleString("en-US")
                : "Unavailable";
        }
    } catch (error) {
        if (requestNumber !== statisticsRequestNumber) return;
        for (const id of Object.values(fields)) {
            document.getElementById(id).textContent = "Unavailable";
        }
    }
}

window.loadUsageStatistics = loadUsageStatistics;
window.loadWeeklyRegistrations = loadWeeklyRegistrations;

if (["/admin", ""].includes(window.location.pathname.replace(/\/+$/, ""))) {
    Promise.all([loadUsageStatistics(), loadWeeklyRegistrations()]);
}
