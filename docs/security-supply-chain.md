# Security and Supply-Chain Baseline

## Scope

This repository contains supply-chain and image-publication controls around the published container image. It does not claim full production security coverage for the running system.

What is actually present in the checkout:

- image vulnerability scan with Trivy
- CycloneDX SBOM generation
- Cosign keyless image signing
- signature verification
- GitHub provenance attestation generation and verification
- SBOM attestation generation and verification
- local verification helper script

Primary implementation location:

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

Artifacts produced:

- `trivy-results.sarif`
- `trivy-image-sbom.cdx.json`

### `sign-image`

This job signs the published image digest with Cosign using GitHub Actions OIDC.

### `verify-signed-image`

This job verifies that the signature matches the current repository workflow identity:

- issuer: `https://token.actions.githubusercontent.com`
- identity pattern tied to `.github/workflows/ci.yaml`

### `attest-image-provenance`

This job generates provenance attestation for the published image digest and pushes the attestation to the registry.

### `verify-image-provenance`

This job verifies provenance in two ways:

- via GitHub attestation API
- via OCI registry bundle

Verification artifacts are uploaded for both checks.

### `attest-image-sbom`

This job attaches the generated CycloneDX SBOM to the published image as an attestation.

### `verify-image-sbom-attestation`

This job verifies the SBOM attestation both via GitHub API and via OCI registry bundle.

## Local verification of a published image

The repository includes a helper for operators who want to verify a published image outside GitHub Actions.

Example:

```bash
./scripts/verify-published-image.sh ghcr.io/xorl-ldaf/todo-devops:sha-<git-sha>
```

Required local tools:

- `docker`
- `gh`
- `cosign`

Default verification outputs:

- `build/verification/provenance-github-api.json`
- `build/verification/provenance-oci.json`
- `build/verification/sbom-github-api.json`
- `build/verification/sbom-oci.json`

Supported environment overrides:

- `REPOSITORY`
- `SIGNER_WORKFLOW`
- `CERT_IDENTITY_RE`
- `OIDC_ISSUER`
- `OUTPUT_DIR`

## What this baseline gives you

- evidence that the published image was built by this repository workflow
- evidence that the image was signed after CI checks
- a generated SBOM tied to the published image
- machine-readable verification artifacts that can be archived or reviewed

## What this baseline does not give you

- runtime admission control in Kubernetes
- secret rotation or secret-manager integration
- host hardening
- cluster hardening
- dependency-review policy for pull requests
- a claim that the application is secure for production by default

This is a meaningful supply-chain baseline, not a blanket security certification.
