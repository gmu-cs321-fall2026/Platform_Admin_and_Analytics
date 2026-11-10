let moderationPage = 1;
let moderationTotal = 0;
let moderationRequestNumber = 0;
let visibleModerationItems = [];
let lastModerationViewButton = null;

const moderationPageSize = 20;

let appliedModerationFilters = {
    type: "",
    status: ""
};

const moderationTypeLabels = {
    FLAGGED_POST: "Flagged post",
    ACCOUNT_FLAG: "Account flag",
    RESOURCE: "Resource",
    PRACTICE_QUESTION: "Practice question"
};

const moderationStatusLabels = {
    PENDING: "Pending review",
    RESOLVED: "Resolved"
};

const moderationActionLabels = {
    POST_REMOVED: "Remove post",
    USER_WARNED: "Warn user",
    FLAG_DISMISSED: "Dismiss flag",
    ACCOUNT_SUSPENDED: "Suspend account",
    RESOURCE_APPROVED: "Approve resource",
    RESOURCE_REJECTED: "Reject resource",
    QUESTION_APPROVED: "Approve practice question",
    QUESTION_REJECTED: "Reject practice question"
};

function formatModerationDate(value) {
    return new Date(value).toLocaleString("en-US", {
        timeZone: "America/New_York",
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        timeZoneName: "short"
    });
}

function appendModerationCell(row, value) {
    const cell = document.createElement("td");

    cell.textContent =
        value === null || value === undefined || value === ""
            ? "—"
            : String(value);

    row.appendChild(cell);

    return cell;
}

function updateModerationPagination(loading = false) {
    document.getElementById("moderation-previous").disabled =
        loading || moderationPage <= 1;

    document.getElementById("moderation-next").disabled =
        loading
        || moderationPage * moderationPageSize >= moderationTotal;

    document.getElementById("moderation-page").textContent =
        `Page ${moderationPage} · ${moderationTotal} matching items`;
}

function displayModerationItems(items) {
    const container = document.getElementById("moderation-items");
    container.replaceChildren();

    if (items.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");

        cell.colSpan = 7;
        cell.className = "loading";
        cell.textContent = "No moderation items match the filters.";

        row.appendChild(cell);
        container.appendChild(row);
        return;
    }

    for (const item of items) {
        const row = document.createElement("tr");

        appendModerationCell(
            row,
            formatModerationDate(item.submittedAt)
        );

        appendModerationCell(row, item.id);

        appendModerationCell(
            row,
            moderationTypeLabels[item.type] || item.type
        );

        appendModerationCell(row, item.title);
        appendModerationCell(row, item.reason);

        const statusCell = document.createElement("td");
        const badge = document.createElement("span");

        badge.className = "review-badge";

        if (item.status === "PENDING") {
            badge.classList.add("review-pending");
        } else if (item.status === "RESOLVED") {
            badge.classList.add("review-resolved");
        }

        badge.textContent =
            moderationStatusLabels[item.status] || item.status;

        statusCell.appendChild(badge);
        row.appendChild(statusCell);

        const actionCell = document.createElement("td");
        const viewButton = document.createElement("button");

        viewButton.type = "button";
        viewButton.className = "view-button";
        viewButton.textContent = "View";

        viewButton.setAttribute(
            "aria-label",
            `View details for ${item.title}`
        );

        viewButton.addEventListener("click", () => {
            lastModerationViewButton = viewButton;
            openModerationItem(item.id);
        });

        actionCell.appendChild(viewButton);
        row.appendChild(actionCell);

        container.appendChild(row);
    }
}

function openModerationItem(id) {
    const item = visibleModerationItems.find(
        current => current.id === id
    );

    if (!item) {
        return;
    }

    document.getElementById("detail-queue-id").textContent =
        item.id;

    document.getElementById("detail-item-type").textContent =
        moderationTypeLabels[item.type] || item.type;

    document.getElementById("detail-item-source").textContent =
        item.source;

    document.getElementById("detail-target-id").textContent =
        item.targetId;

    document.getElementById("detail-item-title").textContent =
        item.title;

    document.getElementById("detail-item-date").textContent =
        formatModerationDate(item.submittedAt);

    document.getElementById("detail-item-reason").textContent =
        item.reason || "No reason provided.";

    document.getElementById("detail-review-status").textContent =
        moderationStatusLabels[item.status] || item.status;

    document.getElementById("detail-item-content").textContent =
        item.details || "No additional details available.";

    const form = document.getElementById("moderation-decision-form");
    const action = document.getElementById("moderation-action");
    form.reset();
    action.replaceChildren();
    for (const value of item.allowedActions) {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = moderationActionLabels[value];
        action.appendChild(option);
    }
    form.hidden = item.allowedActions.length === 0;
    document.getElementById("moderation-decision-error").hidden = true;
    document.getElementById("moderation-result").textContent = "";
    const savedDecision = document.getElementById("moderation-saved-decision");
    savedDecision.hidden = !item.decision;
    if (item.decision) {
        savedDecision.textContent = [
            moderationActionLabels[item.decision.action] || item.decision.action,
            item.decision.adminId,
            formatModerationDate(item.decision.decidedAt),
            item.decision.reason
        ].join(" · ");
    }

    document.getElementById("moderation-list").hidden = true;
    document.getElementById("moderation-details").hidden = false;

    document.getElementById("moderation-detail-title").focus();
}

