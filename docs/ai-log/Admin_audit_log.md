# AI Prompt Log - Admin Audit Log

**Date:** 10/26/2026

**Developer:** Mohammed El-Ghazali 

**AI Tool:** ChatGPT

## Asked
I asked ChatGPT to help implement an admin audit log. I wanted the log to include role changes, accounts suspensions, and moderation actions. 

## Produced
ChatGPT provided AuditLog.java, AuditApi.java, audit-log.html, and audit-log.js. It also changed some of the backend logic to properly route everything.

## Changed or rejected
- I changed the date display and fitering from UTC to Eastern Time.

- I changed the table to display the affected item's ID in a seperate column rather than in the same beside name. 

- I changed the column heading to Affected Item, so it can represent both user accounts and moderated content. 

- I rejected allowing admins to edit and delete audit logs to preserve the history of the log. 