async function getUsers() {

    try {

        const response = await fetch(
            "http://localhost:9000/admin/users"
        );

        if (!response.ok) {
            throw new Error("Failed to get users");
        }

        const users = await response.json();

        displayUsers(users);

    } catch (error) {

        console.error(error);

        document.getElementById("users").textContent =
            "Could not connect to backend.";
    }
}


function displayUsers(users) {

    const usersContainer =
        document.getElementById("users");

    usersContainer.innerHTML = "";

    users.forEach(user => {

        const userElement =
            document.createElement("div");

        userElement.innerHTML = `
            <p>
                <strong>${user.name}</strong><br>
                Email: ${user.email}<br>
                Role: ${user.role}<br>
                Status: ${user.status}
            </p>
            <hr>
        `;

        usersContainer.appendChild(userElement);
    });
}


getUsers();