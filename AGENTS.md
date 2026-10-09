# Agent context: devops-nexus-pipeline

This repository is a college DevOps lab project: a CI/CD pipeline for a small Spring Boot REST API, with Sonatype Nexus Repository as the artifact store. It is partially complete and is being continued by a second person, possibly on a different machine from the one it was started on.

**Read `docs/HANDOFF.md` in full before answering questions about the project's status or remaining work, and before making changes.** Read `docs/SETUP.md` before helping set up the machine that hosts Nexus, and `docs/ARCHITECTURE.md` when explaining how the system works.

## Where the state of the project is recorded

- **`docs/HANDOFF.md`** is the authoritative description of the project's state. It covers:
  - what is implemented and verified
  - the fixed names and settings shared between the workflows and the Nexus server
  - requirements for the machine that hosts Nexus
  - who owns each remaining item
  - the ordered list of remaining work, each item with a "done when" condition
  - the target demo scenario and open questions
- **`docs/SETUP.md`** is the step-by-step procedure for the Nexus host machine: installing Docker, starting and configuring Nexus, GitHub secrets, and the self-hosted runner. Each step has commands per OS, the expected result and known failures.
- **`docs/ARCHITECTURE.md`** explains how the system works, with diagrams. Each section is marked Implemented or Planned.
- **`README.md`** is the public overview and maps the assignment's requirements to where each is met.

## Situation in brief

- **Implemented and passing:**
  - app and 13 tests in `app/`
  - `app/Dockerfile`
  - the CI workflow `.github/workflows/ci.yml`
  - Nexus setup files in `infra/nexus/`
  - Maven publishing configuration (`app/pom.xml`, `app/ci-settings.xml`)
  - `.github/workflows/nexus-setup-test.yml`, which verifies the Nexus setup and the whole publish/push/pull path on a throwaway Nexus in CI
- **Not yet in place:**
  - Nexus running on the host machine
  - the GitHub secrets
  - the self-hosted runner (labels `self-hosted`, `nexus`)
  - release, deploy and rollback workflows
  - the submission report
- **Two machines:** the person continuing the work is expected to host Nexus, the self-hosted runner and the deployed container. The original laptop has too little RAM for Nexus.
- **Matching values:** the names in section 4 of `docs/HANDOFF.md` (ports, repository names, secret names, runner labels, image name) are relied on by both sides and are expected to stay as listed, unless every place that uses them is changed together.

## Codebase facts that commonly trip up changes

- Spring Boot 4.1.1 on Java 21. Test annotations live in `org.springframework.boot.webmvc.test.autoconfigure`, not the Spring Boot 3 packages.
- Builds go through the Maven wrapper (`app/mvnw`), which is stored in git as executable (mode 100755).
- The Dockerfile packages a prebuilt jar passed as build argument `JAR_FILE`. It never compiles source, by design.
- Nexus 3.96.4 is Community Edition. It returns HTTP 403 for every repository request, even for admin, until its EULA is accepted. `setup-nexus.sh` accepts it only when `NEXUS_ACCEPT_EULA=yes` is set in `infra/nexus/.env`.
- `.sh` files must keep LF line endings (enforced by `.gitattributes`). On Windows they run in Git Bash.
- Statements in the docs marked as not verified (Windows runner details, GitHub access rules for personal repositories) come from documentation or general knowledge and have not been tested in this project.
