#!/usr/bin/env bash
# Local contract checks for Univent. Run from infrastructure/: ./scripts/verify-local-contract.sh
set -euo pipefail
cd "$(dirname "$0")/.."

echo "=== docker compose config (build contexts) ==="
docker compose config | head -n 80

echo ""
echo "=== Spring actuator mappings (first 2000 chars) ==="
curl -sfS "http://localhost:8080/actuator/mappings" | head -c 2000 || echo "(curl failed: is spring-boot on :8080?)"

echo ""
echo "=== GET /api/v1/news ==="
curl -sfS "http://localhost:8080/api/v1/news?page=0&size=5" | head -c 800 || echo "(curl failed)"

echo ""
echo "=== GET python-ai /ready ==="
curl -sfS "http://localhost:8000/ready" || echo "(curl failed)"

echo ""
echo "Done."
