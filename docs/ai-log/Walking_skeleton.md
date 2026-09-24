# AI Prompt Log - Walking Skeleton and Structure

**Commit:** implemented walking skeleton

**Data:** 9/24/2026

**Developer:** Mohammed El-Ghazali 

**AI Tool:** ChatGPT

## Asked
I asked ChatGPT to help create a simple Java backend that stores user info and test file, a JavaScript frontend that will display it, and a dockerfile that will containerize and build it. 

## Produced
ChatGPT produced a complex java app using SpringBoot, Maven, PostgreSQL for the backend and a React/JavaScript frontend. It also gave me the Java files, a test file, React files, HTML, CSS, JavaScript, dockerfile, a yom.xml file, and docker compose file. It hard coded users as temporary data to make sure the connection between frontend and backend works. 

## Changed or rejected
- I changed the Main.java file into different classes to keep the logic for the application, data, and API separate, splitting it up into Main, User, and UserApi classes. 

- I rejected the SpringBoot, Maven, PostgreSQL files because the walking skeleton for Sprint 1 is onyl focused on establishing the frontend and backend connection. 

- I kept the hard coded user data because the purpose was just to make sure all connections are working and are able to pull from the JSON data. 

- I reprompted so it can produce a dockerfile and compose file that is based off of what I kept and changed and for it to remove all the Maven and Database connections. 

