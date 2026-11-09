# AI Prompt Log - Admin Audit Log

**Date:** 11/9/2026

**Developer:** Mohammed El-Ghazali 

**AI Tool:** ChatGPT

## Asked
I asked ChatGPT to help plan and create GitHub Actions instructions for the subsytem. 

## Produced
ChatGPT reviewed the Java/Docker build and existing UserTest. It drafted a workflow that runs ESLint, builds the Docker image, runs Java tests in an isolated container, and runs frontend search tests. It also helped me understand how the lint and test works before merging. 

## Changed or rejected
- I rejected using Maven or Gradle since our project already compiles and tests Java through docker 
- I changed Java setup with a Node.js setup in workflows/ci.yml to run frontend tests 
- I changed frontend test folder from the root to the frontend directory under so it stays with frontend code
- I changed npm test command in package.json to use the new test location and corrected paths to app