// User data
let allUsers = [];
let selectedUserId = null;
let statusSaving = false;
const validStatuses = ["ACTIVE", "SUSPENDED"];
const validRoles = ["STUDENT", "STAFF", "ADMIN"];


// Update statistics
function updateStatistics(users) {

    const totalUsers =
        users.length;

    const activeUsers =
        users.filter(
            user => user.status === "ACTIVE"
        ).length;

    const adminUsers =
        users.filter(
            user => user.role === "ADMIN"
        ).length;

    const suspendedUsers =
        users.filter(
            user => user.status === "SUSPENDED"
        ).length;


    document.getElementById(
        "total-users"
    ).textContent = totalUsers;


    document.getElementById(
        "active-users"
    ).textContent = activeUsers;


    document.getElementById(
        "admin-users"
    ).textContent = adminUsers;


    document.getElementById(
        "suspended-users"
    ).textContent = suspendedUsers;
}


// Display users
function displayUsers(users) {

    const usersContainer =
        document.getElementById("users");


    usersContainer.innerHTML = "";


    if (users.length === 0) {

        usersContainer.innerHTML = `
            <tr>
                <td
                    colspan="6"
                    class="loading"
                >
                    No users found.
                </td>
            </tr>
        `;

        return;
    }


    users.forEach(user => {

        const row =
            document.createElement("tr");


        const statusClass =
            user.status === "ACTIVE"
                ? "status-active"
                : "status-suspended";


        row.innerHTML = `

            <td>
                ${user.id}
            </td>

            <td>
                <strong>
                    ${user.name}
                </strong>
            </td>

            <td>
                ${user.email}
            </td>

            <td>

                <span class="role-badge">
                    ${user.role}
                </span>

            </td>

            <td>

                <span
                    class="status-badge ${statusClass}"
                >
                    ${user.status}
                </span>

            </td>

            <td>

                <button
                    class="view-button"
                    onclick="viewUser(${user.id})"
                >
                    View
                </button>

            </td>
        `;


        usersContainer.appendChild(row);

    });
}


// Search users
function searchUsers() {

    const searchInput =
        document.getElementById("search");


    const searchText =
        searchInput.value
            .toLowerCase()
            .trim();


    const filteredUsers =
        allUsers.filter(user => /^\d+$/.test(searchText) ? String(user.id) === searchText :

            user.name
                .toLowerCase()
                .includes(searchText)

            ||

            user.email
                .toLowerCase()
                .includes(searchText)

            ||

            user.role
                .toLowerCase()
                .includes(searchText)

            ||

            user.status
                .toLowerCase()
                .includes(searchText)

        );


    displayUsers(filteredUsers);
}


// View user account information
function viewUser(id) {
    const user = allUsers.find(user => user.id === id);

    if (!user) {
        return;
    }

    document.getElementById("detail-id").textContent = user.id;
    document.getElementById("detail-name").textContent = user.name;
    document.getElementById("detail-email").textContent = user.email;
    const roleElement = document.getElementById("detail-role");
    
    roleElement.textContent = user.role;
    roleElement.className = "role-badge";
    
    renderUserStatus(user);

    selectedUserId = id;
    document.getElementById("role-select").value = user.role;
    document.getElementById("role-message").textContent = "";
    document.getElementById("status-message").textContent = "";

    // Hide the user list and show account details
    document.getElementById("user-list").hidden = true;
    document.getElementById("user-details").hidden = false;
}

// Return to the user search results
function backToUsers() {
    document.getElementById("user-details").hidden = true;
    document.getElementById("user-list").hidden = false;
}

// Search event
document
    .getElementById("search")
    .addEventListener(
        "input",
        searchUsers
    );


// Load the backend data, including previously saved roles.
async function loadUsers() {
    try {
        const response = await fetch("/api/users");
        if (!response.ok) throw new Error("Unable to load users. Please refresh to try again.");
        allUsers = await response.json();
        updateStatistics(allUsers);
        searchUsers();
    } catch (error) {
        const loadError = document.getElementById("load-error");
        loadError.textContent = "Unable to load users. Please refresh to try again.";
        loadError.hidden = false;
        ["total-users", "active-users", "admin-users", "suspended-users"].forEach(id => {
            document.getElementById(id).textContent = "-";
        });
        document.getElementById("users").innerHTML = '<tr><td colspan="6" class="loading">Unable to load users. Please refresh to try again.</td></tr>';
    }
}

