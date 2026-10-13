#!/usr/bin/env bash
# Starts the self-hosted GitHub Actions runner in the background. Run at every container start
# by the runner-autostart feature, and safe to run by hand. Does nothing until the runner has been
# registered in /workspaces/actions-runner (docs/SETUP.md), or while it is already running.
# /workspaces survives codespace stops and rebuilds, so the registration is kept.
RUNNER_DIR=/workspaces/actions-runner

if [[ ! -f "$RUNNER_DIR/.runner" ]]; then
  echo "Runner not registered in $RUNNER_DIR; not starting it."
  exit 0
fi
if pgrep -f "$RUNNER_DIR/bin/Runner.Listener" > /dev/null; then
  echo "Runner already running."
  exit 0
fi

cd "$RUNNER_DIR"
setsid nohup ./run.sh >> runner.log 2>&1 < /dev/null &
echo "Runner started; log: $RUNNER_DIR/runner.log"
