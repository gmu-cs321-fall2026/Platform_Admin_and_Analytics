Platform Admin & Analytics Architecture

Architecture Sketch

              Platform Admin
                    |
                    v
        +-----------------------+
        |    Admin Dashboard    |
        |       Frontend        |
        +-----------+-----------+
                    |
                    | API Requests
                    v
        +-----------------------+
        |   Admin & Analytics   |
        |       Backend         |
        +-----------+-----------+
                    |
                    | Gets Data
                    v
        +-----------------------+
        |   Other Subsystems    |
        |                       |
        | - Shared Core         |
        | - Applications        |
        | - Events              |
        | - Mentor Matching     |
        | - Community Forum     |
        | - Notifications       |
        +-----------------------+

Justification of Architecture: The Platform Admin & Analytics subsystem uses a layered client server architecture. The Admin Dashboard is the frontend that allows administrators to access management tools and view analytics. The dashboard communicates with the backend through API requests. The backend handles the admin functions and gets the data it needs from other subsystems, including Shared Core, Applications, Events, Mentor Matching, Community Forum, and Notifications. We chose this architecture because it keeps the frontend, backend, and data sources separate, which makes the system easier to work on, maintain, and add new features to in future sprints.
