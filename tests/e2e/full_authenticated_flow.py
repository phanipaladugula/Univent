import json
import os
import random
import re
import string
import subprocess
import sys
import time
from typing import Dict, List, Optional

import requests


BASE_URL = os.getenv("API_URL")
TIMEOUT = int(os.getenv("E2E_TIMEOUT_SECONDS", "20"))
OTP_OVERRIDE = os.getenv("OTP_CODE")
EMAIL_OVERRIDE = os.getenv("E2E_EMAIL")


def _rand_email() -> str:
    if EMAIL_OVERRIDE:
        return EMAIL_OVERRIDE
    suffix = "".join(random.choices(string.ascii_lowercase + string.digits, k=10))
    return f"e2e_{suffix}@example.com"


def _request(name: str, method: str, path: str, expected: List[int], **kwargs) -> Dict:
    url = f"{BASE_URL}{path}"
    try:
        response = requests.request(method, url, timeout=TIMEOUT, **kwargs)
        ok = response.status_code in expected
        body = response.text[:300]
        return {
            "name": name,
            "ok": ok,
            "status": response.status_code,
            "expected": expected,
            "url": url,
            "body": body,
            "json": response.json() if "application/json" in response.headers.get("content-type", "") else None,
        }
    except Exception as exc:  # noqa: BLE001
        return {
            "name": name,
            "ok": False,
            "status": None,
            "expected": expected,
            "url": url,
            "body": str(exc),
            "json": None,
        }


def _resolve_base_url() -> str:
    if BASE_URL:
        return BASE_URL.rstrip("/")

    candidates = [
        "http://localhost",  # nginx gateway (full stack)
        "http://localhost:8080",  # spring only — notifications/analytics need gateway + go-edge
    ]

    def _stack_ready(url: str) -> bool:
        try:
            if requests.get(f"{url}/actuator/health", timeout=5).status_code != 200:
                return False
            # Routed by nginx to Go; only present when hitting the gateway, not raw :8080.
            return requests.get(f"{url}/api/go/health", timeout=5).status_code == 200
        except Exception:  # noqa: BLE001
            return False

    for candidate in candidates:
        if _stack_ready(candidate):
            return candidate

    for candidate in candidates:
        try:
            response = requests.get(f"{candidate}/actuator/health", timeout=5)
            if response.status_code == 200:
                return candidate
        except Exception:  # noqa: BLE001
            continue
    return "http://localhost"


def _wait_for_api_ready(base_url: str) -> None:
    """Spring can take 30–60s after `docker compose up`; nginx may return 502 until Tomcat is up."""
    max_wait = int(os.getenv("E2E_READY_TIMEOUT_SECONDS", "120"))
    interval = float(os.getenv("E2E_READY_POLL_SECONDS", "2"))
    deadline = time.monotonic() + max_wait
    while time.monotonic() < deadline:
        try:
            response = requests.get(f"{base_url}/actuator/health", timeout=5)
            if response.status_code == 200:
                return
        except Exception:  # noqa: BLE001
            pass
        time.sleep(interval)


def _find_redis_container() -> Optional[str]:
    cmd = ["docker", "ps", "--format", "{{.Names}}"]
    out = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if out.returncode != 0:
        return None
    names = [n.strip() for n in out.stdout.splitlines() if n.strip()]
    for pattern in (r"^univentcheck-redis-1$", r"^infrastructure-redis-1$"):
        for name in names:
            if re.match(pattern, name):
                return name
    for name in names:
        if name.endswith("-redis-1"):
            return name
    return None


def _get_otp_from_redis(email: str) -> Optional[str]:
    container = _find_redis_container()
    if not container:
        return None
    key = f"otp:{email}"
    cmd = ["docker", "exec", container, "sh", "-lc", f"redis-cli -a \"$REDIS_PASSWORD\" GET '{key}'"]
    out = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if out.returncode != 0:
        return None
    otp = out.stdout.strip().splitlines()
    if not otp:
        return None
    candidate = otp[-1].strip()
    return candidate if re.fullmatch(r"\d{6}", candidate) else None


def _poll_otp(email: str, attempts: int = 10, delay_s: float = 1.0) -> Optional[str]:
    if OTP_OVERRIDE:
        return OTP_OVERRIDE
    for _ in range(attempts):
        otp = _get_otp_from_redis(email)
        if otp:
            return otp
        time.sleep(delay_s)
    return None


