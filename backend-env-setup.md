# Backend Environment Setup

Use `infrastructure/.env.example` as the source of truth for local Docker runs.

Runtime note:

- For local Python worker development, use Python 3.12.
  The deployed Docker image already uses Python 3.12, and some pinned native dependencies do not currently install cleanly on Python 3.14.

1. Copy `infrastructure/.env.example` to `infrastructure/.env`.
   Or generate one automatically with:
   `powershell -ExecutionPolicy Bypass -File scripts/bootstrap-backend-env.ps1`
2. Set `POSTGRES_PASSWORD` to a strong password.
3. Set `JWT_SECRET` to a 64-character hex string.
   Example generation:
   `python -c "import secrets; print(secrets.token_hex(32))"`
4. Set `GEMINI_API_KEY` from Google AI Studio.
5. Set `MINIO_ROOT_PASSWORD` and `GF_SECURITY_ADMIN_PASSWORD`.

Service-specific notes:

- Spring Boot uses:
  `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `ENCRYPTION_SECRET`, `KAFKA_BROKERS`, `MINIO_*`, `AI_WORKER_URL`
- Go uses:
  `POSTGRES_URL`, `REDIS_URL`, `KAFKA_BROKERS`, `JWT_SECRET`, `SPRING_BOOT_URL`, `PYTHON_AI_URL`
- Python uses:
  `POSTGRES_URL`, `KAFKA_BROKERS`, `GEMINI_API_KEY`, `QDRANT_URL` or `QDRANT_HOST/QDRANT_PORT`, optional `QDRANT_API_KEY`

Recommended local startup:

1. `cd infrastructure`
2. `docker compose --profile monitoring up --build`

Useful URLs after startup:

- API gateway: `http://localhost`
- Spring health: `http://localhost/actuator/health`
- Go health: `http://localhost/api/go/health`
- Python health: `http://localhost/api/python/health`
- Grafana: `http://localhost:3000`
- MinIO console: `http://localhost:9001`

Validation:

- Run `scripts/run-backend-validation.ps1`
- For authenticated e2e, set `JWT_TOKEN` and run:
  `python tests/e2e/test_polyglot_flow.py`
