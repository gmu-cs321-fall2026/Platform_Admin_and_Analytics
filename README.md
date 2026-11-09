# Platform Admin and Analytics
## Subsystem 7 for MasonCareerLaunch

### Overview 
Subsystem 7 provides platform admins and career service staff with tools to: 
- Manage User Accounts
- Review Account Activity 
- Moderate Flagged Content
- View Platform Analytics

The Subsystem helps admins monitor platform usage, manage users, and maintain a functioning platform. 

### How to build
Start Docker Desktop. Copy `.env.example` to `.env` and set `POSTGRES_PASSWORD` to a local password. Then run the following command from the repository root.

```
docker compose up --build -d --wait --wait-timeout 120
```

View the subsytem on http://localhost:9000/admin

### Sprint 1 Goal 
The goal of Sprint 1 is to build a working skeleton with the core system architecture, Docker container, and create a working flow between the frontend and backend.

### Sprint 2 Goal 
Implement user account management functionality, allowing for authorized admins to search users, view account info and status, suspend/unsuspend accounts, and change user roles.

### Sprint 3 Goal 
Expand the subsystem with user activity history, content moderation, and an admin audit log. Admins will be able to view and filter user activity and track changes made to accounts and content.

### Sprint 4 Goal 
The goal of Sprint 4 is to complete the remaining moderation actions, add targeted announcements, and connect the subsystem to PostgreSQL. Staff will be able to create, preview, and submit announcements to selected students. Admin records will be stored in the database, and GitHub Actions will run automated checks to block failing pull requests from being merged into main.