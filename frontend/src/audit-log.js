let auditPage = 1;
let auditTotal = 0;
let auditRequestNumber = 0;

const auditPageSize = 20;

let appliedAuditFilters = {
    action: "",
    from: "",
    to: ""
};

const actionLabels = {
    ROLE_CHANGED: "Role changed",
    ACCOUNT_SUSPENDED: "Account suspended",
    ACCOUNT_UNSUSPENDED: "Account unsuspended",
    POST_REMOVED: "Post removed",
    USER_WARNED: "User warned",
    FLAG_DISMISSED: "Flag dismissed",
    RESOURCE_APPROVED: "Resource approved",
    RESOURCE_REJECTED: "Resource rejected",
    QUESTION_APPROVED: "Practice question approved",
    QUESTION_REJECTED: "Practice question rejected"
};

function updateAuditPagination(loading = false) {
    document.getElementById("audit-previous").disabled =
        loading || auditPage <= 1;

    document.getElementById("audit-next").disabled =
        loading || auditPage * auditPageSize >= auditTotal;

    document.getElementById("audit-page").textContent =
        `Page ${auditPage} · ${auditTotal} matching entries`;
}

function appendAuditCell(row, value) {
    const cell = document.createElement("td");
    cell.textContent = value || "—";
    row.appendChild(cell);
}

function displayAuditEntries(entries) {
    const container = document.getElementById("audit-entries");
    container.replaceChildren();

    if (entries.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 7;
        cell.className = "loading";
        cell.textContent = "No audit entries match the filters.";

        row.appendChild(cell);
        container.appendChild(row);
        return;
    }

    for (const entry of entries) {
        const row = document.createElement("tr");

        const timestamp = new Date(entry.timestamp)
            .toLocaleString("en-US", {
                timeZone: "America/New_York",
                year: "numeric",
                month: "short",
                day: "numeric",
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit",
                timeZoneName: "short"
            });

        const change = entry.previousValue || entry.newValue
            ? `${entry.previousValue || "—"} → ${entry.newValue || "—"}`
            : "";

        appendAuditCell(row, timestamp);

        appendAuditCell(
            row,
            actionLabels[entry.action] || entry.action
        );

        appendAuditCell(row, String(entry.targetId ?? ""));
        appendAuditCell(row, entry.targetName);
        appendAuditCell(row, entry.adminId);
        appendAuditCell(row, change);
        appendAuditCell(row, entry.reason);

        container.appendChild(row);
    }
}

async function loadAuditLog() {
    const requestNumber = ++auditRequestNumber;
    const message = document.getElementById("audit-message");

    updateAuditPagination(true);
    message.textContent = "Loading audit log...";

    const query = new URLSearchParams({
        page: String(auditPage),
        pageSize: String(auditPageSize)
    });

    for (const [key, value] of Object.entries(
        appliedAuditFilters
    )) {
        if (value) {
            query.set(key, value);
        }
    }

    try {
        const response = await fetch(
            `/api/admin/audit-log?${query}`,
            { cache: "no-store" }
        );

        const result = await response.json();

        if (requestNumber !== auditRequestNumber) {
            return;
        }

        if (!response.ok) {
            throw new Error(
                result.error || "Could not load the audit log."
            );
        }

        auditTotal = result.total;

        displayAuditEntries(result.entries);
        updateAuditPagination();

        message.textContent = "";
    } catch (error) {
        if (requestNumber !== auditRequestNumber) {
            return;
        }

        document.getElementById("audit-entries")
            .replaceChildren();

        document.getElementById("audit-previous").disabled = true;
        document.getElementById("audit-next").disabled = true;

        message.textContent =
            error.message
            || "Could not load the audit log. Please try again.";
    }
}

document.getElementById("audit-filters")
    .addEventListener("submit", event => {
        event.preventDefault();

        const from = document.getElementById("audit-from").value;
        const to = document.getElementById("audit-to").value;

        if (from && to && from > to) {
            document.getElementById("audit-message").textContent =
                "Start date must not be after end date.";
            return;
        }

        appliedAuditFilters = {
            action: document.getElementById("audit-action").value,
            from,
            to
        };

        auditPage = 1;
        loadAuditLog();
    });

document.getElementById("audit-clear")
    .addEventListener("click", () => {
        document.getElementById("audit-filters").reset();

        appliedAuditFilters = {
            action: "",
            from: "",
            to: ""
        };

        auditPage = 1;
        loadAuditLog();
    });

document.getElementById("audit-previous")
    .addEventListener("click", () => {
        if (auditPage > 1) {
            auditPage--;
            loadAuditLog();
        }
    });

document.getElementById("audit-next")
    .addEventListener("click", () => {
        if (auditPage * auditPageSize < auditTotal) {
            auditPage++;
            loadAuditLog();
        }
    });

loadAuditLog();