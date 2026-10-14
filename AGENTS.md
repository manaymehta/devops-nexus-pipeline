# Agent context: devops-nexus-pipeline

This repository is a college DevOps lab project: a CI/CD pipeline for a small Spring Boot REST API, with Sonatype Nexus Repository as the artifact store. The pipeline is complete and verified; the remaining work is the demo rehearsal, screenshots and the submission report.

**Read `docs/HANDOFF.md` in full before answering questions about the project's status or remaining work, and before making changes.** Read `docs/SETUP.md` before operating the host (a GitHub Codespace), and `docs/ARCHITECTURE.md` when explaining how the system works.

## Where the state of the project is recorded

- **`docs/HANDOFF.md`** is the authoritative description of the project's state. It covers:
  - what is implemented and verified, with run IDs
  - what Nexus currently stores, and which versions are already taken
  - technical facts about the codebase, Nexus and the codespace host
  - the fixed names and settings shared between the workflows and the Nexus server
  - the demo scenario and the remaining work
- **`docs/SETUP.md`** covers how the codespace host was set up and how it is operated day to day, through `gh codespace ssh` and `gh codespace ports forward`.
- **`docs/ARCHITECTURE.md`** explains how the system works, with diagrams.
- **`README.md`** is the public overview and maps the assignment's requirements to where each is met.

## Situation in brief

- **Workflows:**
  - `ci.yml`: GitHub-hosted runners
  - `release.yml`: on tag `v*.*.*`
  - `deploy.yml`: manual or called by release, used for rollback
  - `nexus-setup-test.yml`
- **Host:** the GitHub Codespace `devops-nexus-host-7v7xjqv9j6762x445` runs:
  - Nexus, in Docker
  - the self-hosted runner `codespace-nexus` (labels `self-hosted`, `nexus`)
  - the deployed `devops-app` container on port 8080

  It stops after 30 idle minutes. Nexus and the runner restart by themselves when it starts again.
- **Released versions:** 1.0.0, 1.1.0 and 1.2.0 exist in Nexus. Release versions cannot be published twice, so the next release is 1.3.0 or higher.
- **Matching values:** the names in section 4 of `docs/HANDOFF.md` (ports, repository names, secret names, runner labels, image name) are relied on by the workflows and Nexus. They are expected to stay as listed, unless every place that uses them is changed together.

## Codebase facts that commonly trip up changes

- Spring Boot 4.1.1 on Java 21. Test annotations live in `org.springframework.boot.webmvc.test.autoconfigure`, not the Spring Boot 3 packages.
- Builds go through the Maven wrapper (`app/mvnw`), which is stored in git as executable (mode 100755), like the other shell scripts.
- The Dockerfile packages a prebuilt jar passed as build argument `JAR_FILE`. It never compiles source, by design.
- Nexus 3.96.4 is Community Edition. It returns HTTP 403 for every repository request until its EULA is accepted. On the host it is accepted.
- `.sh` files must keep LF line endings (enforced by `.gitattributes`).
- The codespace's repository clone at `/workspaces/devops-nexus-pipeline` does not update itself. Changes under `.devcontainer/` need a `git pull` there, then `gh codespace rebuild`.
- Commits carry no AI attribution or co-author trailers.
