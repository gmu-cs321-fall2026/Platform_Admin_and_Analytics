### Building and running your application

Copy `.env.example` to `.env` and set `POSTGRES_PASSWORD` to a local password. Compose starts PostgreSQL 16 and waits for it to become ready before starting the Java backend.

```powershell
docker compose up --build -d --wait --wait-timeout 120
docker compose logs backend
```

Look for `PostgreSQL connection verified.` in the logs. The dashboard is available at http://localhost:9000/admin.

Run `bash backend/src/database/tests/check-postgres.sh` from Git Bash to check startup, password rejection, and data persistence. The test recreates the PostgreSQL container. Run `docker compose down` to stop the stack and keep the data volumes.

### Deploying your application to the cloud

First, build your image, e.g.: `docker build -t myapp .`.
If your cloud uses a different CPU architecture than your development
machine (e.g., you are on a Mac M1 and your cloud provider is amd64),
you'll want to build the image for that platform, e.g.:
`docker build --platform=linux/amd64 -t myapp .`.

Then, push it to your registry, e.g. `docker push myregistry.com/myapp`.

Consult Docker's [getting started](https://docs.docker.com/go/get-started-sharing/)
docs for more detail on building and pushing.