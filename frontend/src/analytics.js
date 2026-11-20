async function loadUsageStatistics() {
    const fields = {
        newAccounts: "usage-new-accounts",
        eventsCreated: "usage-events-created",
        eventRegistrations: "usage-event-registrations",
        moderationDecisions: "usage-moderation-decisions"
    };

    try {
        const response = await fetch("/api/admin/analytics/overview");
        if (!response.ok) throw new Error("Unable to load usage statistics.");
        const totals = await response.json();
        for (const [metric, id] of Object.entries(fields)) {
            const value = totals[metric];
            document.getElementById(id).textContent = Number.isSafeInteger(value) && value >= 0
                ? value.toLocaleString("en-US")
                : "Unavailable";
        }
    } catch (error) {
        for (const id of Object.values(fields)) {
            document.getElementById(id).textContent = "Unavailable";
        }
    }
}

if (["/admin", ""].includes(window.location.pathname.replace(/\/+$/, ""))) {
    loadUsageStatistics();
}
