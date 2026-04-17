import asyncio
import json
import os
import random
import re
import string
import subprocess
import sys
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
from urllib.parse import urlparse

import requests
import websockets


@dataclass
class StepResult:
    name: str
    ok: bool
    status: Optional[int] = None
    expected: List[int] = field(default_factory=list)
    url: str = ""
    body: str = ""
    details: Dict[str, Any] = field(default_factory=dict)
    required: bool = True

    def as_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "ok": self.ok,
            "status": self.status,
            "expected": self.expected,
            "url": self.url,
            "body": self.body,
            "details": self.details,
            "required": self.required,
        }


TIMEOUT = int(os.getenv("E2E_TIMEOUT_SECONDS", "25"))
READY_TIMEOUT = int(os.getenv("E2E_READY_TIMEOUT_SECONDS", "180"))
READY_POLL = float(os.getenv("E2E_READY_POLL_SECONDS", "3"))
MONITORING_ENABLED = os.getenv("E2E_MONITORING_ENABLED", "true").lower() == "true"
RUN_POLYGLOT = os.getenv("E2E_RUN_POLYGLOT", "true").lower() == "true"
RUN_RATE_LIMIT = os.getenv("E2E_RUN_RATE_LIMIT", "false").lower() == "true"
OTP_OVERRIDE = os.getenv("OTP_CODE")
EMAIL_OVERRIDE = os.getenv("E2E_EMAIL")
BASE_URL = os.getenv("API_URL")


class SuiteContext:
    def __init__(self) -> None:
        self.base_url = self.resolve_base_url()
        self.results: List[StepResult] = []
        self.email = self.rand_email()
        self.access_token: Optional[str] = None
        self.refresh_token: Optional[str] = None
        self.college_id: Optional[str] = os.getenv("COLLEGE_ID")
        self.program_id: Optional[str] = os.getenv("PROGRAM_ID")
        self.review_id: Optional[str] = None

    @staticmethod
    def rand_email() -> str:
        if EMAIL_OVERRIDE:
            return EMAIL_OVERRIDE
        suffix = "".join(random.choices(string.ascii_lowercase + string.digits, k=10))
        return f"e2e_{suffix}@example.com"

    @staticmethod
    def resolve_base_url() -> str:
        if BASE_URL:
            return BASE_URL.rstrip("/")

        candidates = [
            "http://localhost",
            "http://localhost:8080",
        ]
        for candidate in candidates:
            try:
                response = requests.get(f"{candidate}/actuator/health", timeout=5)
                if response.status_code == 200:
                    return candidate
            except Exception:
                continue
        return "http://localhost"

    def record(self, result: StepResult) -> StepResult:
        self.results.append(result)
        return result


ctx = SuiteContext()


def request_step(name: str, method: str, path: str, expected: List[int], *, required: bool = True, base_url: Optional[str] = None, **kwargs: Any) -> StepResult:
    url = f"{(base_url or ctx.base_url).rstrip('/')}{path}"
    try:
        response = requests.request(method, url, timeout=TIMEOUT, **kwargs)
        body = response.text[:500]
        details = {}
        if "application/json" in response.headers.get("content-type", ""):
            try:
                details["json"] = response.json()
            except Exception:
                details["json_parse_error"] = True
        return ctx.record(
            StepResult(
                name=name,
                ok=response.status_code in expected,
                status=response.status_code,
                expected=expected,
                url=url,
                body=body,
                details=details,
                required=required,
            )
        )
    except Exception as exc:
        return ctx.record(
            StepResult(
                name=name,
                ok=False,
                status=None,
                expected=expected,
                url=url,
                body=str(exc),
                required=required,
            )
        )


def auth_headers() -> Dict[str, str]:
    if not ctx.access_token:
        return {}
    return {"Authorization": f"Bearer {ctx.access_token}"}


def wait_until_ready() -> None:
    deadline = time.monotonic() + READY_TIMEOUT
    while time.monotonic() < deadline:
        try:
            spring = requests.get(f"{ctx.base_url}/actuator/health", timeout=5)
            if spring.status_code == 200:
                try:
                    py = requests.get(f"{ctx.base_url}/api/python/health", timeout=5)
                    go = requests.get(f"{ctx.base_url}/api/go/health", timeout=5)
                    if py.status_code == 200 and go.status_code == 200:
                        return
                except Exception:
                    return
        except Exception:
            pass
        time.sleep(READY_POLL)


def find_redis_container() -> Optional[str]:
    out = subprocess.run(["docker", "ps", "--format", "{{.Names}}"], capture_output=True, text=True, check=False)
    if out.returncode != 0:
        return None
    names = [name.strip() for name in out.stdout.splitlines() if name.strip()]
    patterns = [r"^univentcheck-redis-1$", r"^infrastructure-redis-1$", r"-redis-1$"]
    for pattern in patterns:
        for name in names:
            if re.search(pattern, name):
                return name
    return None


