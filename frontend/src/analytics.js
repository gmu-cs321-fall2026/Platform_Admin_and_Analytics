let statisticsRequestId = 0;

async function loadUsageStatistics(startDate = "", endDate = "") {
    const requestId = ++statisticsRequestId;

    const fields = {
        newAccounts: "usage-new-accounts",
        eventsCreated: "usage-events-created",
        eventRegistrations: "usage-event-registrations",
        moderationDecisions: "usage-moderation-decisions"
    };

    let url = "/api/admin/analytics/overview";
    const params = new URLSearchParams();

    if (startDate) {
        params.set("startDate", startDate);
    }

    if (endDate) {
        params.set("endDate", endDate);
    }

    if (params.toString()) {
        url += "?" + params.toString();
    }

    try {
        const response = await fetch(url);

        if (!response.ok) {
            throw new Error("Unable to load usage statistics.");
        }

        const totals = await response.json();

        if (requestId !== statisticsRequestId) return;

        for (const [metric, id] of Object.entries(fields)) {
            const value = totals[metric];

            document.getElementById(id).textContent =
                Number.isSafeInteger(value) && value >= 0
                    ? value.toLocaleString("en-US")
                    : "Unavailable";
        }
    } catch (error) {
        if (requestId !== statisticsRequestId) return;

        for (const id of Object.values(fields)) {
            document.getElementById(id).textContent = "Unavailable";
        }
    }
}

// Apply date filter
function applyStatisticsDateFilter(event) {
    event.preventDefault();

    const startInput = document.getElementById("statistics-start-date");
    const endInput = document.getElementById("statistics-end-date");
    const error = document.getElementById("statistics-filter-error");

    const startDate = startInput.value;
    const endDate = endInput.value;

    error.hidden = true;
    error.textContent = "";

    if (!startInput.validity.valid || !endInput.validity.valid) {
        error.textContent = "Enter valid start and end dates.";
        error.hidden = false;
        return;
    }

    if (startDate && endDate && startDate > endDate) {
        error.textContent = "Start date cannot be after the end date.";
        error.hidden = false;
        return;
    }

    loadUsageStatistics(startDate, endDate);
}

// Clear date filter
function clearStatisticsDateFilter() {
    document.getElementById("statistics-date-filter").reset();

    const error = document.getElementById("statistics-filter-error");

    error.hidden = true;
    error.textContent = "";

    loadUsageStatistics();
}

// Initialize dashboard
if (["/admin", ""].includes(window.location.pathname.replace(/\/+$/, ""))) {
    const filter = document.getElementById("statistics-date-filter");
    const clearButton = document.getElementById("clear-statistics-filter");

    if (filter && clearButton) {
        filter.addEventListener("submit", applyStatisticsDateFilter);
        clearButton.addEventListener("click", clearStatisticsDateFilter);
    }

    loadUsageStatistics();
}
