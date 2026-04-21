# Kubernetes Baseline

This directory adds a Kubernetes orchestration baseline for the runtime `todo-web-app` workload.

What is intentionally in scope:

- `Deployment`, `Service`, and `Ingress` for the application
- Kustomize `base` plus `local` and `prod` overlays
- `ConfigMap` and `Secret` generation from environment-style key/value files
- startup, readiness, and liveness probes wired to real Spring Boot actuator endpoints
- resource requests and limits
- image/tag parameterization through Kustomize overlays

What is intentionally out of scope for this roadmap item:

- in-cluster PostgreSQL, Kafka, Prometheus, or Grafana deployments
- delivery automation that mutates manifests from CI
- advanced production hardening such as HPA, PodDisruptionBudget, NetworkPolicy, retry orchestration, or secret manager integration

## Structure

- `base/`: workload manifests shared across environments
- `overlays/local/`: local-cluster baseline with debug-friendly health visibility and lighter resource sizing
- `overlays/prod/`: production-oriented baseline with stricter health detail defaults and higher runtime footprint

The application still uses the existing `TODO_*` environment variable strategy. Kubernetes only becomes another orchestration layer on top of the same runtime contract already used by Docker Compose and direct Spring runs.

## Render manifests

If `kubectl` is installed:

```bash
kubectl kustomize deploy/k8s/overlays/local
kubectl kustomize deploy/k8s/overlays/prod
```

If you do not have `kubectl` locally but you do have Docker:

```bash
docker run --rm -v "$PWD":/work -w /work bitnami/kubectl:latest kustomize deploy/k8s/overlays/local
docker run --rm -v "$PWD":/work -w /work bitnami/kubectl:latest kustomize deploy/k8s/overlays/prod
```

## Offline schema validation

Without a live Kubernetes API server, `kubectl apply --dry-run=client` is not fully reliable because discovery still tries to reach cluster OpenAPI endpoints.

For offline validation of rendered manifests, use `kubeconform`:

```bash
docker run --rm -v "$PWD":/work -w /work bitnami/kubectl:latest kustomize deploy/k8s/overlays/local > /tmp/todo-k8s-local.yaml
docker run --rm -v /tmp:/tmp ghcr.io/yannh/kubeconform:latest -summary -strict /tmp/todo-k8s-local.yaml

docker run --rm -v "$PWD":/work -w /work bitnami/kubectl:latest kustomize deploy/k8s/overlays/prod > /tmp/todo-k8s-prod.yaml
docker run --rm -v /tmp:/tmp ghcr.io/yannh/kubeconform:latest -summary -strict /tmp/todo-k8s-prod.yaml
```

## Apply overlays

Local overlay:

```bash
kubectl apply -k deploy/k8s/overlays/local
```

Prod overlay:

```bash
kubectl apply -k deploy/k8s/overlays/prod
```

## Configure runtime values

Before applying an overlay, review these files and replace placeholder values with real environment-specific endpoints and credentials:

- `deploy/k8s/overlays/local/configmap.env`
- `deploy/k8s/overlays/local/secret.env`
- `deploy/k8s/overlays/prod/configmap.env`
- `deploy/k8s/overlays/prod/secret.env`

These values are expected to point at external dependencies. This baseline deliberately does not deploy PostgreSQL or Kafka into the cluster.

Key mappings:

- database connection: `TODO_DB_HOST`, `TODO_DB_PORT`, `TODO_DB_NAME`, `TODO_DB_USERNAME`, `TODO_DB_PASSWORD`
- Kafka wiring: `TODO_KAFKA_ENABLED`, `TODO_KAFKA_BOOTSTRAP_SERVERS`, `TODO_KAFKA_TOPIC_REMINDER_SCHEDULED_V1`, `TODO_KAFKA_CONSUMER_GROUP_ID`
- actuator exposure and health detail policy: `TODO_OBS_ENDPOINTS_WEB_EXPOSURE_INCLUDE`, `TODO_OBS_HEALTH_SHOW_COMPONENTS`, `TODO_OBS_HEALTH_SHOW_DETAILS`

## Image strategy

The CI pipeline already publishes images to:

```text
ghcr.io/<owner>/todo-devops
```

The overlays parameterize image tags through `images` in each `kustomization.yaml`.

Examples:

- local overlay defaults to `:local`
- prod overlay defaults to `:latest`

For an immutable deployment, replace the prod tag with the CI-published SHA tag, for example `sha-<git-sha>`. This remains a manual step in this roadmap item; automated promotion belongs to the delivery/CD item.

## Rollout and availability checks

After apply:

```bash
kubectl -n todo-devops-local rollout status deploy/todo-web-app
kubectl -n todo-devops-local get ingress,svc,pods
kubectl -n todo-devops-local describe deploy todo-web-app
```

Health endpoints used by probes:

- startup: `/actuator/health`
- readiness: `/actuator/health/readiness`
- liveness: `/actuator/health/liveness`

If your ingress controller is reachable, verify application readiness through the host configured in the overlay. For the local overlay the default host is `todo-devops.localtest.me`.

## Relation to Docker Compose

Docker Compose remains the primary local all-in-one environment for this project.

This Kubernetes baseline does not replace Compose:

- Compose still runs local Postgres, Kafka, Prometheus, and Grafana together
- Kubernetes baseline covers only the application workload and its runtime wiring
- external dependency addresses must be supplied explicitly through the overlay config
