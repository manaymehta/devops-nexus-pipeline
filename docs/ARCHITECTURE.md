# Architecture

How the project works, from the concepts it is built on to the exact sequence of steps in each pipeline. Diagrams are in Mermaid, which GitHub renders automatically.

Each section is marked **Implemented** (exists and runs) or **Planned** (designed, not built yet).

---

## 1. Concepts

| Term | Meaning in this project |
|---|---|
| **Pipeline** | An automated sequence of steps that turns source code into a running application, stopping as soon as any step fails. |
| **CI (Continuous Integration)** | Building and testing the code automatically on every push, so problems are found immediately. |
| **CD (Continuous Delivery/Deployment)** | Automatically packaging, storing and deploying versions that passed CI. |
| **Artifact** | The output of a build. Here, the file `devops-app-<version>.jar` and the Docker image `devops-app:<version>`. |
| **Artifact repository** | A server that stores artifacts by name and version, so they can be downloaded and reused. This project uses **Sonatype Nexus Repository**. |
| **SNAPSHOT version** | A work-in-progress version such as `1.0.0-SNAPSHOT`. Nexus allows it to be overwritten by newer builds. |
| **Release version** | A final version such as `1.0.0`. Nexus refuses to overwrite it, so a release is permanent and reproducible. |
| **Maven** | The Java build tool. It reads `pom.xml`, downloads libraries, compiles, runs tests and packages the jar. |
| **Maven wrapper (`mvnw`)** | A script in the repo that downloads the correct Maven version automatically, so Maven does not need to be installed. |
| **Jar** | A single Java archive file containing the compiled app and all its libraries. Run with `java -jar`. |
| **Docker image** | A sealed package containing an operating system layer, Java and the jar. It runs identically on any machine with Docker. |
| **Container** | A running instance of a Docker image. |
| **GitHub Actions** | GitHub's automation service. It runs the steps defined in `.github/workflows/*.yml` files. |
| **Workflow / job / step** | A workflow is one YAML file. It contains jobs, and each job runs on a fresh machine and contains steps (commands). |
| **Runner** | The machine that executes a job. *GitHub-hosted* runners are temporary cloud machines. A *self-hosted* runner is a program installed on one's own machine. |
| **Smoke test** | A quick check after starting the app: does it start and answer basic requests? |
| **Rollback** | Redeploying an older version after a problem is found in a newer one. |

---

## 2. System overview

Three locations are involved: the developer's laptop, GitHub's cloud, and the host that runs Nexus. The host is a GitHub Codespace, a Linux machine in GitHub's cloud (section 11).

```mermaid
flowchart TB
    subgraph laptop["Developer laptop"]
        code["Source code<br/>+ git"]
    end

    subgraph cloud["GitHub cloud"]
        repo["GitHub repository"]
        hosted["GitHub-hosted runners<br/>CI workflow"]
    end

    subgraph host["Host: GitHub Codespace"]
        runner["Self-hosted runner<br/>labels: self-hosted, nexus"]
        nexus[("Nexus Repository<br/>:8081 web/API<br/>:8082 Docker registry")]
        app["Deployed container<br/>devops-app on :8080"]
    end

    code -- "git push" --> repo
    repo -- "every push" --> hosted
    repo -- "release tag / manual deploy" --> runner
    runner -- "upload jar + image" --> nexus
    nexus -- "pull selected version" --> runner
    runner -- "docker run" --> app
```

**Why two kinds of runner:** GitHub-hosted runners are separate temporary machines and cannot reach a Nexus server on another machine (`localhost:8081` there means the runner itself). Steps that need Nexus therefore run on a self-hosted runner installed on the same machine as Nexus. Steps that do not need Nexus (build, test, Docker smoke test) run on GitHub-hosted runners.

---

## 3. The application - Implemented

A Spring Boot REST API. Spring Boot is a Java framework that starts an embedded web server and routes HTTP requests to Java methods.

### How a request is handled