def fetch_otp(email: str) -> Optional[str]:
    if OTP_OVERRIDE:
        return OTP_OVERRIDE
    container = find_redis_container()
    if not container:
        return None
    key = f"otp:{email}"
    command = ["docker", "exec", container, "sh", "-lc", f"redis-cli -a \"$REDIS_PASSWORD\" GET '{key}'"]
    out = subprocess.run(command, capture_output=True, text=True, check=False)
    if out.returncode != 0:
        return None
    candidate = out.stdout.strip().splitlines()
    if not candidate:
        return None
    otp = candidate[-1].strip()
    return otp if re.fullmatch(r"\d{6}", otp) else None


def poll_otp(email: str, attempts: int = 15, delay_s: float = 1.5) -> Optional[str]:
    for _ in range(attempts):
        otp = fetch_otp(email)
        if otp:
            return otp
        time.sleep(delay_s)
    return None


def assert_seed_data() -> None:
    if ctx.college_id and ctx.program_id:
        return

    colleges = request_step("list_colleges", "GET", "/api/v1/colleges", [200])
    programs = request_step("list_programs", "GET", "/api/v1/programs", [200])

    college_json = colleges.details.get("json") or {}
    if not ctx.college_id and isinstance(college_json, dict) and college_json.get("content"):
        ctx.college_id = college_json["content"][0].get("id")

    programs_json = programs.details.get("json")
    if not ctx.program_id and isinstance(programs_json, list) and programs_json:
        ctx.program_id = programs_json[0].get("id")


def run_health_checks() -> None:
    wait_until_ready()
    request_step("spring_health", "GET", "/actuator/health", [200])
    request_step("go_health", "GET", "/api/go/health", [200])
    request_step("python_health", "GET", "/api/python/health", [200])
    request_step("spring_actuator_prometheus", "GET", "/actuator/prometheus", [200, 401, 403], required=False)


def run_auth_flow() -> None:
    register = request_step("register", "POST", "/api/v1/auth/register", [200], json={"email": ctx.email})
    if not register.ok:
        return

    otp = poll_otp(ctx.email)
    ctx.record(
        StepResult(
            name="otp_lookup",
            ok=bool(otp),
            status=200 if otp else None,
            expected=[200],
            url="docker://redis",
            body="OTP resolved from Redis" if otp else "Could not resolve OTP from Redis. Set OTP_CODE manually.",
            required=True,
        )
    )
    if not otp:
        return

    verify = request_step(
        "verify_otp",
        "POST",
        "/api/v1/auth/verify-otp",
        [200],
        json={"email": ctx.email, "otp": otp},
    )
    verify_json = verify.details.get("json") or {}
    ctx.access_token = verify_json.get("accessToken")
    ctx.refresh_token = verify_json.get("refreshToken")

    ctx.record(
        StepResult(
            name="token_extraction",
            ok=bool(ctx.access_token and ctx.refresh_token),
            status=200 if ctx.access_token and ctx.refresh_token else None,
            expected=[200],
            url=f"{ctx.base_url}/api/v1/auth/verify-otp",
            body="Access and refresh tokens extracted" if ctx.access_token and ctx.refresh_token else "Auth response missing tokens",
            required=True,
        )
    )

    if ctx.refresh_token:
        request_step(
            "refresh_token",
            "POST",
            "/api/v1/auth/refresh",
            [200],
            json={"refreshToken": ctx.refresh_token},
        )


def run_public_and_protected_api_checks() -> None:
    request_step("users_me_requires_auth", "GET", "/api/v1/users/me", [401, 403])

    if ctx.access_token:
        request_step("users_me", "GET", "/api/v1/users/me", [200], headers=auth_headers())
        request_step("users_my_reviews", "GET", "/api/v1/users/me/reviews", [200], headers=auth_headers())
        request_step("notifications", "GET", "/api/v1/notifications/", [200], headers=auth_headers())
        request_step("analytics_denied_for_normal_user", "GET", "/api/v1/analytics/dashboard", [401, 403], headers=auth_headers())

    request_step("public_colleges", "GET", "/api/v1/colleges", [200])
    request_step("public_programs", "GET", "/api/v1/programs", [200])


def run_ai_checks() -> None:
    if not ctx.access_token:
        return

    assert_seed_data()
    if not ctx.college_id:
        ctx.record(StepResult(name="ai_seed_data", ok=False, body="No college seed data found", required=False))
        return

    request_step("ai_stats", "GET", "/api/v1/ai/stats", [200], headers=auth_headers())
    request_step(
        "ai_suggest",
        "POST",
        "/api/v1/ai/suggest",
        [200],
        headers=auth_headers(),
        json={"programCategory": "Engineering", "location": "hyderabad"},
    )
    request_step(
        "ai_summarize",
        "POST",
        "/api/v1/ai/summarize",
        [200, 404],
        headers=auth_headers(),
        json={"collegeId": ctx.college_id, "programId": ctx.program_id},
        required=False,
    )
    request_step(
        "ai_chat",
        "POST",
        "/api/v1/ai/chat",
        [200],
        headers=auth_headers(),
        json={
            "query": "Summarize the main strengths and concerns students mention for this college.",
            "collegeId": ctx.college_id,
            "programId": ctx.program_id,
        },
    )


