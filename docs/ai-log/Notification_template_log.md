# AI Prompt Log - Notification Template
**Date:** 11/19/2026

**Developer:** Sara El-Ghazali 

**AI Tool:** ChatGPT

## Asked
I asked ChatGPT to help implement selecting and editing notification templates, previewing messages before saving, rejecting blank messages, and saving updated wording in PostgreSQL.

## Produced
It produced a notification template configuration API, PostgreSQL migration, editing page with message preview and tests, which allow the Notifications and Communications subsystem to retrieve updated templates after restarting and preserve edits when saving fails.

## Changed or rejected
- I changed the preview and save process so editing the message clears the previous preview and requires previewing the updated wording before saving.

- I rejected adding notification sending because this story only covers editing saved templates.

- I rejected adding sample templates because the actual names and wording should come from the configuration data.