```mermaid
flowchart LR
    client["HTTP client<br/>curl, browser, test"] --> ctrl["TaskController<br/>maps URL to method"]
    ctrl -- "POST body" --> valid{"Validation<br/>title not blank,<br/>max 200 chars"}
    valid -- "invalid" --> e400["HTTP 400"]
    valid -- "valid" --> svc["TaskService<br/>business logic"]
    ctrl -- "GET / PATCH / DELETE" --> svc
    svc --> store[("In-memory map<br/>id to Task")]
    svc -- "id not found" --> e404["TaskNotFoundException<br/>HTTP 404"]
    svc -- "result" --> json["JSON response"]
```

### Source files

| File | Role |
|---|---|
| `DevopsAppApplication.java` | Entry point that starts Spring Boot. |
| `task/Task.java` | The data shape of a task: `id`, `title`, `done`, `createdAt`. |
| `task/CreateTaskRequest.java` | The accepted input for creating a task, with validation rules. |
| `task/TaskController.java` | Maps URLs (`/api/tasks/...`) to service calls and sets HTTP status codes (201 Created, 204 No Content). |
| `task/TaskService.java` | Stores tasks in a thread-safe in-memory map and implements create/find/complete/delete. |
| `task/TaskNotFoundException.java` | Signals a missing task. Spring turns it into HTTP 404. |
| `version/VersionController.java` | `GET /api/version`. Reads `META-INF/build-info.properties`, a file Maven generates at build time containing the name, version and build time. |
| `resources/application.properties` | Configuration. Exposes `/actuator/health` and `/actuator/info`. |

### Why `/api/version` matters

The version is written into the jar by Maven during the build (the `build-info` goal in `pom.xml`). The running app reports it, so after any deployment or rollback a single request shows exactly which artifact is live:

```
GET /api/version  ->  {"name":"devops-app","version":"1.0.0-SNAPSHOT","buildTime":"2026-10-09T04:38:25Z"}
```

---

## 4. Tests - Implemented

13 automated tests, run by `./mvnw verify`. A single failure stops the pipeline.

| Test class | Kind | What it checks |
|---|---|---|
| `TaskServiceTest` (5) | Unit test: plain Java, no web server | IDs increment, titles are trimmed, list order, completing, deleting, missing IDs throw an error |
| `TaskControllerTest` (6) | API test: Spring MockMvc sends simulated HTTP requests to the controller | Status codes (201, 200, 204, 400, 404), JSON fields, `Location` header, blank title rejected |
| `DevopsAppApplicationTests` (2) | Full application test: starts the whole app | `/actuator/health` reports `UP`, `/api/version` reports a real version |

---

## 5. Docker image - Implemented

`app/Dockerfile` does **not** compile code. It only packages a jar that was already built and tested:

```mermaid
flowchart LR
    base["eclipse-temurin:21-jre-alpine<br/>small Linux + Java 21 runtime"] --> user["Create non-root user 'app'"]
    user --> copy["COPY the jar<br/>build arg JAR_FILE"]
    copy --> hc["HEALTHCHECK<br/>every 10s: /actuator/health is UP?"]
    hc --> run["ENTRYPOINT<br/>java -jar app.jar on port 8080"]
```

Separating "build the jar" from "package the jar" is deliberate: the image is guaranteed to contain the exact file that passed the tests, and later the exact file stored in Nexus. `.dockerignore` limits what Docker can see to `target/*.jar`.

---

## 6. CI workflow - Implemented

File: `.github/workflows/ci.yml`. Trigger: every push to any branch, and every pull request to `main`. Runs on GitHub-hosted Ubuntu runners.

```mermaid
sequenceDiagram
    actor Dev as Developer
    participant GH as GitHub
    participant J1 as Job 1 runner
    participant J2 as Job 2 runner

    Dev->>GH: git push
    GH->>J1: start "Build & test"
    J1->>J1: checkout code, install JDK 21
    J1->>J1: ./mvnw verify - compile, 13 tests, package jar
    alt a test fails
        J1-->>GH: job fails, pipeline stops, nothing is packaged
    else all tests pass
        J1->>GH: upload jar and test reports as workflow artifacts
        GH->>J2: start "Docker image & smoke test"
        J2->>GH: download the same jar
        J2->>J2: docker build, docker run on port 8080
        J2->>J2: curl /actuator/health, /api/version, POST /api/tasks
        J2-->>GH: pass or fail, container logs printed, container removed
    end
    GH-->>Dev: green tick or red cross on the Actions tab
```

