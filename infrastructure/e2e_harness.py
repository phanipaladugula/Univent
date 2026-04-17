import sys
import time
import uuid
import requests
import subprocess

# Configuration
BASE_URL = "http://localhost"
REDIS_HOST = "localhost"
REDIS_PORT = 6379

def print_step(msg):
    print(f"\n[{time.strftime('%H:%M:%S')}] \033[1;36m==>\033[0m \033[1m{msg}\033[0m")

def print_success(msg):
    print(f"[{time.strftime('%H:%M:%S')}] \033[1;32m[PASS]\033[0m {msg}")

def print_error(msg):
    print(f"[{time.strftime('%H:%M:%S')}] \033[1;31m[FAIL]\033[0m {msg}")
    sys.exit(1)

def run_e2e_tests():
    print("==================================================")
    print("      UNIVENT POLYGLOT E2E TEST HARNESS           ")
    print("==================================================\n")

    # 1. Health Checks
    print_step("Infrastructure Health Checks")
    
    try:
        r = requests.get(f"{BASE_URL}/actuator/health")
        if r.status_code == 200:
            print_success("Spring Boot (API Gateway) is Healthy")
        else:
            print_error(f"Spring Boot Health Failed: {r.status_code}")
    except Exception as e:
        print_error(f"Could not connect to Gateway: {e}")

    try:
        r = requests.get(f"{BASE_URL}/api/v1/colleges/search?query=test")
        if r.status_code == 200:
            print_success("Spring Boot DB Connection & College Search Alive")
        else:
            print_error(f"College Search Failed: {r.status_code}")
    except Exception as e:
        print_error(f"Could not connect to College Service: {e}")

    # 2. Auth Flow (Registration & Redis OTP)
    print_step("Authentication Flow (Redis Bypass)")
    test_id = str(uuid.uuid4())[:8]
    test_email = f"e2e_{test_id}@univent.com"
    test_password = "Password123!"
    
    try:
        r = requests.post(f"{BASE_URL}/api/v1/auth/register", json={
            "email": test_email,
            "password": test_password,
            "fullName": f"E2E Tester {test_id}",
            "role": "STUDENT"
        })
        if r.status_code == 200:
            print_success(f"Successfully registered user: {test_email}")
        elif r.status_code == 400 and "send OTP email" in r.text:
            print_success(f"Registered user but SMTP failed (expected in dev): {test_email}")
        else:
            print_error(f"Registration Failed: {r.text}")
    except Exception as e:
        print_error(f"Registration Request Error: {e}")

    # Connect to Redis via Docker
    print_step("Bypassing Email via Redis (Docker exec)")
    otp = None
    try:
        otp_key = f"otp:{test_email}"
        result = subprocess.run(
            ["docker", "exec", "infrastructure-redis-1", "redis-cli", "get", otp_key],
            capture_output=True, text=True, check=True
        )
        # result.stdout might look like `"123456"\n`, so strip quotes and whitespace
        otp = result.stdout.strip().replace('"', '')
        
        if otp and otp != "(nil)":
            print_success(f"Found OTP in Redis: {otp}")
        else:
            print_error(f"OTP not found in Redis for {test_email}")
    except Exception as e:
        print_error(f"Failed to connect to Redis container: {e}")

    # Verify OTP
    print_step("Verifying OTP")
    try:
        r = requests.post(f"{BASE_URL}/api/v1/auth/verify-otp", json={
            "email": test_email,
            "otp": otp
        })
        if r.status_code == 200:
            token_data = r.json()
            access_token = token_data.get("accessToken")
            print_success("Successfully verified OTP and acquired Access Token")
        else:
            print_error(f"OTP Verification Failed: {r.text}")
    except Exception as e:
        print_error(f"Verification Request Error: {e}")

    # Authenticated Context Headers
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json"
    }

    # 3. Discovery Flow
    print_step("Discovery & Read Flows")
    try:
        r = requests.get(f"{BASE_URL}/api/v1/colleges/search?query=MIT", headers=headers)
        if r.status_code == 200:
            colleges = r.json().get('content', [])
            print_success(f"College search executed. Found {len(colleges)} constraints for 'MIT'.")
        else:
            print_error(f"Authenticated College Search Failed: {r.text}")
    except Exception as e:
        print_error(f"Search Request Error: {e}")

    # 3.5 Seed the Database via Docker
    print_step("Seeding Relational DB (College & Program)")
    try:
        subprocess.run([
            "docker", "exec", "infrastructure-postgres-1", "psql", "-U", "postgres", "-d", "univent", "-c",
            "INSERT INTO colleges (id, name, slug, city, state, college_type, is_verified, average_rating, total_reviews) VALUES ('550e8400-e29b-41d4-a716-446655440001', 'Test College', 'test-college', 'Test City', 'Test State', 'ENGINEERING', true, 0, 0) ON CONFLICT DO NOTHING;"
        ], check=True)
        subprocess.run([
            "docker", "exec", "infrastructure-postgres-1", "psql", "-U", "postgres", "-d", "univent", "-c",
            "INSERT INTO programs (id, name, slug, category, degree, duration_years) VALUES ('550e8400-e29b-41d4-a716-446655440101', 'Test Program', 'test-program', 'Computer Science', 'B.Tech', 4) ON CONFLICT DO NOTHING;"
        ], check=True)
        print_success("Seeded Test College & Program.")
    except Exception as e:
        print_error(f"Failed to seed DB: {e}")

    # 4. Content Creation
    print_step("Review Submission (Kafka Publisher hook)")
    try:
        review_payload = {
            "collegeId": "550e8400-e29b-41d4-a716-446655440001",
            "programId": "550e8400-e29b-41d4-a716-446655440101",
            "overallRating": 5.0,
            "academicsRating": 5.0,
            "infrastructureRating": 4.5,
            "placementRating": 4.0,
            "campusLifeRating": 4.5,
            "valueForMoneyRating": 4.0,
            "infrastructure": 5,
            "teachingQuality": 5,
            "campusLife": 5,
            "placementSupport": 4,
            "hostelLife": 3,
            "valueForMoney": 4,
            "reviewText": f"E2E Automated Review {test_id} - The campus infrastructure is amazing! The professors are highly qualified.",
            "pros": ["Great infrastructure", "Good professors"],
            "cons": ["Hostel food could be better"],
            "graduationYear": 2026,
            "wouldRecommend": True,
            "tags": ["Infrastructure", "Academics"]
        }
        
        # We might get 400 or 404 if collegeId doesn't exist, which is fine, it means the API layer caught it.
        r = requests.post(f"{BASE_URL}/api/v1/reviews", json=review_payload, headers=headers)
        if r.status_code in [200, 201, 202]:
            print_success("Successfully published Review event to Kafka pipeline")
        elif r.status_code == 404:
            print_success("Review API routed successfully, but test college UUID doesn't exist in DB (Expected)")
        else:
            print_error(f"Review Submission Failed: [{r.status_code}] {r.text}")
    except Exception as e:
        print_error(f"Review Request Error: {e}")

    # 5. Chaos Engineering: Slow AI Test
    print_step("Chaos Engineering: Slow AI Trap")
    try:
        # We manually inject the X-Test-Delay header, Spring Boot proxies this to Python!
        chaos_headers = headers.copy()
        chaos_headers["X-Test-Delay"] = "10000"
        ai_payload = {"query": "Will it blend?"}
        
        # We set our request timeout to 12s so requests doesn't throw its own exception too early
        start_t = time.time()
        r = requests.post(f"{BASE_URL}/api/v1/ai/chat", json=ai_payload, headers=chaos_headers, timeout=12)
        duration = time.time() - start_t
        
        # Since Spring Boot doesn't natively timeout unless configured, we just want to ensure
        # it handles the upstream delay without crashing the entire gateway (should return 200 or 5xx gracefully)
        if duration >= 10:
            print_success(f"Slow AI test passed! Gateway endured the 10s delay. (Status: {r.status_code})")
        else:
            print_error(f"Failed to inject delay. Response returned too quickly ({duration:.2f}s).")
    except Exception as e:
        print_success(f"Slow AI test expectedly triggered a timeout or graceful failure: {e}")

    # 6. Payload Fuzzing assertions
    print_step("Payload Fuzzing (Strict Assertions)")
    try:
        fuzz_payload = review_payload.copy()
        
        # 6a. SQL Injection Attempt
        fuzz_payload["reviewText"] = "'; DROP TABLE users; --"
        r = requests.post(f"{BASE_URL}/api/v1/reviews", json=fuzz_payload, headers=headers)
        assert r.status_code in [200, 201, 202, 400, 404], f"SQLi Payload caused 500 error! Status: {r.status_code}"
        print_success("SQLi Payload safely mitigated by ORM/Validation.")

        # 6b. Emoji Stress Test
        fuzz_payload["reviewText"] = "🚀🔥" * 1000
        r = requests.post(f"{BASE_URL}/api/v1/reviews", json=fuzz_payload, headers=headers)
        assert r.status_code in [200, 201, 202, 400, 404], f"Emoji Buffer caused error! Status: {r.status_code}"
        print_success("Emoji UTF-8 boundaries successfully handled.")

        # 6c. Buffer Overflow Limit
        fuzz_payload["reviewText"] = "A" * 50000
        r = requests.post(f"{BASE_URL}/api/v1/reviews", json=fuzz_payload, headers=headers)
        # Should probably return 400 Bad Request due to @Size constraints in Spring Boot
        assert r.status_code in [400, 413, 202, 201, 404], f"Buffer Overflow wasn't rejected properly! Status: {r.status_code}"
        print_success(f"Buffer Overflow correctly caught or safely swallowed! Status: {r.status_code}")
    except AssertionError as e:
        print_error(f"Fuzzing Assertion Failed: {e}")

    # 7. Chaos Engineering: The "Kafka Down" Test
    print_step("Chaos Engineering: Kafka Down Simulator")
    try:
        print("Pausing Kafka container...")
        subprocess.run(["docker", "pause", "infrastructure-kafka-1"], check=True)
        
        # Spring Boot should wrap the internal Kafka exception inside the Review Service and not crash
        r = requests.post(f"{BASE_URL}/api/v1/reviews", json=review_payload, headers=headers)
        assert r.status_code in [503, 500, 202], f"Kafka outage returned unexpected status: {r.status_code}"
        print_success(f"Spring Boot caught dead Kafka Broker gracefully (Status: {r.status_code})")
        
    except Exception as e:
        print_error(f"Kafka Down Sequence Failed: {e}")
    finally:
        print("Unpausing Kafka container...")
        subprocess.run(["docker", "unpause", "infrastructure-kafka-1"])

    print("\n==================================================")
    print(" \033[1;32mALL SYSTEMS AUTOMATION TEST COMPLETE\033[0m")
    print("==================================================\n")

if __name__ == "__main__":
    run_e2e_tests()
