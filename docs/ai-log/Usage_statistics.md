# AI Prompt Log - View Usage Statistics

**Date:** 11/19/2026

**Developer:** Mohammed El-Ghazali

**AI Tool:** ChatGPT

## Asked
I asked ChatGPT to help implement usage stats on the dashboard that links to shared core APIs. 

## Produced
ChatGPT added an analytics endpoint and 4 usage cards on the dashboard, also tests for database integration and added Gson for parsing JSON. 

## Changed or rejected
- I changed the test directory from the backend to backend/src/tests to make sure code structure is consistent

- I changed the dashboard cards to show unavailable because ChatGPT had fake values

- I rejected adding weekly charts and date filters because they were out of Sprint 5 scope 

- I rejected using hardcoded user counts because it should be reflected after subsystem integration