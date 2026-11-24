# AI Prompt Log - Event Categories

**Date:** 11/19/2026

**Developer:** Sara El-Ghazali 

**AI Tool:** ChatGpt

## Asked
Asked ChatGpt to help implement editing existing event category names and descriptions, reject blank and duplicate names, and save the changes in PostgreSQL.

## Produced
It produced a category configuration API, PostgreSQL migration, category editing page and tests, which allow admins to update categories while keeping the same ID, retrieve saved changes after restarting, and keep their edits if saving fails. 

## Changed or Rejected 
- I changed duplicate name validation from only checking in JavaScript to enforcing it in PostgreSQL. This prevents duplicate names even when requests bypass the page or two updates happen at the same time.

- I rejected keeping the handoff document in the repository because it was outside the acceptance criteria.

- I rejected adding category creation and deletion because the story only requires editing existing categories.