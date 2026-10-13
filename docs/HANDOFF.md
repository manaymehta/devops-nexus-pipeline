# Project State and Remaining Work

This document describes the project's state precisely enough for a person, or an AI assistant acting for them, to continue the work without access to earlier conversations. It records facts, agreed names and settings, and what is left.

- How the system works: [`ARCHITECTURE.md`](ARCHITECTURE.md).
- How the host (a GitHub Codespace) is set up and operated: [`SETUP.md`](SETUP.md).

Last updated: 2026-10-13.

---

## 1. Project context

- **Purpose:** college DevOps lab assignment. The brief requires:
  - version control (Git/GitHub)
  - an application build
  - automated testing
  - Docker containerization
  - a CI/CD pipeline (Jenkins or GitHub Actions)
  - deployment
  - documentation
  - a live demonstration of the working pipeline
- **Chosen topic:** *Artifact Management in DevOps Using Nexus Repository*, widened into a full CI/CD pipeline. Nexus stores every released version, and deployments pull from Nexus.
- **Repository:** https://github.com/manaymehta/devops-nexus-pipeline (public, default branch `main`). It is owned by `manaymehta`, who does all remaining work.
- **History:**
  - A teammate was originally going to host Nexus on their machine. That did not happen.
  - The repository was transferred to the teammate's account and back.
  - The host is now a GitHub Codespace on `manaymehta`'s account.
- **Commit history:** AI co-author trailers were removed from all early commits by a history rewrite (force-push on 2026-10-13). Commits are authored by the repository owner only.

---

## 2. What exists and is verified

| Item | State | Evidence |
|---|---|---|
| Spring Boot app in `app/` | Complete | Builds with `./mvnw verify`, serves all endpoints |
| Tests | 13, all passing | Every CI and release run |
| `app/Dockerfile` | Complete | Built and smoke-tested in CI and in releases |
| `ci.yml` | Complete, green | Build + test, then Docker image + smoke test, on GitHub-hosted runners |
| `nexus-setup-test.yml` | Complete, green | Throwaway Nexus in CI: setup, publish, duplicate rejection, image push/pull. Run `37945141137`. |
| Host: GitHub Codespace | Running | `.devcontainer/` (JDK 21, Docker, sshd, runner auto-start). See `SETUP.md` |
| Nexus on the host | Configured | `setup-nexus.sh` run; status 200; `docker login localhost:8082 -u ci` succeeds; anonymous requests get 401 |
| GitHub secrets `NEXUS_USER`, `NEXUS_PASSWORD` | Set | `gh secret list` |
| Self-hosted runner `codespace-nexus` | Registered, online when the codespace runs | Labels `self-hosted, X64, nexus, Linux`; restarts by itself on codespace start |
| `release.yml` | Complete, verified | Run `38419484993` (1.0.0), run `38419898826` (1.1.0) |
| `deploy.yml` | Complete, verified | Run `38420296234`: rollback from 1.1.0 to 1.0.0 |
| Duplicate release rejected | Verified | Re-run of `38419898826`: HTTP 409 from Nexus |
| Jar from Nexus runs outside Docker | Verified | 1.1.0 jar downloaded with `curl -u`, run with `java -jar` |
| App and Nexus reachable from the laptop | Verified | `gh codespace ports forward 8080:8080 8081:8081` |
| Documentation | Updated for the codespace host | `README.md`, `docs/`, `AGENTS.md` |
| Project report and screenshots | Not started | Section 6 |

### Current contents of Nexus

| Repository | Contents |
|---|---|
| `maven-releases` | `devops-app` 1.0.0 and 1.1.0 |
| `docker-hosted` | `devops-app:1.0.0` and `devops-app:1.1.0` |
| `maven-snapshots` | empty |

Deployed at last check: **1.0.0** (after the rollback test).

**The next release version is `1.2.0` or higher.** 1.0.0 and 1.1.0 exist and cannot be released again.

---

## 3. Technical facts