The jar and test reports remain downloadable from the run's page on GitHub (the "Artifacts" section).

---

## 7. Release workflow - Implemented

File: `.github/workflows/release.yml`. Trigger: pushing a git tag such as `v1.1.0`. The version is the tag without `v`. Runs on the self-hosted runner next to Nexus.

```mermaid
sequenceDiagram
    actor Dev as Developer
    participant GH as GitHub
    participant R as Self-hosted runner
    participant N as Nexus

    Dev->>GH: git tag v1.1.0, git push origin v1.1.0
    GH->>R: start release workflow
    R->>R: set Maven version to 1.1.0
    R->>R: ./mvnw deploy - build, 13 tests, package
    alt a test fails
        R-->>GH: workflow fails, nothing reaches Nexus
    else tests pass
        R->>N: upload devops-app-1.1.0.jar to maven-releases
        Note over N: Release versions are immutable.<br/>Publishing 1.1.0 again gets HTTP 409.
        R->>N: download devops-app-1.1.0.jar
        R->>R: docker build from the downloaded jar
        R->>N: docker push localhost:8082/devops-app:1.1.0
        R->>R: deploy 1.1.0, see section 8
    end
```

The jar is downloaded back from Nexus before the image is built. This demonstrates the core idea of artifact management: **build once, store, and deploy the stored artifact**, rather than rebuilding for each deployment.

The deploy step is the separate workflow `deploy.yml`, called from `release.yml` as a reusable workflow (`workflow_call`), so release and rollback share one deployment procedure.

Verified runs:
- `38419484993` released and deployed `1.0.0`.
- `38419898826` released and deployed `1.1.0`. Its re-run failed with HTTP 409: *"cannot be updated as asset already exists and redeploy is not allowed"*.

Publishing `-SNAPSHOT` builds to `maven-snapshots` on pushes to `main` is designed but not implemented. The setup test shows that it works.

---

## 8. Deployment and rollback - Implemented

File: `.github/workflows/deploy.yml`. One deployment procedure, used both at the end of a release and on demand: Actions → Deploy → Run workflow, with a version number as input.

```mermaid
flowchart LR
    pick["Version selected<br/>e.g. 1.0.0"] --> pull["docker pull<br/>localhost:8082/devops-app:1.0.0<br/>local copy removed first"]
    pull --> stop["Stop and remove the<br/>running devops-app container"]
    stop --> start["docker run -d -p 8080:8080<br/>devops-app:1.0.0"]
    start --> check{"Health UP and<br/>/api/version = 1.0.0?"}
    check -- "yes" --> ok["Deployment succeeded"]
    check -- "no" --> fail["Workflow fails,<br/>container logs printed"]
```

**Rollback** is the same procedure with an older version number. Nothing is rebuilt: the old image is fetched from Nexus, so it is byte-for-byte the version that was originally tested and released.

Verified: run `38420296234` deployed `1.0.0` while `1.1.0` was running. Afterwards `/api/version` reported `1.0.0` with its original build time, `15:09:21`, which shows the image was not rebuilt.

A `concurrency` group lets only one deployment run at a time, so two deployments never compete for port 8080.

### Version lifecycle in Nexus

```mermaid
flowchart LR
    v100["1.0.0<br/>released"] --> v110["1.1.0<br/>released, deployed"]
    v110 -- "bug found" --> rb["manual deploy of 1.0.0"]
    rb --> live["1.0.0 running again<br/>1.1.0 still stored in Nexus"]
```

---

## 9. Nexus setup and its automated test - Implemented

`infra/nexus/docker-compose.yml` runs Nexus 3.96.4 (Community Edition) with a 1 GB heap. `infra/nexus/setup-nexus.sh` configures it through the REST API:

