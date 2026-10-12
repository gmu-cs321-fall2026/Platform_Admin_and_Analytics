# AI Prompt Log - Search User Accounts

**Commit:** added search by user ID

**Date:** 10/12/2026

**Developer:** Mohammed El-Ghazali 

**AI Tool:** ChatGPT

## Asked
I gave ChatGPT my existing Platform Admin code and asked it to help complete the user account search feature. 

## Produced
ChatGPT produced the updated index.html and app.js files. In app.js it added ID matching for searches, so the admin can search by user ID. In index.html, it updated the search placeholder and label to mention name, email, and ID. 

## Changed or rejected
- I changed the search requirements to use exact ID matching, so when you search for an ID its the exact match rather than when you search for 1, it would also include 10 - 15 since they start with 1. 

- I rejected adding a separate search page because the existing user search bar was already working. 

- I reprompted to make user email different than the their names to test if it would still search for emails that are completely different than names. 