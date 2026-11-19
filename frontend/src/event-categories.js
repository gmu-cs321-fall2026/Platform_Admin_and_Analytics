function categoryEditor(category) {
    const form = document.createElement("form");
    form.className = "users-section category-editor";
    const heading = document.createElement("h2");
    heading.textContent = category.name;
    const nameLabel = document.createElement("label");
    nameLabel.htmlFor = `category-name-${category.id}`;
    nameLabel.textContent = "Name";
    const name = document.createElement("input");
    name.id = nameLabel.htmlFor;
    name.value = category.name;
    name.required = true;
    name.maxLength = 200;
    const descriptionLabel = document.createElement("label");
    descriptionLabel.htmlFor = `category-description-${category.id}`;
    descriptionLabel.textContent = "Description";
    const description = document.createElement("textarea");
    description.id = descriptionLabel.htmlFor;
    description.value = category.description;
    description.rows = 4;
    description.maxLength = 5000;
    const button = document.createElement("button");
    button.type = "submit";
    button.className = "view-button";
    button.textContent = "Save Category";
    const message = document.createElement("p");
    message.setAttribute("role", "status");
    message.setAttribute("aria-live", "polite");
    form.append(heading, nameLabel, name, descriptionLabel, description, button, message);

    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (button.disabled) return;
        if (!name.value.trim()) {
            message.textContent = "Enter a category name.";
            return;
        }
        button.disabled = name.disabled = description.disabled = true;
        message.textContent = "Saving...";
        try {
            const response = await fetch(`/api/config/event-categories/${category.id}`, {
                method: "PUT",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: new URLSearchParams({ name: name.value, description: description.value })
            });
            const result = await response.json();
            if (!response.ok) throw new Error(result.error || "Could not save category. Please try again.");
            if (result.id !== category.id || typeof result.name !== "string"
                || typeof result.description !== "string") {
                throw new Error("Could not confirm the saved category. Please try again.");
            }
            name.value = result.name;
            description.value = result.description;
            heading.textContent = result.name;
            message.textContent = "Category saved successfully.";
        } catch (error) {
            // Keep the entered values so the admin can correct or retry them.
            message.textContent = error.message || "Could not save category. Please try again.";
        } finally {
            button.disabled = name.disabled = description.disabled = false;
        }
    });
    return form;
}

async function loadCategories() {
    const message = document.getElementById("categories-message");
    try {
        const response = await fetch("/api/config/event-categories");
        if (!response.ok) throw new Error("Could not load event categories. Please refresh to try again.");
        const result = await response.json();
        const editors = document.getElementById("category-editors");
        result.categories.forEach(category => editors.appendChild(categoryEditor(category)));
        message.textContent = result.categories.length ? "" : "No event categories exist.";
    } catch (error) {
        message.textContent = error.message || "Could not load event categories. Please refresh to try again.";
    }
}

loadCategories();
