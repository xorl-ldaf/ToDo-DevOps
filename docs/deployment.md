# Deployment Notes

## What exists

This repository contains a real Kubernetes deployment baseline for the `todo-web-app` workload:

- tracked manifests in `deploy/k8s/base`
- environment overlays in `deploy/k8s/overlays/local` and `deploy/k8s/overlays/prod`
- digest-based render helper in `scripts/render-k8s-overlay.sh`
- manual GitHub Actions deployment workflow in `.github/workflows/deploy.yaml`

The deploy baseline is application-focused. It does not provision the full data/observability platform inside the cluster.

## What is intentionally not here

- no in-cluster PostgreSQL deployment
- no in-cluster Kafka deployment
- no in-cluster Prometheus or Grafana deployment
- no secret-manager integration
- no automatic rollback controller
- no GitOps controller

## Kubernetes layout

- `deploy/k8s/base/deployment.yaml`
  Base `Deployment` with actuator probes, rolling update strategy, non-root runtime, read-only root filesystem, seccomp, resource defaults, pod anti-affinity preference, and topology spread.
- `deploy/k8s/base/pod-disruption-budget.yaml`
  Baseline `PodDisruptionBudget` with `minAvailable: 1`.
- `deploy/k8s/base/service.yaml`
  ClusterIP service exposing port `80` to the app container.
- `deploy/k8s/base/ingress.yaml`
  Base ingress definition.
- `deploy/k8s/overlays/local`
  Namespace `todo-devops-local`, one replica, debug-friendly actuator visibility, ingress host `todo-devops.localtest.me`.
- `deploy/k8s/overlays/prod`
  Namespace `todo-devops-prod`, two replicas, stricter actuator visibility, ingress host `todo.example.com`.

## Runtime configuration inputs

Before applying an overlay, review:

- `deploy/k8s/overlays/local/configmap.env`
- `deploy/k8s/overlays/local/secret.env`
- `deploy/k8s/overlays/prod/configmap.env`
- `deploy/k8s/overlays/prod/secret.env`

Important variables carried by the overlays:

- database
  `TODO_DB_HOST`, `TODO_DB_PORT`, `TODO_DB_NAME`, `TODO_DB_USERNAME`, `TODO_DB_PASSWORD`
- Kafka boundary
  `TODO_KAFKA_ENABLED`, `TODO_KAFKA_BOOTSTRAP_SERVERS`, `TODO_KAFKA_TOPIC_REMINDER_SCHEDULED_V1`, `TODO_KAFKA_CONSUMER_GROUP_ID`
- Kafka outbox worker
  `TODO_KAFKA_OUTBOX_BATCH_SIZE`, `TODO_KAFKA_OUTBOX_MAX_ATTEMPTS`, `TODO_KAFKA_OUTBOX_RETRY_BACKOFF`, `TODO_KAFKA_OUTBOX_PROCESSING_TIMEOUT`, `TODO_KAFKA_OUTBOX_INITIAL_DELAY`, `TODO_KAFKA_OUTBOX_FIXED_DELAY`
- Telegram
  `TODO_TELEGRAM_ENABLED`, `TODO_TELEGRAM_BOT_TOKEN`, `TODO_TELEGRAM_BASE_URL`
- reminder delivery worker
  `TODO_REMINDER_DELIVERY_ENABLED`, `TODO_REMINDER_DELIVERY_INITIAL_DELAY_MS`, `TODO_REMINDER_DELIVERY_FIXED_DELAY_MS`, `TODO_REMINDER_DELIVERY_BATCH_SIZE`, `TODO_REMINDER_DELIVERY_MAX_ATTEMPTS`, `TODO_REMINDER_DELIVERY_RETRY_BACKOFF`, `TODO_REMINDER_DELIVERY_PROCESSING_TIMEOUT`
- observability exposure
  `TODO_OBS_ENDPOINTS_WEB_EXPOSURE_INCLUDE`, `TODO_OBS_HEALTH_SHOW_COMPONENTS`, `TODO_OBS_HEALTH_SHOW_DETAILS`
- graceful shutdown
  `TODO_APP_SHUTDOWN_TIMEOUT`

## Runtime hardening baseline

The workload baseline now assumes:

- container runs as UID/GID `10001`
- `runAsNonRoot: true`
- `allowPrivilegeEscalation: false`
- dropped Linux capabilities
- `readOnlyRootFilesystem: true`
- `seccompProfile: RuntimeDefault`
- writable `emptyDir` mounted at `/tmp`

The image and manifests were updated together so these settings match the current container runtime behavior.

## Render manifests locally

If `kubectl` is available:

```bash
kubectl kustomize deploy/k8s/overlays/local
kubectl kustomize deploy/k8s/overlays/prod
```

Apply tracked overlays directly:

```bash
kubectl apply -k deploy/k8s/overlays/local
kubectl apply -k deploy/k8s/overlays/prod
```

## Render an immutable digest manifest

The repository uses digest promotion for deploys. The helper script renders a transient Kustomize layer that replaces the tracked image tag with a published digest reference.

Example:

```bash
./scripts/render-k8s-overlay.sh \
  prod \
  ghcr.io/xorl-ldaf/todo-devops@sha256:<digest> \
  build/deploy/prod-rendered.yaml

kubectl apply -f build/deploy/prod-rendered.yaml
```

## GitHub Actions deploy workflow

Workflow file:

- `.github/workflows/deploy.yaml`

Trigger:

- manual `workflow_dispatch`

Inputs:

- `target_environment`
  Must be `prod` or `local`
- `publish_run_id`
  GitHub Actions run ID of the CI run that uploaded `published-image-metadata`

The deploy workflow does not rebuild the image. It downloads the `published-image-metadata` artifact from a prior CI run, validates the digest reference, renders the chosen overlay with that digest, applies it, and then performs rollout plus smoke verification.

## Rollout verification

The baseline rollout checks are:

```bash
kubectl -n <namespace> rollout status deployment/todo-web-app --timeout=240s
kubectl -n <namespace> get deployment,replicaset,pod,service,ingress,pdb
kubectl -n <namespace> describe deployment todo-web-app
```

The application-level checks used by the workflow are:

```bash
kubectl -n <namespace> port-forward service/todo-web-app 18080:80
curl --fail http://127.0.0.1:18080/actuator/health/readiness
curl --fail http://127.0.0.1:18080/api/users
```

Probe endpoints defined in the manifests:

- startup: `/actuator/health`
- readiness: `/actuator/health/readiness`
- liveness: `/actuator/health/liveness`

## Relationship to Docker Compose

Compose remains the primary local full-stack environment. Kubernetes in this repository is a deployment baseline for the application workload, not a replacement for the local all-in-one stack.