### Codebase
- **Stack:** Java 21, Spring Boot **4.1.1**, Maven **3.9.16** through the Maven wrapper 3.3.4.
- **Coordinates:** groupId `com.devops`, artifactId `devops-app`, version `1.0.0-SNAPSHOT` in `pom.xml`. Releases set the version from the tag, in the runner's checkout only; the change is never committed.
- **Spring Boot 4 test packages:** `org.springframework.boot.webmvc.test.autoconfigure.{WebMvcTest, AutoConfigureMockMvc}`.
- **Version reporting:** the `build-info` goal writes `META-INF/build-info.properties`. `GET /api/version` returns name, version and build time.
- **Publishing:**
  - `./mvnw -s ci-settings.xml deploy`, with credentials from the env vars `NEXUS_USER` and `NEXUS_PASSWORD`.
  - The Nexus URL is the Maven property `nexus.url`, default `http://localhost:8081`.
- **Dockerfile:**
  - Base image `eclipse-temurin:21-jre-alpine`, non-root user, `HEALTHCHECK`.
  - Takes a prebuilt jar via build arg `JAR_FILE` and never compiles.
  - `.dockerignore` admits only `target/*.jar`.

### Repository and Git
- `app/mvnw`, `infra/nexus/setup-nexus.sh`, `.devcontainer/start-runner.sh` and `.devcontainer/runner-autostart/install.sh` are stored as mode `100755`.
- `.gitattributes` keeps `*.sh` LF.
- Actions in use: `actions/checkout@v7`, `actions/setup-java@v6`, `actions/upload-artifact@v7`, `actions/download-artifact@v8`.
- **`gh` token scopes needed:** `workflow` to push workflow files, `codespace` to manage codespaces.

### Nexus
- `sonatype/nexus3:3.96.4`, Community Edition.
- **EULA:** it answers every repository request with 403 until the EULA is accepted. On the host it was accepted by `setup-nexus.sh` with `NEXUS_ACCEPT_EULA=yes`.
- **Memory:** 1 GB heap. Measured at 1.18–1.22 GiB.
- **Security:**
  - Anonymous access is disabled.
  - The DockerToken realm is active.
  - Role `ci-deployer` has view privileges on `maven-releases`, `maven-snapshots` and `docker-hosted`.
- **Write policies:**
  - `maven-releases` rejects redeploys with HTTP 409.
  - `docker-hosted` allows tag overwrite.

### Codespace host
- **Name:** `devops-nexus-host-7v7xjqv9j6762x445`. Machine `basicLinux32gb` (2 cores, 8 GB, 32 GB disk), idle timeout 30 minutes.
- **Billing:** usage is billed to `manaymehta`'s free Codespaces allowance (120 core-hours and 15 GB-month per month on GitHub Free). The 2-core machine uses 2 core-hours per hour. Without a payment method, usage beyond the allowance is blocked, not charged.
- **Where things live:**
  - Repository at `/workspaces/devops-nexus-pipeline`, a git clone that needs `git pull` to pick up new commits.
  - Runner at `/workspaces/actions-runner`, version 2.338.0, log at `runner.log`.
  - Passwords in `/workspaces/devops-nexus-pipeline/infra/nexus/.env`: two random 24-character passwords, file mode 600.
- **Restarts:** after a codespace start, the Docker engine and Nexus take a few minutes. The runner restarts through the entrypoint hook.
- **Known build facts:**
  - The `devcontainers/java` image failed the docker-in-docker feature: its Yarn apt source has a signing key (`62D54FD4003F6525`) that no longer verifies.
  - A process left running by `postStartCommand` is stopped when that command ends. Processes started from the container entrypoint survive.
- **VS Code connection:** VS Code, both browser and desktop, did not finish connecting from the project laptop. The connection attempts reached the codespace's VS Code server, and the cause was not found. All work is done with `gh codespace ssh` and `gh codespace ports forward` instead.

---

## 4. Fixed names and settings

Changing one of these means changing every place that uses it.

