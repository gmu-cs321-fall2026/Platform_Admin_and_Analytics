# AI Prompt Log - Change User roles

**Commit:** added feature to change user roles

**Date:** 10/12/2026

**Developer:** Mohammed El-Ghazali 

**AI Tool:** ChatGPT

## Asked
I asked ChatGPT to help implement a feature that lets Admin select and save a new role for a user (Student, Staff, Admin).

## Produced
ChatGPT produced updates to index.html, app.js, Main.java, User.java, and UserApi.java. It added a role dropdown with Student, Staff, and Admin options. A save role button with feedback that the role saves successfully. 

## Changed or rejected
- I changed the implmentation to keep the changing user roles feature inside the user account information, (the view button under action). 

- I rejected expanding the feature to include a database, login system, since it wasn't a part of Sprint 2's scope.

- I rerouted the Users to go to the Users tab and under admin/users, while the dashboard only has the user metrics. 