function backToModerationQueue() {
    document.getElementById("moderation-details").hidden = true;
    document.getElementById("moderation-list").hidden = false;

    if (lastModerationViewButton?.isConnected) {
        lastModerationViewButton.focus();
    }
}

async function saveModerationDecision(event) {
    event.preventDefault();
    const id = document.getElementById("detail-queue-id").textContent;
    const error = document.getElementById("moderation-decision-error");
    const button = document.getElementById("moderation-save-decision");
    const back = document.getElementById("moderation-back");
    const reason = document.getElementById("moderation-decision-reason").value.trim();
    if (!reason) {
        error.textContent = "Enter a reason for the moderation decision.";
        error.hidden = false;
        return;
    }
    button.disabled = true;
    back.disabled = true;
    error.hidden = true;
    try {
        const response = await fetch("/api/admin/moderation/" + encodeURIComponent(id) + "/decision", {
            method: "POST",
            body: new URLSearchParams({
                action: document.getElementById("moderation-action").value,
                reason
            })
        });
        const result = await response.json();
        if (!response.ok) {
            throw new Error(result.error || "Could not save the moderation decision.");
        }
        
        backToModerationQueue();
        await loadModerationQueue();
        document.getElementById("moderation-message").textContent =
            "Moderation decision saved successfully.";
        
    } catch (failure) {
        error.textContent = failure.message || "Could not save the moderation decision.";
        error.hidden = false;
    } finally {
        button.disabled = false;
        back.disabled = false;
    }
}

async function loadModerationQueue() {
    const requestNumber = ++moderationRequestNumber;

    const message =
        document.getElementById("moderation-message");

    updateModerationPagination(true);
    message.textContent = "Loading moderation queue...";

    const query = new URLSearchParams({
        page: String(moderationPage),
        pageSize: String(moderationPageSize)
    });

    for (const [key, value] of Object.entries(
        appliedModerationFilters
    )) {
        if (value) {
            query.set(key, value);
        }
    }

    try {
        const response = await fetch(
            `/api/admin/moderation?${query}`,
            { cache: "no-store" }
        );

        const result = await response.json();

        if (requestNumber !== moderationRequestNumber) {
            return;
        }

        if (!response.ok) {
            throw new Error(
                result.error || "Could not load the moderation queue."
            );
        }

        visibleModerationItems = result.items;
        moderationTotal = result.total;

        document.getElementById("moderation-demo-note").hidden =
            !result.demoData;

        displayModerationItems(visibleModerationItems);
        updateModerationPagination();

        message.textContent = "";
    } catch (error) {
        if (requestNumber !== moderationRequestNumber) {
            return;
        }

        visibleModerationItems = [];
        moderationTotal = 0;

        document.getElementById("moderation-items")
            .replaceChildren();

        document.getElementById("moderation-previous").disabled =
            true;

        document.getElementById("moderation-next").disabled =
            true;

        document.getElementById("moderation-page").textContent =
            "Queue unavailable";

        message.textContent =
            error.message
            || "Could not load the moderation queue. Please try again.";
    }
}

document.getElementById("moderation-filters")
    .addEventListener("submit", event => {
        event.preventDefault();

        appliedModerationFilters = {
            type: document.getElementById("moderation-type").value,
            status: document.getElementById("moderation-status").value
        };

        moderationPage = 1;
        loadModerationQueue();
    });

document.getElementById("moderation-clear")
    .addEventListener("click", () => {
        document.getElementById("moderation-filters").reset();

        appliedModerationFilters = {
            type: "",
            status: ""
        };

        moderationPage = 1;
        loadModerationQueue();
    });

document.getElementById("moderation-previous")
    .addEventListener("click", () => {
        if (moderationPage > 1) {
            moderationPage--;
            loadModerationQueue();
        }
    });

document.getElementById("moderation-next")
    .addEventListener("click", () => {
        if (
            moderationPage * moderationPageSize
            < moderationTotal
        ) {
            moderationPage++;
            loadModerationQueue();
        }
    });

document.getElementById("moderation-back")
    .addEventListener("click", backToModerationQueue);

document.getElementById("moderation-decision-form")
    .addEventListener("submit", saveModerationDecision);

loadModerationQueue();
