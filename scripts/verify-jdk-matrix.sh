#!/usr/bin/env bash
set -euo pipefail

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repository_root=$(cd -- "${script_directory}/.." && pwd)
podman_command=${PODMAN_COMMAND:-podman}

if ! command -v "${podman_command}" >/dev/null 2>&1; then
    echo "Podman is required to run the local JDK matrix." >&2
    exit 1
fi

if (( $# == 0 )); then
    jdk_versions=(8 11 17 21)
else
    jdk_versions=("$@")
fi

mkdir -p "${repository_root}/.build-cache/maven/repository"

for jdk_version in "${jdk_versions[@]}"; do
    case "${jdk_version}" in
        8|11|17|21) ;;
        *)
            echo "Unsupported matrix JDK: ${jdk_version}" >&2
            exit 2
            ;;
    esac

    image="docker.io/library/eclipse-temurin:${jdk_version}-jdk"
    echo "==> Verifying Folio PDF on JDK ${jdk_version} (${image})"
    "${podman_command}" run --rm \
        --userns=keep-id \
        --volume "${repository_root}:/workspace:Z" \
        --workdir /workspace \
        --env MAVEN_USER_HOME=/workspace/.build-cache/maven \
        "${image}" \
        sh -c 'PATH=/workspace/scripts/container-bin:"${PATH}"; export PATH; exec ./mvnw "$@"' \
        folio-pdf-matrix \
        -B -ntp \
        -Dmaven.repo.local=/workspace/.build-cache/maven/repository \
        verify
done
