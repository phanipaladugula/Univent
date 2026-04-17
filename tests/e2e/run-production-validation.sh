#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TEST_DIR="$ROOT_DIR/tests/e2e"

export API_URL="${API_URL:-http://localhost}"
export E2E_TIMEOUT_SECONDS="${E2E_TIMEOUT_SECONDS:-25}"
export E2E_READY_TIMEOUT_SECONDS="${E2E_READY_TIMEOUT_SECONDS:-180}"
export E2E_READY_POLL_SECONDS="${E2E_READY_POLL_SECONDS:-3}"
export E2E_MONITORING_ENABLED="${E2E_MONITORING_ENABLED:-true}"
export E2E_RUN_POLYGLOT="${E2E_RUN_POLYGLOT:-true}"
export E2E_RUN_RATE_LIMIT="${E2E_RUN_RATE_LIMIT:-false}"

cd "$TEST_DIR"
python3 -m pip install -r requirements.txt
python3 production_validation_suite.py
