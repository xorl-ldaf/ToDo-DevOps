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

The tracked overlays still keep a human-readable default image tag through `images` in each `kustomization.yaml`.

Examples:

- local overlay defaults to `:local`
- prod overlay defaults to `:latest`

For delivery/CD, the repository now uses immutable digest promotion instead of guessing tags:

- `CI` uploads a `published-image-metadata` artifact for each non-PR publish run
- the artifact contains `image_digest_ref`, for example `ghcr.io/<owner>/todo-devops@sha256:<digest>`
- the deploy workflow consumes that artifact and renders a transient promotion overlay on top of the tracked Kustomize overlay
- the tracked YAML remains unchanged; the immutable image pin is injected only for the deployment run

You can render the same immutable manifest locally:

```bash
chmod +x ./scripts/render-k8s-overlay.sh
./scripts/render-k8s-overlay.sh prod ghcr.io/xorl-ldaf/todo-devops@sha256:<digest> build/deploy/prod-rendered.yaml
kubectl apply -f build/deploy/prod-rendered.yaml
```

The helper script enforces digest-based references and keeps the existing `deploy/k8s/base` plus environment overlay structure intact.

## Delivery / CD baseline

The repository now includes a separate GitHub Actions deploy workflow:

- workflow file: `.github/workflows/deploy.yaml`
- trigger: manual `workflow_dispatch`
- input `target_environment`: explicit deployment environment (`prod` or `local`)
- input `publish_run_id`: CI workflow run ID that produced the `published-image-metadata` artifact

The deploy workflow does not rebuild the image. It links back to the existing publish pipeline by downloading the exact image metadata artifact from the selected CI run and deploying that immutable digest into Kubernetes.

## Required GitHub-side configuration

The deploy job uses GitHub Environments. Create at least the `prod` environment in the repository settings.

Required environment secret:

- `KUBE_CONFIG`: raw kubeconfig content for the target cluster

Optional environment variables:

- `K8S_NAMESPACE`: overrides the default namespace derived from the overlay (`todo-devops-prod` or `todo-devops-local`)
- `KUSTOMIZE_MATCH_IMAGE`: overrides the tracked base image name if the manifests are renamed later; current default is `ghcr.io/xorl-ldaf/todo-devops`

Recommended environment setup:

- `prod` environment backed by a cluster-reachable kubeconfig
- optional `local` environment only when the runner can reach a local or self-hosted cluster

Because GitHub Environment creation and secret entry live outside the repository, those parts must still be configured manually in GitHub.

## Run the deploy workflow

1. Run `.github/workflows/ci.yaml` on a commit that should be promoted.
2. Note the resulting GitHub Actions run ID for that CI execution.
3. Start `.github/workflows/deploy.yaml`.
4. Set `target_environment`.
5. Pass the CI `publish_run_id`.

The workflow will:

- download `published-image-metadata`
- validate that the artifact belongs to the current repository
- verify that the digest reference exists in GHCR
- render the chosen Kustomize overlay with the immutable digest
- `kubectl apply` the rendered manifest
- wait on `kubectl rollout status`
- run post-deploy application checks through `kubectl port-forward`

## Rollout and availability checks

After apply, the baseline checks are:

```bash
kubectl -n <namespace> rollout status deploy/todo-web-app
kubectl -n <namespace> get ingress,svc,pods
kubectl -n <namespace> describe deploy todo-web-app
```

Health endpoints used by probes:

- startup: `/actuator/health`
- readiness: `/actuator/health/readiness`
- liveness: `/actuator/health/liveness`

The deploy workflow formalizes two post-rollout checks:

- infrastructure-level: `kubectl rollout status deployment/todo-web-app`
- application-level: `curl` against `/actuator/health/readiness` and `/api/users` through `kubectl port-forward service/todo-web-app 18080:80`

This avoids depending on external ingress reachability during the baseline CD step while still validating the live workload from outside the pod.

## Relation to Docker Compose

Docker Compose remains the primary local all-in-one environment for this project.

This Kubernetes baseline does not replace Compose:

- Compose still runs local Postgres, Kafka, Prometheus, and Grafana together
- Kubernetes baseline covers only the application workload and its runtime wiring
- external dependency addresses must be supplied explicitly through the overlay config
