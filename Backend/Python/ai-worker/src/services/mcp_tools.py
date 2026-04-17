"""MCP tools backed by PostgreSQL queries."""
import logging
from contextlib import closing
from typing import List

import psycopg2
import psycopg2.extras

from src.config import settings

logger = logging.getLogger(__name__)


class MCPTools:
    def __init__(self):
        self.conn_params = settings.POSTGRES_URL
        logger.info("MCP tools initialized")

    def _get_conn(self):
        conn = psycopg2.connect(
            self.conn_params,
            cursor_factory=psycopg2.extras.RealDictCursor,
            connect_timeout=settings.POSTGRES_CONNECT_TIMEOUT,
            options=f"-c statement_timeout={settings.POSTGRES_STATEMENT_TIMEOUT_MS}",
        )
        conn.set_session(readonly=True, autocommit=True)
        return conn

    def ping(self) -> bool:
        try:
            with closing(self._get_conn()) as conn:
                with conn.cursor() as cur:
                    cur.execute("SELECT 1")
                    cur.fetchone()
            return True
        except Exception as exc:
            logger.error("MCP ping failed: %s", exc)
            return False

    def search_reviews(self, query: str = None, college_id: str = None, program_id: str = None, limit: int = 10) -> List[dict]:
        limit = max(1, min(limit, 50))
        try:
            with closing(self._get_conn()) as conn:
                with conn.cursor() as cur:
                    sql = """
                        SELECT r.id, r.review_text, r.overall_rating, r.teaching_quality,
                               r.placement_support, r.infrastructure, r.hostel_life,
                               r.campus_life, r.value_for_money, r.pros, r.cons,
                               r.would_recommend, r.is_verified_review, r.graduation_year,
                               r.sentiment, r.upvotes, r.downvotes,
                               c.name AS college_name, p.name AS program_name
                        FROM reviews r
                        JOIN colleges c ON c.id = r.college_id
                        JOIN programs p ON p.id = r.program_id
                        WHERE r.status = 'PUBLISHED'
                    """
                    params = []

                    if college_id:
                        sql += " AND r.college_id = %s::uuid"
                        params.append(college_id)
                    if program_id:
                        sql += " AND r.program_id = %s::uuid"
                        params.append(program_id)
                    if query:
                        sql += " AND r.review_text ILIKE %s"
                        params.append(f"%{query.strip()}%")

                    sql += " ORDER BY r.upvotes DESC, r.created_at DESC LIMIT %s"
                    params.append(limit)
                    cur.execute(sql, params)
                    rows = cur.fetchall()

            return [
                {
                    "review_id": str(row["id"]),
                    "excerpt": row["review_text"][:300] + "..." if len(row["review_text"]) > 300 else row["review_text"],
                    "overall_rating": row["overall_rating"],
                    "college_name": row["college_name"],
                    "program_name": row["program_name"],
                    "is_verified": row["is_verified_review"],
                    "graduation_year": row["graduation_year"],
                    "sentiment": row["sentiment"],
                    "would_recommend": row["would_recommend"],
                    "helpful_score": (row["upvotes"] or 0) - (row["downvotes"] or 0),
                }
                for row in rows
            ]
        except Exception as exc:
            logger.error("search_reviews failed: %s", exc)
            return []

    def get_college_stats(self, college_id: str, program_id: str = None) -> dict:
        try:
            with closing(self._get_conn()) as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        SELECT college_name, slug, city, state, college_type,
                               total_reviews, avg_overall_rating, avg_teaching_quality,
                               avg_infrastructure, avg_hostel_life, avg_campus_life,
                               avg_value_for_money, total_programs,
                               avg_median_package, avg_placement_percentage
                        FROM mv_college_stats
                        WHERE college_id = %s::uuid
                        """,
                        (college_id,),
                    )
                    row = cur.fetchone()
                    if not row:
                        return {"error": "College not found"}

                    result = dict(row)
                    if program_id:
                        cur.execute(
                            """
                            SELECT cp.fees_total, cp.median_package, cp.highest_package,
                                   cp.placement_percentage, cp.seats_intake, cp.entrance_exam,
                                   p.name AS program_name, p.degree, p.duration_years
                            FROM college_programs cp
                            JOIN programs p ON p.id = cp.program_id
                            WHERE cp.college_id = %s::uuid AND cp.program_id = %s::uuid
                            """,
                            (college_id, program_id),
                        )
                        prog_row = cur.fetchone()
                        if prog_row:
                            result["program"] = dict(prog_row)

            return self._serialize(result)
        except Exception as exc:
            logger.error("get_college_stats failed: %s", exc)
            return {"error": str(exc)}

    def compare_colleges(self, college_ids: List[str], program_id: str) -> List[dict]:
        try:
            results = []
            with closing(self._get_conn()) as conn:
                with conn.cursor() as cur:
                    for college_id in college_ids[:4]:
                        cur.execute(
                            """
                            SELECT college_name, college_slug, city, state,
                                   overall_rating, review_count, placement_percentage,
                                   median_package, total_fees, teaching_quality,
                                   infrastructure, hostel_life, campus_life,
                                   value_for_money, would_recommend_percent,
                                   roi_score, weighted_score, rank
                            FROM mv_college_rankings
                            WHERE college_id = %s::uuid AND program_id = %s::uuid
                            """,
                            (college_id, program_id),
                        )
                        row = cur.fetchone()
                        if row:
                            results.append(self._serialize(dict(row)))
            return results
        except Exception as exc:
            logger.error("compare_colleges failed: %s", exc)
            return []

    def get_roi(self, college_id: str, program_id: str) -> dict:
        try:
            with closing(self._get_conn()) as conn:
                with conn.cursor() as cur:
                    cur.execute(
                        """
                        SELECT cp.fees_total, cp.median_package, p.duration_years, p.name,
                               c.name AS college_name
                        FROM college_programs cp
                        JOIN programs p ON p.id = cp.program_id
                        JOIN colleges c ON c.id = cp.college_id
                        WHERE cp.college_id = %s::uuid AND cp.program_id = %s::uuid
                        """,
                        (college_id, program_id),
                    )
                    row = cur.fetchone()

            if not row:
                return {"error": "College-program combination not found"}

            fees = float(row["fees_total"] or 0)
            median = float(row["median_package"] or 0)
            duration = row["duration_years"] or 4
            four_year_earning = median * 4
            net_gain = max(0, four_year_earning - fees)
            roi_per_year = net_gain / duration if duration > 0 else 0

            if roi_per_year >= 2000000:
                label = "Excellent"
            elif roi_per_year >= 1000000:
                label = "Good"
            elif roi_per_year >= 500000:
                label = "Fair"
            else:
                label = "Poor"

            return {
                "college_name": row["college_name"],
                "program_name": row["name"],
                "total_fees": fees,
                "median_package": median,
                "duration_years": duration,
                "roi_per_year": roi_per_year,
                "roi_label": label,
                "roi_percentage": round((median / fees * 100), 1) if fees > 0 else 0,
            }
        except Exception as exc:
            logger.error("get_roi failed: %s", exc)
            return {"error": str(exc)}

    def get_recommendations(self, program_category: str = None, location: str = None, limit: int = 10) -> List[dict]:
        limit = max(1, min(limit, 25))
        try:
            with closing(self._get_conn()) as conn:
                with conn.cursor() as cur:
                    sql = """
                        SELECT DISTINCT ON (college_id)
                               college_id, college_name, program_name, city, state,
                               overall_rating, placement_percentage, median_package,
                               total_fees, weighted_score
                        FROM mv_college_rankings
                        WHERE review_count > 0
                    """
                    params = []

                    if program_category:
                        sql += " AND category = %s"
                        params.append(program_category)

                    if location:
                        sql += " AND (LOWER(state) LIKE %s OR LOWER(city) LIKE %s)"
                        like_pattern = f"%{location.lower()}%"
                        params.extend([like_pattern, like_pattern])

                    sql += " ORDER BY college_id, weighted_score DESC LIMIT %s"
                    params.append(limit)

                    cur.execute(sql, params)
                    rows = cur.fetchall()

            recommendations = []
            for row in rows:
                highlights = []
                if row.get("overall_rating") and float(row["overall_rating"]) >= 4:
                    highlights.append(f"High rating: {row['overall_rating']}/5")
                if row.get("placement_percentage") and float(row["placement_percentage"]) >= 80:
                    highlights.append(f"Strong placements: {row['placement_percentage']}%")
                if row.get("median_package"):
                    pkg_lpa = float(row["median_package"]) / 100000
                    highlights.append(f"Median package: Rs {pkg_lpa:.1f} LPA")

                recommendations.append(
                    {
                        "college_id": str(row["college_id"]),
                        "college_name": row["college_name"],
                        "program_name": row.get("program_name"),
                        "match_score": float(row.get("weighted_score", 0)),
                        "highlights": highlights,
                    }
                )

            return recommendations
        except Exception as exc:
            logger.error("get_recommendations failed: %s", exc)
            return []

    def _serialize(self, data: dict) -> dict:
        from decimal import Decimal

        result = {}
        for key, value in data.items():
            if isinstance(value, Decimal):
                result[key] = float(value)
            elif isinstance(value, dict):
                result[key] = self._serialize(value)
            else:
                result[key] = value
        return result
