#!/usr/bin/env bash
# Configures a fresh Nexus (started with docker-compose.yml in this folder) for the pipeline.
# Safe to run more than once: every step checks the current state first.
#
#   1. Waits until Nexus is ready.
#   2. Replaces the generated admin password with NEXUS_ADMIN_PASSWORD.
#   3. Activates the Docker Bearer Token Realm (needed for `docker login`).
#   4. Creates the Docker hosted repository "docker-hosted" with an HTTP connector on port 8082.
#   5. Creates the role "ci-deployer" (read/write on maven-releases, maven-snapshots, docker-hosted).
#   6. Creates the user "ci" with NEXUS_CI_PASSWORD and that role.
#
# Usage (Linux, macOS, or Git Bash on Windows), from this folder:
#   cp .env.example .env    # then edit the two passwords
#   bash setup-nexus.sh
#
# Settings come from the environment or from .env next to this script:
#   NEXUS_ADMIN_PASSWORD  required
#   NEXUS_CI_PASSWORD     required
#   NEXUS_URL             default http://localhost:8081
#   NEXUS_CONTAINER       default nexus (container name from docker-compose.yml)
set -euo pipefail

# Git Bash on Windows rewrites paths like /nexus-data/... in arguments; this disables that.
export MSYS_NO_PATHCONV=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "$SCRIPT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/.env"
  set +a
fi

NEXUS_URL="${NEXUS_URL:-http://localhost:8081}"
NEXUS_CONTAINER="${NEXUS_CONTAINER:-nexus}"
: "${NEXUS_ADMIN_PASSWORD:?NEXUS_ADMIN_PASSWORD is not set (see .env.example)}"
: "${NEXUS_CI_PASSWORD:?NEXUS_CI_PASSWORD is not set (see .env.example)}"

