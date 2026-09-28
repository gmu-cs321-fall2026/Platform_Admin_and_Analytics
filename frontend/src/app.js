// User data
let allUsers = [

    {
        id: 1,
        name: "John Doe",
        email: "john@gmu.edu",
        role: "STUDENT",
        status: "ACTIVE"
    },

    {
        id: 2,
        name: "Jane Smith",
        email: "jane@gmu.edu",
        role: "ADMIN",
        status: "ACTIVE"
    },

    {
        id: 3,
        name: "Alex Johnson",
        email: "alex@gmu.edu",
        role: "STAFF",
        status: "SUSPENDED"
    }

];


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
        allUsers.filter(user =>

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


// View user
function viewUser(id) {

    const user =
        allUsers.find(
            user => user.id === id
        );


    if (!user) {
        return;
    }


    alert(
        "User Information\n\n" +

        "Name: " +
        user.name +

        "\nEmail: " +
        user.email +

        "\nRole: " +
        user.role +

        "\nStatus: " +
        user.status
    );
}


// Search event
document
    .getElementById("search")
    .addEventListener(
        "input",
        searchUsers
    );


// Initial dashboard load
updateStatistics(allUsers);

displayUsers(allUsers);