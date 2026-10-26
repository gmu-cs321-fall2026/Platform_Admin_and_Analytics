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
Open Docker Desktop and run the following command in directory terminal: 

```
docker compose up --build
```

View the subsytem on http://localhost:9000/admin

### Sprint 1 Goal 
The goal of Sprint 1 is to build a working skeleton with the core system architecture, Docker container, and create a working flow between the frontend and backend.

### Sprint 2 Goal 
Implement user account management functionality, allowing for authorized admins to search users, view account info and status, suspend/unsuspend accounts, and change user roles.

### Sprint 3 Goal 
Expand the subsystem with user activity history, content moderation, and an admin audit log. Admins will be able to view and filter user activity and track changes made to accounts and content.