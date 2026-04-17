import json
import sys
from typing import List, Tuple

import requests


BASE_URL = "http://localhost"
PROM_URL = "http://localhost:9091"
GRAFANA_URL = "http://localhost:3000"
LOKI_URL = "http://localhost:3100"
TIMEOUT = 15


def check(name: str, method: str, url: str, expected: Tuple[int, ...], **kwargs):
    try:
        response = requests.request(method, url, timeout=TIMEOUT, **kwargs)
        status = response.status_code
        ok = status in expected
        return {
            "name": name,
            "url": url,
            "status": status,
            "ok": ok,
            "expected": expected,
            "body": response.text[:240],
        }
    except Exception as exc:  # noqa: BLE001
        return {
            "name": name,
            "url": url,
            "status": None,
            "ok": False,
            "expected": expected,
            "body": f"request failed: {exc}",
        }


def run_checks() -> List[dict]:
    results = []

    # Core service health and infra ingress
    results.append(check("nginx->spring health", "GET", f"{BASE_URL}/actuator/health", (200,)))
    results.append(check("nginx->go health", "GET", f"{BASE_URL}/api/go/health", (200, 503)))
    results.append(check("nginx->python health", "GET", f"{BASE_URL}/api/python/health", (200,)))
    results.append(check("spring metrics", "GET", f"{BASE_URL}/actuator/prometheus", (200,)))

    # Public API groups (should be reachable without auth)
    public_gets = [
        ("/api/v1/colleges", (200,)),
        ("/api/v1/programs", (200,)),
        ("/api/v1/reviews", (200, 400)),
        ("/api/v1/news", (200,)),
        ("/api/v1/rankings/top", (200,)),
    ]
    for path, expected in public_gets:
        results.append(check(f"public {path}", "GET", f"{BASE_URL}{path}", expected))

    # Protected endpoints (auth gate should reject anonymously)
    protected = [
        ("POST /api/v1/reviews", "POST", "/api/v1/reviews", (401, 403), {"json": {}}),
        ("GET /api/v1/users/me", "GET", "/api/v1/users/me", (401, 403), {}),
        ("GET /api/v1/notifications/", "GET", "/api/v1/notifications/", (401, 403), {}),
        ("GET /api/v1/analytics/dashboard", "GET", "/api/v1/analytics/dashboard", (401, 403), {}),
        ("GET /api/v1/admin/audit/logs", "GET", "/api/v1/admin/audit/logs", (401, 403), {}),
    ]
    for name, method, path, expected, kwargs in protected:
        results.append(check(name, method, f"{BASE_URL}{path}", expected, **kwargs))

    # Monitoring stack
    results.append(check("prometheus healthy", "GET", f"{PROM_URL}/-/healthy", (200,)))
    results.append(check("prometheus targets", "GET", f"{PROM_URL}/api/v1/targets", (200,)))
    results.append(check("grafana health", "GET", f"{GRAFANA_URL}/api/health", (200,)))
    results.append(check("loki ready", "GET", f"{LOKI_URL}/ready", (200,)))

    return results


def main():
    results = run_checks()
    failed = [r for r in results if not r["ok"]]
    print(json.dumps({"total": len(results), "failed": len(failed), "results": results}, indent=2))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
