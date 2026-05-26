# Kubernetes Manifests

This directory contains the tracked Kubernetes baseline for `todo-web-app`.

The overlay `secret.env` files are renderable examples only. They contain `change-me-*` placeholders so Kustomize can generate a `Secret`, but real cluster credentials should come from the target environment or a production secret manager integration.

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
