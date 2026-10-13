#!/usr/bin/env bash
# Installs a container-start hook that starts the self-hosted runner. The hook runs as root from
# the container's entrypoint, like the Docker and SSH start scripts, so the runner it starts stays
# alive. (A runner left running by postStartCommand is stopped when that command finishes.)
set -e
RUNNER_USER="${_REMOTE_USER:-vscode}"
cat > /usr/local/share/runner-autostart.sh << EOF
#!/usr/bin/env bash
# Never blocks or fails container start; output goes to /tmp/runner-autostart.log.
su $RUNNER_USER -s /bin/bash -c 'bash /workspaces/devops-nexus-pipeline/.devcontainer/start-runner.sh' \\
  > /tmp/runner-autostart.log 2>&1 || true
EOF
chmod 755 /usr/local/share/runner-autostart.sh