def build_review_payload() -> Optional[Dict[str, Any]]:
    assert_seed_data()
    if not ctx.college_id or not ctx.program_id:
        return None
    return {
        "collegeId": ctx.college_id,
        "programId": ctx.program_id,
        "graduationYear": 2025,
        "isCurrentStudent": True,
        "overallRating": 4,
        "teachingQuality": 4,
        "placementSupport": 4,
        "infrastructure": 3,
        "hostelLife": 3,
        "campusLife": 4,
        "valueForMoney": 4,
        "pros": ["faculty", "academics", "placements"],
        "cons": ["hostel maintenance", "wifi stability"],
        "reviewText": (
            "Faculty quality is strong, the curriculum is relevant, and placement support is consistent. "
            "Campus life is active and collaborative. Hostel maintenance and network stability still need improvement, "
            "but overall the academic experience has been positive and useful for career preparation."
        ),
        "wouldRecommend": True,
    }


async def polyglot_flow() -> None:
    if not ctx.access_token:
        return

    payload = build_review_payload()
    if not payload:
        ctx.record(StepResult(name="polyglot_seed_data", ok=False, body="No seed college/program available", required=False))
        return

    parsed = urlparse(ctx.base_url)
    ws_scheme = "wss" if parsed.scheme == "https" else "ws"
    ws_url = f"{ws_scheme}://{parsed.netloc}/ws?token={ctx.access_token}"

    try:
        async with websockets.connect(ws_url, open_timeout=15, ping_interval=20, ping_timeout=20) as websocket:
            ctx.record(StepResult(name="websocket_connect", ok=True, status=101, expected=[101], url=ws_url, body="Connected"))
            submit = request_step("submit_review", "POST", "/api/v1/reviews", [202], headers=auth_headers(), json=payload)
            submit_json = submit.details.get("json") or {}
            ctx.review_id = submit_json.get("reviewId") or submit_json.get("id")

            event_received = False
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                try:
                    raw = await asyncio.wait_for(websocket.recv(), timeout=5)
                    data = json.loads(raw)
                    event_type = data.get("type") or data.get("eventType")
                    if event_type in {"AI_PROCESSING_COMPLETE", "notification", "NOTIFICATION_CREATED"}:
                        event_received = True
                        ctx.record(
                            StepResult(
                                name="polyglot_event_received",
                                ok=True,
                                status=200,
                                expected=[200],
                                url=ws_url,
                                body=str(data)[:500],
                                details={"event": data},
                                required=False,
                            )
                        )
                        break
                except asyncio.TimeoutError:
                    continue

            if not event_received:
                ctx.record(
                    StepResult(
                        name="polyglot_event_received",
                        ok=False,
                        status=None,
                        expected=[200],
                        url=ws_url,
                        body="No websocket event received within timeout",
                        required=False,
                    )
                )
    except Exception as exc:
        ctx.record(
            StepResult(
                name="websocket_connect",
                ok=False,
                status=None,
                expected=[101],
                url=ws_url,
                body=str(exc),
                required=False,
            )
        )


def run_monitoring_checks() -> None:
    if not MONITORING_ENABLED:
        return

    request_step("prometheus_ui", "GET", ":9091/-/healthy", [200], required=False, base_url="http://localhost")
    request_step("grafana_health", "GET", ":3000/api/health", [200], required=False, base_url="http://localhost")
    request_step("loki_ready", "GET", ":3100/ready", [200], required=False, base_url="http://localhost")


def run_rate_limit_check() -> None:
    if not RUN_RATE_LIMIT or not ctx.access_token:
        return
    headers = auth_headers()
    last = None
    for attempt in range(1, 80):
        last = request_step(
            f"rate_limit_attempt_{attempt}",
            "GET",
            "/api/v1/users/me",
            [200, 429],
            headers=headers,
            required=False,
        )
        if last.status == 429:
            break
    ctx.record(
        StepResult(
            name="rate_limit_triggered",
            ok=bool(last and last.status == 429),
            status=last.status if last else None,
            expected=[429],
            url=f"{ctx.base_url}/api/v1/users/me",
            body="Rate limit triggered" if last and last.status == 429 else "Rate limit did not trigger in configured attempts",
            required=False,
        )
    )


def main() -> int:
    run_health_checks()
    run_auth_flow()
    run_public_and_protected_api_checks()
    run_ai_checks()
    if RUN_POLYGLOT:
        asyncio.run(polyglot_flow())
    run_monitoring_checks()
    run_rate_limit_check()

    required_failures = [result for result in ctx.results if result.required and not result.ok]
    optional_failures = [result for result in ctx.results if not result.required and not result.ok]

    report = {
        "base_url": ctx.base_url,
        "email": ctx.email,
        "total_steps": len(ctx.results),
        "required_failures": len(required_failures),
        "optional_failures": len(optional_failures),
        "results": [result.as_dict() for result in ctx.results],
    }
    print(json.dumps(report, indent=2))
    return 1 if required_failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