| Item | Value | Defined in |
|---|---|---|
| Nexus web UI and REST API | `http://localhost:8081` (on the host) | `docker-compose.yml`, `pom.xml` (`nexus.url`) |
| Nexus Docker registry | `localhost:8082` | `docker-compose.yml`, `setup-nexus.sh` |
| Maven repositories | `maven-releases`, `maven-snapshots` | Nexus defaults, `pom.xml` |
| Docker hosted repository | `docker-hosted` | `setup-nexus.sh` |
| Image name | `localhost:8082/devops-app:<version>` | `release.yml`, `deploy.yml` |
| Maven `<server>` ids | `nexus-releases`, `nexus-snapshots` | `pom.xml`, `ci-settings.xml` |
| Nexus CI user / role | `ci` / `ci-deployer` | `setup-nexus.sh` |
| GitHub Actions secrets | `NEXUS_USER` (= `ci`), `NEXUS_PASSWORD` (= `NEXUS_CI_PASSWORD`) | repository settings |
| Self-hosted runner | name `codespace-nexus`, labels `self-hosted`, `nexus`; jobs use `runs-on: [self-hosted, nexus]` | runner config |
| Deployed container | name `devops-app`, port `8080`, `--restart unless-stopped` | `deploy.yml` |
| Version tags | `vX.Y.Z`; the Maven/image version is the tag without `v` | `release.yml` |

---

## 5. Demo scenario

**Before the demo:**
1. Start the codespace: `gh codespace ssh -c devops-nexus-host-7v7xjqv9j6762x445 -- true`.
2. Wait until the runner is online and Nexus answers, a few minutes after start.
3. Start `gh codespace ports forward 8080:8080 8081:8081 -c devops-nexus-host-7v7xjqv9j6762x445` in a separate terminal and leave it running.
4. Optionally, make the deployed version the one to start from (Actions → Deploy → `1.1.0`).
5. Keep the codespace active during the demo: it stops after 30 minutes without terminal activity, and a running job does not count as activity.

**Steps** (versions below assume 1.0.0 and 1.1.0 already exist):
1. `http://localhost:8080/api/version` shows the running version, e.g. `1.1.0`.
2. A code change is pushed and tagged `v1.2.0`. The release workflow runs live, and `1.2.0` appears in Nexus (`http://localhost:8081`, Browse) and is deployed.
3. A deliberately broken test is pushed. CI turns red. A release tagged on it would fail at the test stage, so nothing reaches Nexus.
4. The `v1.2.0` release is re-run. Nexus rejects the duplicate with HTTP 409.
5. Actions → Deploy → Run workflow with `1.1.0`. The old image is pulled from Nexus and running again with its original build time, with no rebuild.
6. A specific jar is downloaded from Nexus with `curl -u` and run with `java -jar`, which shows the artifact is reusable outside Docker.

---

## 6. Remaining work

| Item | Notes |
|---|---|
| Require approval for outside contributors' workflow runs | Settings → Actions → General → "Approval for running fork pull request workflows from contributors" → *Require approval for all external contributors*. Protects the self-hosted runner on this public repository (`ARCHITECTURE.md` section 10). |
| Demo rehearsal and screenshots | Screenshots: a green CI run; a red CI run from a failing test; the Nexus browse view with several versions; `docker-hosted` contents; `/api/version` before and after a rollback; the rejected re-release (HTTP 409). |
| Report | Introduction; architecture (diagrams in `ARCHITECTURE.md`); tools and why each was chosen; each pipeline stage; Nexus concepts; challenges (see below); conclusion. |
| Optional: snapshot publishing | A job on `[self-hosted, nexus]` for pushes to `main` running `./mvnw -s ci-settings.xml deploy`. Only succeeds while the codespace runs. |
| Optional extras, not accepted or rejected | JaCoCo coverage report; Nexus cleanup policies for old snapshots |

**Challenges worth covering in the report:**
- Laptop RAM: 0.73 GB free with Nexus running, measured. This led to the codespace host and to GitHub Actions with a self-hosted runner instead of Jenkins.
- The Nexus Community Edition EULA blocks every request until accepted.
- Nexus anonymous access had to be disabled before Maven would send credentials.
- The dev container build failed on an expired Yarn signing key in the Java base image.
- The runner started by `postStartCommand` was killed. Fixed with an entrypoint hook.
- VS Code could not connect to the codespace from the laptop's network. Everything is operated through `gh codespace ssh` and port forwarding.

---

## 7. Open questions

- Whether the faculty accepts a deployment to a cloud development environment (Codespaces), or expects a VM such as AWS EC2.
- Whether the faculty requires Jenkins specifically. GitHub Actions was chosen because Jenkins needs more memory.
