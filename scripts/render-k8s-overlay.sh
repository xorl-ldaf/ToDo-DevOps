#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/render-k8s-overlay.sh <environment> <image-digest-ref> [output-file]

Example:
  ./scripts/render-k8s-overlay.sh prod ghcr.io/xorl-ldaf/todo-devops@sha256:abcdef... build/deploy/prod.yaml

Environment variables:
  KUSTOMIZE_MATCH_IMAGE   Base image name currently used in tracked manifests.
                          default: ghcr.io/xorl-ldaf/todo-devops
EOF
}

if [[ $# -lt 2 || $# -gt 3 ]]; then
  usage
  exit 2
fi

TARGET_ENVIRONMENT="$1"
IMAGE_DIGEST_REF="$2"
OUTPUT_FILE="${3:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OVERLAY_PATH="${REPO_ROOT}/deploy/k8s/overlays/${TARGET_ENVIRONMENT}"
MATCH_IMAGE="${KUSTOMIZE_MATCH_IMAGE:-ghcr.io/xorl-ldaf/todo-devops}"

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: required command not found: ${cmd}" >&2
    exit 1
  fi
}

if [[ ! -d "${OVERLAY_PATH}" ]]; then
  echo "ERROR: overlay not found: ${OVERLAY_PATH}" >&2
  exit 1
fi

if [[ "${IMAGE_DIGEST_REF}" != *@sha256:* ]]; then
  echo "ERROR: image reference must be an immutable digest reference: ${IMAGE_DIGEST_REF}" >&2
  exit 1
fi

require_cmd kubectl

IMAGE_NAME="${IMAGE_DIGEST_REF%@*}"
IMAGE_DIGEST="${IMAGE_DIGEST_REF#*@}"

WORK_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

mkdir -p "${WORK_DIR}/promotion"
cp -R "${REPO_ROOT}/deploy/k8s" "${WORK_DIR}/k8s"

cat > "${WORK_DIR}/promotion/kustomization.yaml" <<EOF
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
  - ../k8s/overlays/${TARGET_ENVIRONMENT}

images:
  - name: ${MATCH_IMAGE}
    newName: ${IMAGE_NAME}
    digest: ${IMAGE_DIGEST}
EOF

if [[ -n "${OUTPUT_FILE}" ]]; then
  mkdir -p "$(dirname "${OUTPUT_FILE}")"
  kubectl kustomize "${WORK_DIR}/promotion" > "${OUTPUT_FILE}"
else
  kubectl kustomize "${WORK_DIR}/promotion"
fi
