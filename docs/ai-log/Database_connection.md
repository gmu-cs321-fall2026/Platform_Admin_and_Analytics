# AI Prompt Log - Database Connection 

**Date:** 11/9/2026

**Developer:** Sara El-Ghazali 

**AI Tool:** ChatGpt

## Asked
Asked ChatGpt to help implement the PostgreSQL connection to the code and make sure Docker can run it. 

## Produced
It produced files and test for PostgreSQL Compose service, JDBC driver, Java startup probe and CI integration test. These all allow docker to establish a connection with PostgreSQL and tests for CI. 

## Changed or Rejected 
- I changed the folder layout to put Database.java and the tests together under backend/src/database 

- I rejected comitting the local .env file because it contains the database password. I used a .env.example so other users can reference that

- I rejected adding schemas and querying the database with values, since its out of scope for the story. 