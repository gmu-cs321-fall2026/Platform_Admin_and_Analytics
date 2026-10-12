// User data
let allUsers = [
  {
    "id": 1,
    "name": "John Doe",
    "email": "orbit742@example.com",
    "role": "STUDENT",
    "status": "ACTIVE"
  },
  {
    "id": 2,
    "name": "Jane Smith",
    "email": "cedar318@example.com",
    "role": "ADMIN",
    "status": "ACTIVE"
  },
  {
    "id": 3,
    "name": "Alex Johnson",
    "email": "comet905@example.com",
    "role": "STAFF",
    "status": "SUSPENDED"
  },
  {
    "id": 4,
    "name": "Maya Patel",
    "email": "harbor261@example.com",
    "role": "STUDENT",
    "status": "ACTIVE"
  },
  {
    "id": 5,
    "name": "Noah Williams",
    "email": "maple804@example.com",
    "role": "STAFF",
    "status": "ACTIVE"
  },
  {
    "id": 6,
    "name": "Sofia Garcia",
    "email": "quartz537@example.com",
    "role": "STUDENT",
    "status": "ACTIVE"
  },
  {
    "id": 7,
    "name": "Ethan Brown",
    "email": "river196@example.com",
    "role": "ADMIN",
    "status": "SUSPENDED"
  },
  {
    "id": 8,
    "name": "Ava Nguyen",
    "email": "nova623@example.com",
    "role": "STUDENT",
    "status": "ACTIVE"
  },
  {
    "id": 9,
    "name": "Liam Davis",
    "email": "pine458@example.com",
    "role": "STAFF",
    "status": "ACTIVE"
  },
  {
    "id": 10,
    "name": "Isabella Wilson",
    "email": "lunar079@example.com",
    "role": "STUDENT",
    "status": "ACTIVE"
  },
  {
    "id": 11,
    "name": "Oliver Chen",
    "email": "cloud382@example.com",
    "role": "STUDENT",
    "status": "SUSPENDED"
  },
  {
    "id": 12,
    "name": "Amelia Robinson",
    "email": "coral651@example.com",
    "role": "ADMIN",
    "status": "ACTIVE"
  },
  {
    "id": 13,
    "name": "Lucas Martinez",
    "email": "pixel924@example.com",
    "role": "STAFF",
    "status": "ACTIVE"
  },
  {
    "id": 14,
    "name": "Charlotte Lee",
    "email": "meadow173@example.com",
    "role": "STUDENT",
    "status": "SUSPENDED"
  },
  {
    "id": 15,
    "name": "Elijah Thompson",
    "email": "cobalt860@example.com",
    "role": "STUDENT",
    "status": "ACTIVE"
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
    const statusElement = document.getElementById("detail-status");
    
    roleElement.textContent = user.role;
    roleElement.className = "role-badge";
    
    statusElement.textContent = user.status;
    statusElement.className =
        "status-badge " +
        (user.status === "ACTIVE"
            ? "status-active"
            : "status-suspended");

    // Hide the user list and show account details
    document.querySelector(".users-section").hidden = true;
    document.getElementById("user-details").hidden = false;
}

// Return to the user search results
function backToUsers() {
    document.getElementById("user-details").hidden = true;
    document.querySelector(".users-section").hidden = false;
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
