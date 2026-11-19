const notificationTemplateApi = "/api/config/notification-templates";
let notificationTemplates = [];
const notificationDrafts = new Map();
let notificationTemplateSaving = false;

function selectedNotificationTemplate() {
    return notificationTemplates.find(template => template.id === document.getElementById("notification-template-select").value);
}

function clearNotificationPreview() {
    document.getElementById("notification-template-preview").hidden = true;
    document.getElementById("notification-template-save").disabled = true;
}

function showNotificationTemplate() {
    if (notificationTemplateSaving) return;
    const template = selectedNotificationTemplate();
    if (!template) return;
    document.getElementById("notification-template-message").value = notificationDrafts.get(template.id) ?? template.message;
    document.getElementById("notification-template-status").textContent = "";
    clearNotificationPreview();
}

function previewNotificationTemplate() {
    if (notificationTemplateSaving) return;
    const message = document.getElementById("notification-template-message").value;
    const status = document.getElementById("notification-template-status");
    if (!message.trim()) {
        clearNotificationPreview();
        status.textContent = "Template messages cannot be blank.";
        return;
    }
    // Plain text preview preserves wording and line breaks without executing markup.
    document.getElementById("notification-preview-message").textContent = message;
    document.getElementById("notification-template-preview").hidden = false;
    document.getElementById("notification-template-save").disabled = false;
    status.textContent = "Review the preview before saving.";
}

async function saveNotificationTemplate(event) {
    event.preventDefault();
    if (notificationTemplateSaving) return;
    const template = selectedNotificationTemplate();
    const input = document.getElementById("notification-template-message");
    const status = document.getElementById("notification-template-status");
    if (!template || !input.value.trim()) {
        status.textContent = "Select a template and enter a nonblank message.";
        return;
    }
    if (document.getElementById("notification-template-preview").hidden
        || document.getElementById("notification-preview-message").textContent !== input.value) {
        clearNotificationPreview();
        status.textContent = "Preview the current message before saving.";
        return;
    }
    notificationTemplateSaving = true;
    document.getElementById("notification-template-select").disabled = true;
    document.getElementById("notification-template-preview-button").disabled = true;
    document.getElementById("notification-template-save").disabled = true;
    input.disabled = true;
    status.textContent = "Saving...";
    try {
        const response = await fetch(`${notificationTemplateApi}/${template.id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: new URLSearchParams({ message: input.value })
        });
        const saved = await response.json();
        if (!response.ok) throw new Error(saved.error || "Could not save the notification template. Please try again.");
        if (saved.id !== template.id || typeof saved.message !== "string" || saved.message !== input.value) {
            throw new Error("Could not confirm the saved template. Please try again.");
        }
        template.message = saved.message;
        notificationDrafts.delete(template.id);
        status.textContent = "Notification template saved.";
    } catch (error) {
        notificationDrafts.set(template.id, input.value);
        status.textContent = error.message || "Could not save the notification template. Please try again.";
    } finally {
        notificationTemplateSaving = false;
        input.disabled = false;
        document.getElementById("notification-template-select").disabled = false;
        document.getElementById("notification-template-preview-button").disabled = false;
        document.getElementById("notification-template-save").disabled = false;
    }
}

async function loadNotificationTemplates() {
    const status = document.getElementById("notification-load-message");
    try {
        const response = await fetch(notificationTemplateApi);
        const result = await response.json();
        if (!response.ok || !Array.isArray(result.templates) || result.templates.some(template =>
            typeof template.id !== "string" || typeof template.name !== "string" || typeof template.message !== "string")) {
            throw new Error("Could not load notification templates. Please refresh to try again.");
        }
        notificationTemplates = result.templates;
        const select = document.getElementById("notification-template-select");
        select.replaceChildren();
        notificationTemplates.forEach(template => {
            const option = document.createElement("option");
            option.value = template.id;
            option.textContent = template.name;
            select.appendChild(option);
        });
        if (!notificationTemplates.length) {
            status.textContent = "No notification templates exist.";
            return;
        }
        select.value = notificationTemplates[0].id;
        showNotificationTemplate();
        document.getElementById("notification-template-form").hidden = false;
        status.textContent = "";
    } catch (error) {
        status.textContent = error.message || "Could not load notification templates. Please refresh to try again.";
    }
}

document.getElementById("notification-template-select").addEventListener("change", showNotificationTemplate);
document.getElementById("notification-template-message").addEventListener("input", () => {
    const template = selectedNotificationTemplate();
    if (template) notificationDrafts.set(template.id, document.getElementById("notification-template-message").value);
    clearNotificationPreview();
    document.getElementById("notification-template-status").textContent = "Unsaved changes.";
});
document.getElementById("notification-template-preview-button").addEventListener("click", previewNotificationTemplate);
document.getElementById("notification-template-form").addEventListener("submit", saveNotificationTemplate);
loadNotificationTemplates();
