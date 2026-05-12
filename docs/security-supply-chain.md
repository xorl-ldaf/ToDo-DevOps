# Security and Supply-Chain Baseline

## Scope

This repository contains supply-chain controls around the published image and a basic workload runtime hardening baseline. It does not claim full production security coverage for the running system.

What is actually present in the checkout:

- image vulnerability scan with Trivy
- CycloneDX SBOM generation
- Cosign keyless image signing
- signature verification
- GitHub provenance attestation generation and verification
- SBOM attestation generation and verification
- local verification helper script
- non-root container runtime
- Kubernetes workload security context baseline

Primary CI implementation location:

- `.github/workflows/ci.yaml`

Local helper:

- `scripts/verify-published-image.sh`

## CI jobs and what they do

### `security-scan-image`

This job:

- logs in to GHCR
- installs Trivy
- generates SARIF output for the published image
- uploads SARIF to GitHub Security
- generates a CycloneDX SBOM artifact
- fails the pipeline on `CRITICAL` or `HIGH` vulnerabilities

### `sign-image`

Signs the published image digest with Cosign using GitHub Actions OIDC.

### `verify-signed-image`

Verifies that the signature matches the current repository workflow identity:

- issuer: `https://token.actions.githubusercontent.com`
- identity pattern tied to `.github/workflows/ci.yaml`

### `attest-image-provenance`

Generates provenance attestation for the published image digest and pushes the attestation to the registry.

### `verify-image-provenance`

Verifies provenance via GitHub attestation API and via OCI registry bundle.

### `attest-image-sbom`

Attaches the generated CycloneDX SBOM to the published image as an attestation.

### `verify-image-sbom-attestation`

Verifies the SBOM attestation via GitHub API and via OCI registry bundle.

## Runtime hardening implemented here

### Container image baseline

The runtime image now:

- runs as UID/GID `10001`
- sets `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp`
- keeps writable state limited to `/tmp`

### Kubernetes workload baseline

The manifests now include:

- pod-level `securityContext`
- `runAsNonRoot: true`
- fixed UID/GID
- `allowPrivilegeEscalation: false`
- `readOnlyRootFilesystem: true`
- dropped Linux capabilities
- `seccompProfile: RuntimeDefault`
- `emptyDir` mount for `/tmp`
- `PodDisruptionBudget`
- anti-affinity / topology spread hints

This is a meaningful workload hardening baseline, but not a full cluster security posture.

## Local verification of a published image

Example:

```bash
./scripts/verify-published-image.sh ghcr.io/xorl-ldaf/todo-devops:sha-<git-sha>
```

Required local tools:

- `docker`
- `gh`
- `cosign`

## What this baseline gives you

- evidence that the published image was built by this repository workflow
- evidence that the image was signed after CI checks
- a generated SBOM tied to the published image
- machine-readable verification artifacts that can be archived or reviewed
- a non-root/read-only container baseline consistent with the checked-in manifests

## What this baseline does not give you

- runtime admission control in Kubernetes
- secret rotation or secret-manager integration
- host hardening
- cluster hardening beyond the workload manifest baseline
- dependency-review policy for pull requests
- a claim that the application is secure for production by default

This is a meaningful baseline, not a blanket security certification.
