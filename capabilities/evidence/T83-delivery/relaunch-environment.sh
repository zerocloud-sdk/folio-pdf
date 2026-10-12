#!/usr/bin/env bash
# Select the already provisioned, ticket-owned execution inputs; no credentials.
set -euo pipefail
export JAVA_HOME=/workspace/folio-pdf/.build-cache/t83-jdk17
export MAVEN_USER_HOME=/workspace/folio-pdf/.build-cache/maven
export MAVEN_OPTS=-Dmaven.repo.local=/workspace/folio-pdf/.build-cache/maven/repository
export PYTHONPATH=/workspace/folio-pdf/.build-cache/t83-fonttools:/workspace/folio-pdf/.build-cache/foundation-host-python
export FOLIO_FOUNDATION_PYTHON_ROOT=/workspace/folio-pdf/.build-cache/foundation-python
export FOLIO_HARFBUZZ_HELPER=/workspace/folio-pdf/.build-cache/t81-provision/folio-harfbuzz/bin/folio-harfbuzz
export CONTAINERS_STORAGE_CONF=/workspace/folio-pdf/.build-cache/t83-storage.conf
exec "$@"
