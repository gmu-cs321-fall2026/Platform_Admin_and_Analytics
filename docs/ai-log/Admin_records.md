# AI Prompt Log - Save and Retrieve Admin Records

**Date:** 11/9/2026

**Developer:** Mohammed El-Ghazali

**AI Tool:** ChatGPT

## Asked
Asked ChatGPT to help implement data persistance and retrieval for admin records and to make tests to make sure it works with CI. 

## Produced
ChatGPT added PostgreSQL storage for audit entries, moderation decisions, announcement , and tests for the CI. 

## Changed or rejected
- I changed the location of AdminRecordsTest.java from backend directory to the backend/src/tests to maintain code structure 

- I changed the announcment save and retrieval logic from Main.java and into AnnouncementApi.java so Main only has the route

- I rejected storing a copy of each users name and email and only kept the ID so it doesn't duplicate account info