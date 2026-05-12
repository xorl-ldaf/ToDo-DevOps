# Runbook: Rollback and First Checks

## Goal

Stabilize a bad deployment without inventing a rollback platform that does not exist in this repository.

## Check before rolling back

First determine whether the issue is really the new image.

1. Inspect deployment state:

   ```bash
   kubectl -n <namespace> get deployment,replicaset,pod
   kubectl -n <namespace> describe deployment todo-web-app
   ```

2. Read logs:

   ```bash
   kubectl -n <namespace> logs deployment/todo-web-app --all-containers --tail=200
   ```

3. Confirm the active image:

   ```bash
   kubectl -n <namespace> get deployment todo-web-app \
     -o jsonpath='{.spec.template.spec.containers[?(@.name=="app")].image}'
   ```

4. Re-check external dependency wiring:

- database host and credentials from the overlay
- Kafka bootstrap servers if Kafka is enabled
- Telegram token only if Telegram delivery is enabled

If the failure is caused by broken `ConfigMap` or `Secret` values, changing only the image will not fix it.

## Rollback option A: Kubernetes deployment history

If a healthy previous ReplicaSet exists, use the built-in deployment rollback:

```bash
kubectl -n <namespace> rollout undo deployment/todo-web-app
kubectl -n <namespace> rollout status deployment/todo-web-app --timeout=240s
```

Use this only when the cluster still has a good previous revision to return to.

## Rollback option B: redeploy a previous digest

When you know the last good image digest, redeploy it explicitly:

```bash
./scripts/render-k8s-overlay.sh \
  prod \
  ghcr.io/xorl-ldaf/todo-devops@sha256:<previous-good-digest> \
  build/deploy/rollback-rendered.yaml

kubectl apply -f build/deploy/rollback-rendered.yaml
kubectl -n <namespace> rollout status deployment/todo-web-app --timeout=240s
```

You can also use the manual deploy workflow again with the older CI `publish_run_id`.

## After rollback

Repeat the smoke checks:

```bash
kubectl -n <namespace> port-forward service/todo-web-app 18080:80
curl --fail http://127.0.0.1:18080/actuator/health/readiness
curl --fail http://127.0.0.1:18080/api/users
```

## What to capture for follow-up

Before closing the incident, keep:

- failing deployment logs
- the bad image digest
- the CI run ID used for the deploy
- the overlay and namespace involved
- whether the fault was image-related or environment-related
