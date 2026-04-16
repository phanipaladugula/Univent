import asyncio
import json
import logging
import os

import requests
from websockets import connect

API_URL = os.getenv("API_URL", "http://localhost")
WS_URL = os.getenv("WS_URL", "ws://localhost/api/v1/ws")
JWT_TOKEN = os.getenv("JWT_TOKEN")
COLLEGE_ID = os.getenv("COLLEGE_ID")
PROGRAM_ID = os.getenv("PROGRAM_ID")

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger(__name__)


def require_token():
    if not JWT_TOKEN:
        raise RuntimeError("Set JWT_TOKEN to run the authenticated e2e flow.")
    return {"Authorization": f"Bearer {JWT_TOKEN}"}


async def run_e2e_flow():
    logger.info("Starting Univent polyglot e2e test")

    for endpoint in ("/actuator/health", "/api/go/health", "/api/python/health"):
        response = requests.get(f"{API_URL}{endpoint}", timeout=10)
        response.raise_for_status()

    headers = require_token()

    college_id = COLLEGE_ID
    program_id = PROGRAM_ID
    if not college_id or not program_id:
        colleges = requests.get(f"{API_URL}/api/v1/colleges", headers=headers, timeout=10).json()
        if not colleges.get("content"):
            raise RuntimeError("No colleges found. Seed the database first.")
        college = colleges["content"][0]
        college_id = college["id"]

        programs = requests.get(f"{API_URL}/api/v1/programs", headers=headers, timeout=10).json()
        if not programs:
            raise RuntimeError("No programs found. Seed the database first.")
        program_id = programs[0]["id"]

    process_completed = asyncio.Event()

    async def listen_to_ws():
        async with connect(f"{WS_URL}?token={JWT_TOKEN}") as websocket:
            while True:
                payload = json.loads(await websocket.recv())
                logger.info("Received WS event: %s", payload.get("type"))
                if payload.get("type") == "AI_PROCESSING_COMPLETE":
                    process_completed.set()
                    return

    ws_task = asyncio.create_task(listen_to_ws())
    await asyncio.sleep(1)

    review_payload = {
        "collegeId": college_id,
        "programId": program_id,
        "reviewText": "Strong academics and faculty support, but hostel food quality needs improvement.",
        "pros": ["faculty", "academics"],
        "cons": ["hostel food"],
        "overallRating": 4,
        "teachingQuality": 5,
        "placementSupport": 4,
        "infrastructure": 4,
        "hostelLife": 2,
        "campusLife": 4,
        "valueForMoney": 4,
        "wouldRecommend": True,
        "graduationYear": 2025,
        "isCurrentStudent": True,
    }
    review_response = requests.post(f"{API_URL}/api/v1/reviews", headers=headers, json=review_payload, timeout=20)
    review_response.raise_for_status()

    try:
        await asyncio.wait_for(process_completed.wait(), timeout=30)
    finally:
        ws_task.cancel()

    logger.info("Polyglot review flow completed successfully")
    return True


if __name__ == "__main__":
    asyncio.run(run_e2e_flow())
