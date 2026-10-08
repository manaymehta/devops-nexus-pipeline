# DevOps CI/CD Pipeline with Artifact Management (Nexus)

A complete software delivery pipeline for a small Spring Boot REST API:
**Git/GitHub → build → automated tests → artifact stored in Nexus → Docker image → deployment → rollback**.

## Repository layout

```
.github/workflows/ci.yml   CI pipeline (runs on GitHub's cloud runners)
app/                       Spring Boot application (Java 21, Maven)
  src/main/...             Task API + /api/version endpoint
  src/test/...             Unit tests and API tests (JUnit, MockMvc)
  Dockerfile               Runtime image built from an already-built jar
```

## The application

| Endpoint | Purpose |
|---|---|
| `GET /api/tasks` | List tasks |
| `POST /api/tasks` `{"title": "..."}` | Create a task (blank titles are rejected with 400) |
| `GET /api/tasks/{id}` | Get one task |
| `PATCH /api/tasks/{id}/complete` | Mark a task done |
| `DELETE /api/tasks/{id}` | Delete a task |
| `GET /api/version` | Name, version and build time of the running build. Used to prove which version is deployed. |
| `GET /actuator/health` | Health check used by the smoke test and Docker `HEALTHCHECK` |

Tasks are kept in memory, so they reset on restart. That's intentional: the project is about the delivery pipeline, not the app.

## Run locally

Requires JDK 21. Maven is not needed; the included wrapper (`mvnw`) downloads it.

```bash
cd app
./mvnw verify                               # compile + run tests + build the jar
java -jar target/devops-app-1.0.0-SNAPSHOT.jar
curl localhost:8080/api/version
```

With Docker:

```bash
cd app
./mvnw verify
docker build -t devops-app:local .
docker run -p 8080:8080 devops-app:local
```

## CI pipeline (`.github/workflows/ci.yml`)

Runs on every push and pull request, on GitHub-hosted runners:

1. **Build & test:** `./mvnw verify` compiles, runs all tests and packages the jar. A failing test stops the pipeline. Test reports and the jar are saved as workflow artifacts.
2. **Docker image & smoke test:** downloads *the same jar that passed the tests*, builds the image, starts a container, and checks `/actuator/health`, `/api/version` and a real API call.

## Nexus integration (planned)

Publishing to Nexus and deployment run on a self-hosted runner on the machine that hosts Nexus. These names are fixed so the workflows and the Nexus setup match:

| Item | Value |
|---|---|
| Nexus URL | `http://localhost:8081` |
| Docker registry (Nexus) | `localhost:8082` |
| Maven repositories | `maven-releases`, `maven-snapshots` |
| Docker repository | `docker-hosted` |
| Image name | `devops-app` |
| GitHub Secrets | `NEXUS_USER`, `NEXUS_PASSWORD` |
