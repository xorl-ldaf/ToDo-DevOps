#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/verify-published-image.sh <image-ref>

Example:
  ./scripts/verify-published-image.sh ghcr.io/xorl-ldaf/todo-devops:sha-0123456789abcdef

Optional environment variables:
  REPOSITORY         GitHub repository in owner/repo form
                     default: xorl-ldaf/ToDo-DevOps

  SIGNER_WORKFLOW    Signer workflow identity for GitHub attestations
                     default: xorl-ldaf/ToDo-DevOps/.github/workflows/ci.yaml

  CERT_IDENTITY_RE   Cosign certificate identity regexp
                     default: ^https://github\.com/xorl-ldaf/ToDo-DevOps/\.github/workflows/ci\.yaml@.*$

  OIDC_ISSUER        Expected OIDC issuer for keyless Cosign verification
                     default: https://token.actions.githubusercontent.com

  OUTPUT_DIR         Directory for JSON verification outputs
                     default: build/verification
EOF
}

if [[ $# -ne 1 ]]; then
  usage
  exit 2
fi

IMAGE_REF="$1"
IMAGE_URI="oci://${IMAGE_REF}"

REPOSITORY="${REPOSITORY:-xorl-ldaf/ToDo-DevOps}"
SIGNER_WORKFLOW="${SIGNER_WORKFLOW:-xorl-ldaf/ToDo-DevOps/.github/workflows/ci.yaml}"
CERT_IDENTITY_RE="${CERT_IDENTITY_RE:-^https://github\\.com/xorl-ldaf/ToDo-DevOps/\\.github/workflows/ci\\.yaml@.*$}"
OIDC_ISSUER="${OIDC_ISSUER:-https://token.actions.githubusercontent.com}"
OUTPUT_DIR="${OUTPUT_DIR:-build/verification}"

PROVENANCE_FILE_API="${OUTPUT_DIR}/provenance-github-api.json"
PROVENANCE_FILE_OCI="${OUTPUT_DIR}/provenance-oci.json"
SBOM_FILE_API="${OUTPUT_DIR}/sbom-github-api.json"
SBOM_FILE_OCI="${OUTPUT_DIR}/sbom-oci.json"

SBOM_PREDICATE_TYPE="https://cyclonedx.org/bom"

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: required command not found: ${cmd}" >&2
    exit 1
  fi
}

require_cmd docker
require_cmd gh
require_cmd cosign

mkdir -p "${OUTPUT_DIR}"

echo "[1/5] Verifying Cosign keyless signature for ${IMAGE_REF}"
cosign verify "${IMAGE_REF}" \
  --certificate-identity-regexp "${CERT_IDENTITY_RE}" \
  --certificate-oidc-issuer "${OIDC_ISSUER}" \
  > /dev/null

echo "[2/5] Verifying build provenance via GitHub attestation API"
gh attestation verify "${IMAGE_URI}" \
  --repo "${REPOSITORY}" \
  --signer-workflow "${SIGNER_WORKFLOW}" \
  --deny-self-hosted-runners \
  --format json > "${PROVENANCE_FILE_API}"

echo "[3/5] Verifying build provenance via OCI registry"
gh attestation verify "${IMAGE_URI}" \
  --repo "${REPOSITORY}" \
  --signer-workflow "${SIGNER_WORKFLOW}" \
  --deny-self-hosted-runners \
  --bundle-from-oci \
  --format json > "${PROVENANCE_FILE_OCI}"

echo "[4/5] Verifying SBOM attestation via GitHub attestation API"
gh attestation verify "${IMAGE_URI}" \
  --repo "${REPOSITORY}" \
  --signer-workflow "${SIGNER_WORKFLOW}" \
  --deny-self-hosted-runners \
  --predicate-type "${SBOM_PREDICATE_TYPE}" \
  --format json > "${SBOM_FILE_API}"

echo "[5/5] Verifying SBOM attestation via OCI registry"
gh attestation verify "${IMAGE_URI}" \
  --repo "${REPOSITORY}" \
  --signer-workflow "${SIGNER_WORKFLOW}" \
  --deny-self-hosted-runners \
  --predicate-type "${SBOM_PREDICATE_TYPE}" \
  --bundle-from-oci \
  --format json > "${SBOM_FILE_OCI}"

echo
echo "Verification succeeded."
echo "Artifacts written to:"
echo "  ${PROVENANCE_FILE_API}"
echo "  ${PROVENANCE_FILE_OCI}"
echo "  ${SBOM_FILE_API}"
echo "  ${SBOM_FILE_OCI}"