def main() -> int:
    results: List[Dict] = []
    email = _rand_email()
    global BASE_URL
    BASE_URL = _resolve_base_url()
    _wait_for_api_ready(BASE_URL)

    # 1) Register and get OTP
    register_payload = {"email": email}
    reg = _request("register", "POST", "/api/v1/auth/register", [200], json=register_payload)
    results.append(reg)
    if not reg["ok"]:
        print(json.dumps({"stage": "register_failed", "results": results}, indent=2))
        return 1

    otp = _poll_otp(email)
    if not otp:
        results.append(
            {
                "name": "otp_lookup",
                "ok": False,
                "status": None,
                "expected": [200],
                "url": "docker://redis",
                "body": "Could not resolve OTP from Redis; set OTP_CODE manually.",
                "json": None,
            }
        )
        print(json.dumps({"stage": "otp_failed", "results": results}, indent=2))
        return 1

    # 2) Verify OTP and obtain JWT
    verify_payload = {"email": email, "otp": otp}
    verify = _request("verify_otp", "POST", "/api/v1/auth/verify-otp", [200], json=verify_payload)
    results.append(verify)
    if not verify["ok"] or not verify["json"]:
        print(json.dumps({"stage": "verify_failed", "results": results}, indent=2))
        return 1

    access = verify["json"].get("accessToken")
    refresh = verify["json"].get("refreshToken")
    if not access or not refresh:
        results.append(
            {
                "name": "token_extraction",
                "ok": False,
                "status": None,
                "expected": [200],
                "url": f"{BASE_URL}/api/v1/auth/verify-otp",
                "body": "Auth response missing accessToken/refreshToken",
                "json": verify["json"],
            }
        )
        print(json.dumps({"stage": "token_failed", "results": results}, indent=2))
        return 1

    auth_headers = {"Authorization": f"Bearer {access}"}

    # 3) Refresh token flow
    refresh_req = _request(
        "refresh_token",
        "POST",
        "/api/v1/auth/refresh",
        [200],
        json={"refreshToken": refresh},
    )
    results.append(refresh_req)

    # 4) Protected endpoints with JWT
    results.append(_request("users_me", "GET", "/api/v1/users/me", [200], headers=auth_headers))
    results.append(_request("my_reviews", "GET", "/api/v1/users/me/reviews", [200], headers=auth_headers))
    results.append(_request("notifications", "GET", "/api/v1/notifications/", [200], headers=auth_headers))

    # 5) Role-gated endpoint should be denied for normal user
    results.append(
        _request(
            "admin_analytics_denied_for_user",
            "GET",
            "/api/v1/analytics/dashboard",
            [401, 403],
            headers=auth_headers,
        )
    )

    # 6) Review submit flow (if there is seed data)
    colleges = _request("list_colleges", "GET", "/api/v1/colleges", [200])
    programs = _request("list_programs", "GET", "/api/v1/programs", [200])
    results.extend([colleges, programs])

    college_id = None
    program_id = None
    if colleges["ok"] and colleges["json"] and colleges["json"].get("content"):
        college_id = colleges["json"]["content"][0].get("id")
    if programs["ok"] and isinstance(programs["json"], list) and programs["json"]:
        program_id = programs["json"][0].get("id")

    if college_id and program_id:
        review_text = (
            "Faculty quality and academics are strong, and placement support is consistent. "
            "Hostel facilities need better maintenance, but overall campus environment is positive."
        )
        payload = {
            "collegeId": college_id,
            "programId": program_id,
            "graduationYear": 2025,
            "isCurrentStudent": True,
            "overallRating": 4,
            "teachingQuality": 4,
            "placementSupport": 4,
            "infrastructure": 3,
            "hostelLife": 3,
            "campusLife": 4,
            "valueForMoney": 4,
            "pros": ["faculty", "academics"],
            "cons": ["hostel maintenance"],
            "reviewText": review_text,
            "wouldRecommend": True,
        }
        results.append(
            _request("submit_review_authenticated", "POST", "/api/v1/reviews", [202], headers=auth_headers, json=payload)
        )
    else:
        results.append(
            {
                "name": "submit_review_authenticated",
                "ok": True,
                "status": None,
                "expected": [202],
                "url": f"{BASE_URL}/api/v1/reviews",
                "body": "Skipped: no colleges/programs seed data available.",
                "json": None,
            }
        )

    failed = [r for r in results if not r["ok"]]
    print(json.dumps({"email": email, "total": len(results), "failed": len(failed), "results": results}, indent=2))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
