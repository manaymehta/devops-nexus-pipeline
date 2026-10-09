# Agent context: devops-nexus-pipeline

This repository is a college DevOps lab project: a CI/CD pipeline for a small Spring Boot REST API, with Sonatype Nexus Repository as the artifact store. It is partially complete and is being continued by a second person, possibly on a different machine from the one it was started on.

**Read `docs/HANDOFF.md` in full before answering questions about the project's status or remaining work, and before making changes.** Read `docs/ARCHITECTURE.md` when explaining how the system works.

## Where the state of the project is recorded

- **`docs/HANDOFF.md`** is the authoritative description of the project's state. It covers:
  - what is implemented and verified
  - the fixed names and settings shared between the workflows and the Nexus server
  - requirements for the machine that hosts Nexus
  - who owns each remaining item
  - the ordered list of remaining work, each item with a "done when" condition
  - the target demo scenario and open questions
- **`docs/ARCHITECTURE.md`** explains how the system works, with diagrams. Each section is marked Implemented or Planned.
- **`README.md`** is the public overview and maps the assignment's requirements to where each is met.

## Situation in brief

- **Implemented and passing:**
  - app and 13 tests in `app/`
  - `app/Dockerfile`
  - the CI workflow `.github/workflows/ci.yml` on GitHub-hosted runners
- **Not yet in place:**
  - the Nexus server
  - the GitHub Actions self-hosted runner (labels `self-hosted`, `nexus`)
  - Maven publishing configuration
  - release, deploy and rollback workflows
  - the submission report
- **Two machines:** the person continuing the work is expected to host Nexus, the self-hosted runner and the deployed container. The original laptop has too little RAM for Nexus.
- **Matching values:** the names in section 4 of `docs/HANDOFF.md` (ports, repository names, secret names, runner labels, image name) are relied on by both sides and are expected to stay as listed, unless every place that uses them is changed together.

## Codebase facts that commonly trip up changes

- Spring Boot 4.1.1 on Java 21. Test annotations live in `org.springframework.boot.webmvc.test.autoconfigure`, not the Spring Boot 3 packages.
- Builds go through the Maven wrapper (`app/mvnw`), which is stored in git as executable (mode 100755).
- The Dockerfile packages a prebuilt jar passed as build argument `JAR_FILE`. It never compiles source, by design.
- Facts marked "not yet verified" in `docs/HANDOFF.md` (Nexus memory settings, edition limits) come from general knowledge and have not been tested here.