```mermaid
flowchart LR
    wait["Wait until<br/>Nexus is ready"] --> pw["Replace generated<br/>admin password"]
    pw --> eula["Accept Community<br/>Edition EULA"]
    eula --> realm["Enable Docker<br/>login realm"]
    realm --> repo["Create docker-hosted<br/>registry on :8082"]
    repo --> role["Create role<br/>ci-deployer"]
    role --> user["Create user ci"]
```

Every step checks first whether it is already done, so the script can be re-run safely.

The workflow `.github/workflows/nexus-setup-test.yml` runs this setup on a throwaway Nexus inside a GitHub-hosted runner, then exercises the real pipeline path:
- publish a SNAPSHOT and a release jar
- confirm a duplicate release is rejected
- download the jar from Nexus and build the image from it
- push the image to the registry, delete it locally, pull it back, run it, and check `/api/version`

The procedure for the real host is in [`SETUP.md`](SETUP.md).

---

## 10. Security and configuration notes

- Nexus credentials are never stored in the repository. Workflows read them from the GitHub Secrets `NEXUS_USER` and `NEXUS_PASSWORD`, and pass them to Docker with `--password-stdin`.
- Workflow inputs (the deploy version, the tag name) are checked against the pattern `1.2.3` before use, and only reach shell commands through environment variables.
- The repository is public and has a self-hosted runner. GitHub warns that a pull request from a fork could change a workflow to run code on that runner. The jobs that use the runner only start from tag pushes and manual runs, which need write access. Requiring approval for workflow runs from all outside contributors (Settings → Actions → General) closes the remaining path.
- The container runs as a non-root user.
- Nexus anonymous access is disabled. The pipeline uses a dedicated `ci` user whose role only covers the three repositories it needs.
- Nexus passwords on the host live in `infra/nexus/.env`, which is git-ignored.
- The Nexus Docker registry uses plain HTTP on `localhost:8082`. Docker allows unencrypted registries on `localhost` by default. A registry on any other address would need HTTPS or an `insecure-registries` entry in the Docker daemon settings.

---

## 11. The host: a GitHub Codespace - Implemented

The host for Nexus, the runner and the deployed app is a GitHub Codespace: a Linux container on a cloud machine (2 cores, 8 GB), created from this repository. Its contents are defined in code by `.devcontainer/devcontainer.json`:
- Debian 12
- JDK 21
- a Docker engine inside the container (docker-in-docker)
- an SSH server, so it can be operated from a local terminal with `gh codespace ssh`
- a small local feature that restarts the runner whenever the codespace starts

```mermaid
flowchart TB
    start["Codespace starts<br/>manually or by gh codespace ssh"] --> ep["Container entrypoint"]
    ep --> dind["Docker engine starts"]
    ep --> sshd["SSH server starts"]
    ep --> hook["runner-autostart hook:<br/>start-runner.sh as user vscode"]
    dind --> nexus["Nexus container restarts<br/>restart: unless-stopped"]
    dind --> app["devops-app container restarts<br/>--restart unless-stopped"]
    hook --> runner["Runner online,<br/>listening for jobs"]
    idle["30 minutes idle"] --> stop["Codespace stops:<br/>all processes stop,<br/>/workspaces and Docker volumes kept"]
```

**Why a codespace and not the project laptop:** the laptop has 7.4 GB RAM. With Docker Desktop and Nexus running, 0.73 GB stayed free, measured, which is not enough for a Maven build and the app on top. In the codespace, Nexus uses 1.22 GB and about 5.5 GB stays available.

**What persists:**
- Files under `/workspaces`: the repository, the runner registration, `infra/nexus/.env`.
- Docker volumes such as `nexus-data`.

Both survived a stop/start and two rebuilds, verified by the Nexus configuration still being present.

**Reaching the app and Nexus from the laptop:** `gh codespace ports forward 8080:8080 8081:8081 -c <name>` makes them available as `http://localhost:8080` and `http://localhost:8081` on the laptop. Codespaces' browser links (`*.app.github.dev`) are the alternative, where the network allows them.
