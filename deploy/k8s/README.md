# Kubernetes Manifests

This directory contains the tracked Kubernetes baseline for `todo-web-app`.

Structure:

- `base/`
- `overlays/local/`
- `overlays/prod/`

Use [docs/deployment.md](../../docs/deployment.md) as the operational source of truth for:

- render and apply commands
- GitHub Actions deploy workflow usage
- required secrets and environment variables
- rollout and post-deploy verification
- current deployment boundaries and non-goals
