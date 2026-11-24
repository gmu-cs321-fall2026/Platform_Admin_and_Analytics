const checklistApi = "/api/config/readiness-checklist-templates/default";
let checklistSaving = false;
let checklistFieldId = 0;

function updateChecklistLabels() {
    const rows = document.getElementById("checklist-items").children;
    Array.from(rows).forEach((row, index) => {
        row.querySelector("label").textContent = `Item ${index + 1}`;
        row.querySelector("button").setAttribute("aria-label", `Remove item ${index + 1}`);
    });
    document.getElementById("checklist-empty").hidden = rows.length !== 0;
    document.getElementById("checklist-add").disabled = rows.length >= 200;
}

function addChecklistItem(text = "", focus = false) {
    if (checklistSaving || document.getElementById("checklist-items").children.length >= 200) return;
    const row = document.createElement("li");
    row.className = "checklist-item";
    const label = document.createElement("label");
    const input = document.createElement("textarea");
    input.id = `checklist-item-${++checklistFieldId}`;
    input.value = text;
    input.required = true;
    input.maxLength = 1000;
    input.rows = 2;
    label.htmlFor = input.id;
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "view-button";
    remove.textContent = "Remove";
    remove.addEventListener("click", () => {
        if (checklistSaving) return;
        row.remove();
        updateChecklistLabels();
        document.getElementById("checklist-message").textContent = "Unsaved changes.";
    });
    input.addEventListener("input", () => {
        document.getElementById("checklist-message").textContent = "Unsaved changes.";
    });
    row.append(label, input, remove);
    document.getElementById("checklist-items").appendChild(row);
    updateChecklistLabels();
    if (focus) input.focus();
}

function validChecklistTemplate(template) {
    return template?.id === "default" && Array.isArray(template.items)
        && template.items.length <= 200 && template.items.every(item =>
            typeof item === "string" && item.trim() && item.length <= 1000);
}

function displayChecklist(items) {
    document.getElementById("checklist-items").replaceChildren();
    items.forEach(item => addChecklistItem(item));
    updateChecklistLabels();
}

async function loadChecklistTemplate() {
    const message = document.getElementById("checklist-load-message");
    try {
        const response = await fetch(checklistApi);
        const template = await response.json();
        if (!response.ok || !validChecklistTemplate(template)) {
            throw new Error("Could not load the checklist template. Please refresh to try again.");
        }
        displayChecklist(template.items);
        document.getElementById("checklist-form").hidden = false;
        message.textContent = "";
    } catch (error) {
        message.textContent = error.message || "Could not load the checklist template. Please refresh to try again.";
    }
}

async function saveChecklistTemplate(event) {
    event.preventDefault();
    if (checklistSaving) return;
    const message = document.getElementById("checklist-message");
    const inputs = Array.from(document.getElementById("checklist-items").querySelectorAll("textarea"));
    if (inputs.some(input => !input.value.trim())) {
        message.textContent = "Checklist items cannot be blank.";
        return;
    }
    const body = new URLSearchParams({ itemCount: String(inputs.length) });
    inputs.forEach((input, index) => body.set(`item${index}`, input.value));
    checklistSaving = true;
    document.getElementById("checklist-fields").disabled = true;
    message.textContent = "Saving...";
    try {
        const response = await fetch(checklistApi, {
            method: "PUT",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body
        });
        const template = await response.json();
        if (!response.ok) throw new Error(template.error || "Could not save the checklist template. Please try again.");
        if (!validChecklistTemplate(template) || template.items.length !== inputs.length) throw new Error("Could not confirm the saved template. Please try again.");
        inputs.forEach((input, index) => { input.value = template.items[index]; });
        message.textContent = "Checklist template saved.";
    } catch (error) {
        // Keep added, edited, and removed items exactly as entered for a retry.
        message.textContent = error.message || "Could not save the checklist template. Please try again.";
    } finally {
        checklistSaving = false;
        document.getElementById("checklist-fields").disabled = false;
    }
}

document.getElementById("checklist-add").addEventListener("click", () => {
    addChecklistItem("", true);
    document.getElementById("checklist-message").textContent = "Unsaved changes.";
});
document.getElementById("checklist-form").addEventListener("submit", saveChecklistTemplate);
loadChecklistTemplate();
