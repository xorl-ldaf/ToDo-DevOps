# Runbook: Post-Deploy Smoke Verification

## Goal

Verify that a deployment to Kubernetes produced a usable workload, not just a successful `kubectl apply`.

## Baseline checks

1. Confirm rollout:

   ```bash
   kubectl -n <namespace> rollout status deployment/todo-web-app --timeout=240s
   ```

2. Inspect workload state:

   ```bash
   kubectl -n <namespace> get deployment,replicaset,pod,service,ingress
   ```

3. Port-forward the service:

   ```bash
   kubectl -n <namespace> port-forward service/todo-web-app 18080:80
   ```

4. Verify readiness:

   ```bash
   curl --fail http://127.0.0.1:18080/actuator/health/readiness
   ```

5. Verify the API baseline:

   ```bash
   curl --fail http://127.0.0.1:18080/api/users
   ```

Expected result:

- readiness reports `UP`
- `/api/users` returns a JSON array

## Verify the deployed image pin

If the deploy was meant to promote a specific digest, confirm it directly:

```bash
kubectl -n <namespace> get deployment todo-web-app \
  -o jsonpath='{.spec.template.spec.containers[?(@.name=="app")].image}'
```

Compare the result to the digest reference from the selected CI run artifact.

## Optional ingress check

After service-level checks pass, verify the ingress host configured by the overlay:

- `todo-devops.localtest.me` for `local`
- `todo.example.com` for `prod`

Ingress reachability is secondary to the service port-forward check. If ingress fails but service-level checks pass, treat it as an ingress/network issue rather than as an application failure.
