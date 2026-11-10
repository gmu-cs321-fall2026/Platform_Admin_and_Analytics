# Platform Admin and Analytics
## Subsystem 7 for MasonCareerLaunch

### Overview 
Subsystem 7 provides platform admins and career service staff with tools to: 
- Manage User Accounts
- Review Account Activity 
- Moderate Flagged Content
- View Platform Analytics

The Subsystem helps admins monitor platform usage, manage users, and maintain a functioning platform. 

### How to build and run
Start Docker Desktop and open a terminal in the project folder.

#### First time database setup
If you already have a working `.env` with your own database password, skip to **Start the app**. If you don’t have a `.env` yet, copy the template:

```
Copy-Item .env.example .env
```

Start the PostgreSQL container and open its command prompt: 

```
docker compose up -d --wait db
docker compose exec db psql -U platform_admin -d platform_admin
```

At the `platform_admin=#` prompt, type `\password` and press Enter. Enter your new password twice, then type `\q` to return to terminal.

In `.env`, set `POSTGRES_PASSWORD` to the same new password and save the file. Keep `.env` out of Git.

#### Start the app

```
docker compose up
```

Open the [Admin Dashboard](http://localhost:9000/admin). To verify the database connection, run `docker compose logs backend`. 

### Sprint 1 Goal 
The goal of Sprint 1 is to build a working skeleton with the core system architecture, Docker container, and create a working flow between the frontend and backend.

### Sprint 2 Goal 
Implement user account management functionality, allowing for authorized admins to search users, view account info and status, suspend/unsuspend accounts, and change user roles.

### Sprint 3 Goal 
Expand the subsystem with user activity history, content moderation, and an admin audit log. Admins will be able to view and filter user activity and track changes made to accounts and content.

### Sprint 4 Goal 
The goal of Sprint 4 is to complete the remaining moderation actions, add targeted announcements, and connect the subsystem to PostgreSQL. Staff will be able to create, preview, and submit announcements to selected students. Admin records will be stored in the database, and GitHub Actions will run automated checks to block failing pull requests from being merged into main.