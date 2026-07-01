#!/usr/bin/env bash
set -euo pipefail

APP_VERSION="${1:-0.1.0}"
IMAGE="quay.io/arencloud/balance:${APP_VERSION}"
LOCAL_MANIFEST="localhost/balance-multi:${APP_VERSION}"
MODE="${2:-single}"

if [[ "$(podman info --format '{{.Host.Security.Rootless}}')" != "true" ]]; then
  echo "Rootless Podman is required. Run without sudo." >&2
  exit 1
fi

./mvnw package

case "${MODE}" in
  single)
    podman build -f src/main/container/Containerfile -t "${IMAGE}" .
    ;;
  multiarch)
    podman manifest rm "${LOCAL_MANIFEST}" >/dev/null 2>&1 || true
    podman build \
      --platform linux/amd64,linux/arm64 \
      --manifest "${LOCAL_MANIFEST}" \
      -f src/main/container/Containerfile .
    podman manifest push --all "${LOCAL_MANIFEST}" "docker://${IMAGE}"
    ;;
  *)
    echo "Usage: $0 [version] [single|multiarch]" >&2
    exit 2
    ;;
esac

echo "${IMAGE}"