for pw in "$NEXUS_ADMIN_PASSWORD" "$NEXUS_CI_PASSWORD"; do
  if [[ "$pw" == *'"'* || "$pw" == *'\'* ]]; then
    echo "ERROR: passwords must not contain double quotes or backslashes." >&2
    exit 1
  fi
done

API="$NEXUS_URL/service/rest/v1"
ADMIN_AUTH="admin:$NEXUS_ADMIN_PASSWORD"

log() { echo "==> $*"; }

# Prints the HTTP status code of a request. Usage: status <curl args...>
status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

# Runs a request and fails with the response body if the status is not 2xx.
request() {
  local out code
  out="$(mktemp)"
  code="$(curl -s -o "$out" -w '%{http_code}' "$@")"
  if [[ "$code" != 2* ]]; then
    echo "ERROR: HTTP $code from: curl $*" >&2
    cat "$out" >&2; echo >&2
    rm -f "$out"
    exit 1
  fi
  cat "$out"
  rm -f "$out"
}

# 1. Wait for Nexus ---------------------------------------------------------------------------
log "Waiting for Nexus at $NEXUS_URL (first start can take a few minutes)"
for _ in $(seq 1 120); do
  if [[ "$(status "$API/status/writable")" == "200" ]]; then break; fi
  sleep 5
done
if [[ "$(status "$API/status/writable")" != "200" ]]; then
  echo "ERROR: Nexus did not become ready. Check: docker logs $NEXUS_CONTAINER" >&2
  exit 1
fi
log "Nexus is ready"

# 2. Admin password ---------------------------------------------------------------------------
if [[ "$(status -u "$ADMIN_AUTH" "$API/security/users?userId=admin")" == "200" ]]; then
  log "Admin password already set to NEXUS_ADMIN_PASSWORD"
else
  initial="$(docker exec "$NEXUS_CONTAINER" cat /nexus-data/admin.password 2>/dev/null || true)"
  if [[ -z "$initial" ]]; then
    echo "ERROR: admin login failed and /nexus-data/admin.password is gone, so the admin" >&2
    echo "password was already changed to something other than NEXUS_ADMIN_PASSWORD." >&2
    echo "Put the current admin password in NEXUS_ADMIN_PASSWORD, or reset Nexus with" >&2
    echo "'docker compose down -v' (deletes all Nexus data)." >&2
    exit 1
  fi
  request -u "admin:$initial" -X PUT -H 'Content-Type: text/plain' \
    --data "$NEXUS_ADMIN_PASSWORD" "$API/security/users/admin/change-password" >/dev/null
  log "Admin password changed"
fi

# 3. Docker Bearer Token Realm ----------------------------------------------------------------
realms="$(request -u "$ADMIN_AUTH" "$API/security/realms/active")"
if [[ "$realms" == *'"DockerToken"'* ]]; then
  log "Docker Bearer Token Realm already active"
else
  # Append DockerToken to the existing JSON list, e.g. ["A","B"] -> ["A","B","DockerToken"]
  updated="$(echo "$realms" | tr -d ' \n' | sed 's/]$/,"DockerToken"]/; s/\[,/[/')"
  request -u "$ADMIN_AUTH" -X PUT -H 'Content-Type: application/json' \
    --data "$updated" "$API/security/realms/active" >/dev/null
  log "Docker Bearer Token Realm activated"
fi

# 4. Docker hosted repository -----------------------------------------------------------------
if [[ "$(status -u "$ADMIN_AUTH" "$API/repositories/docker-hosted")" == "200" ]]; then
  log "Repository docker-hosted already exists"
else
  request -u "$ADMIN_AUTH" -X POST -H 'Content-Type: application/json' \
    "$API/repositories/docker/hosted" --data '{
      "name": "docker-hosted",
      "online": true,
      "storage": {
        "blobStoreName": "default",
        "strictContentTypeValidation": true,
        "writePolicy": "allow"
      },
      "docker": { "v1Enabled": false, "forceBasicAuth": true, "httpPort": 8082 }
    }' >/dev/null
  log "Repository docker-hosted created (HTTP connector on port 8082)"
fi

# 5. CI role ----------------------------------------------------------------------------------
if [[ "$(status -u "$ADMIN_AUTH" "$API/security/roles/ci-deployer")" == "200" ]]; then
  log "Role ci-deployer already exists"
else
  request -u "$ADMIN_AUTH" -X POST -H 'Content-Type: application/json' \
    "$API/security/roles" --data '{
      "id": "ci-deployer",
      "name": "ci-deployer",
      "description": "Read and write access for the CI/CD pipeline",
      "privileges": [
        "nx-repository-view-maven2-maven-releases-*",
        "nx-repository-view-maven2-maven-snapshots-*",
        "nx-repository-view-docker-docker-hosted-*"
      ],
      "roles": []
    }' >/dev/null
  log "Role ci-deployer created"
fi

# 6. CI user ----------------------------------------------------------------------------------
users="$(request -u "$ADMIN_AUTH" "$API/security/users?userId=ci")"
if [[ "$users" == *'"userId" : "ci"'* || "$users" == *'"userId":"ci"'* ]]; then
  request -u "$ADMIN_AUTH" -X PUT -H 'Content-Type: text/plain' \
    --data "$NEXUS_CI_PASSWORD" "$API/security/users/ci/change-password" >/dev/null
  log "User ci already exists; password set to NEXUS_CI_PASSWORD"
else
  request -u "$ADMIN_AUTH" -X POST -H 'Content-Type: application/json' \
    "$API/security/users" --data "{
      \"userId\": \"ci\",
      \"firstName\": \"CI\",
      \"lastName\": \"Pipeline\",
      \"emailAddress\": \"ci@example.invalid\",
      \"password\": \"$NEXUS_CI_PASSWORD\",
      \"status\": \"active\",
      \"roles\": [\"ci-deployer\"]
    }" >/dev/null
  log "User ci created"
fi

cat <<EOF

Nexus is configured.
  Web UI:           $NEXUS_URL  (user: admin)
  Maven releases:   $NEXUS_URL/repository/maven-releases/
  Maven snapshots:  $NEXUS_URL/repository/maven-snapshots/
  Docker registry:  localhost:8082  (test: docker login localhost:8082 -u ci)
  GitHub secrets:   NEXUS_USER=ci   NEXUS_PASSWORD=<value of NEXUS_CI_PASSWORD>
EOF
