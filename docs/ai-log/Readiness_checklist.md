# AI Prompt Log - Readiness Checklist
**Date:** 11/19/2026

**Developer:** Sara El-Ghazali 

**AI Tool:** ChatGPT

## Asked
I asked ChatGPT to help implement editing the default readiness checklist template, including adding, editing, and removing items, rejecting blank items, and saving the item order after restarting.

## Produced
It produced a checklist configuration API, PostgreSQL migration, checklist editing page and tests. These allow admins to update checklist items, let the readiness tracker retrieve the saved template, and preserve edits when saving fails.

## Changed or rejected
- I changed the save process to update all checklist items in one database transaction so a failed save would not leave a partially updated template or incorrect item order.

- I rejected adding student checklist tracking because this story only covers editing the default template.

- I rejected adding example checklist items because admins should provide the actual guidance.