async function saveUserRole(event) {
    event.preventDefault();
    const user = allUsers.find(user => user.id === selectedUserId);
    const role = document.getElementById("role-select").value;
    const message = document.getElementById("role-message");
    if (!user || !validRoles.includes(role)) {
        message.textContent = "Choose a valid role: STUDENT, STAFF, or ADMIN.";
        return;
    }
    const button = document.getElementById("save-role");
    button.disabled = true;
    message.textContent = "Saving...";
    try {
        const response = await fetch(`/api/users/${user.id}/role`, {
            method: "PUT",
            headers: { "Content-Type": "text/plain" },
            body: role
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.error || "Could not save role.");
        user.role = result[0].role;
        document.getElementById("detail-role").textContent = user.role;
        updateStatistics(allUsers);
        searchUsers();
        message.textContent = "Role saved successfully.";
    } catch (error) {
        message.textContent = error.message || "Could not save role. Please try again.";
    } finally {
        button.disabled = false;
    }
}

// Keep the status badge and the available action consistent.
function renderUserStatus(user) {
    const badge = document.getElementById("detail-status");
    const button = document.getElementById("toggle-status");
    badge.textContent = user.status;
    const active = user.status === "ACTIVE";
    const valid = validStatuses.includes(user.status);
    badge.className = "status-badge" + (valid ? (active ? " status-active" : " status-suspended") : "");
    button.textContent = valid ? (active ? "Suspend Account" : "Unsuspend Account") : "Status unavailable";
    button.className = "view-button status-action" + (valid ? (active ? " suspend-action" : " unsuspend-action") : "");
    button.disabled = statusSaving || !valid;
}

async function toggleUserStatus() {
    if (statusSaving) return;
    const user = allUsers.find(user => user.id === selectedUserId);
    const message = document.getElementById("status-message");
    if (!user || !validStatuses.includes(user.status)) {
        message.textContent = "Cannot update an account with an invalid status.";
        return;
    }
    const nextStatus = user.status === "ACTIVE" ? "SUSPENDED" : "ACTIVE";
    statusSaving = true;
    renderUserStatus(user);
    message.textContent = "Saving account status...";
    try {
        const response = await fetch(`/api/users/${user.id}/status`, {
            method: "PUT",
            headers: { "Content-Type": "text/plain" },
            body: nextStatus
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.error || "Could not update account status.");
        if (!Array.isArray(result) || result[0]?.id !== user.id || !validStatuses.includes(result[0]?.status)) {
            throw new Error("The server returned an invalid account status. Refresh to check the account.");
        }
        user.status = result[0].status;
        updateStatistics(allUsers);
        searchUsers();
        if (selectedUserId === user.id) {
            message.textContent = user.status === "SUSPENDED"
                ? "Account suspended successfully."
                : "Account unsuspended successfully.";
        }
    } catch (error) {
        if (selectedUserId === user.id) message.textContent = error.message || "Could not update account status. Please try again.";
    } finally {
        statusSaving = false;
        const selectedUser = allUsers.find(current => current.id === selectedUserId);
        if (selectedUser) renderUserStatus(selectedUser);
    }
}

// Both URLs use this page, but each displays its own section.
function showCurrentPage() {
    const path = window.location.pathname.replace(/\/+$/, "");
    const isUsersPage = path === "/admin/users";

    document.getElementById("dashboard-view").hidden = isUsersPage;
    document.getElementById("users-view").hidden = !isUsersPage;
    document.getElementById("page-title").textContent = isUsersPage ? "Users" : "Dashboard";
    document.getElementById("page-description").textContent = isUsersPage
        ? "Search user accounts and manage roles and account access"
        : "Welcome back, Administrator";
    document.title = isUsersPage ? "Users | Platform Admin" : "Dashboard | Platform Admin";

    const dashboardLink = document.getElementById("dashboard-link");
    const usersLink = document.getElementById("users-link");
    dashboardLink.className = "nav-item" + (isUsersPage ? "" : " active");
    usersLink.className = "nav-item" + (isUsersPage ? " active" : "");
    const activeLink = isUsersPage ? usersLink : dashboardLink;
    const inactiveLink = isUsersPage ? dashboardLink : usersLink;
    activeLink.setAttribute("aria-current", "page");
    inactiveLink.removeAttribute("aria-current");
}

showCurrentPage();
document.getElementById("role-form").addEventListener("submit", saveUserRole);
document.getElementById("toggle-status").addEventListener("click", toggleUserStatus);
loadUsers();
