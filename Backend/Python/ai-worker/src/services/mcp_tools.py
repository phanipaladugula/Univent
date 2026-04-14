"""MCP Tools: Database-backed tools that Gemini calls during chat."""
import logging
from typing import List, Optional

import psycopg2
import psycopg2.extras

from src.config import settings

logger = logging.getLogger(__name__)


class MCPTools:
    """Model Context Protocol tools that query PostgreSQL for real-time data."""

    def __init__(self):
        self.conn_params = settings.POSTGRES_URL
        logger.info("✅ MCP Tools initialized")

    def _get_conn(self):
        return psycopg2.connect(self.conn_params, cursor_factory=psycopg2.extras.RealDictCursor)

    def search_reviews(self, query: str = None, college_id: str = None,
                       program_id: str = None, limit: int = 10) -> List[dict]:
        """Search published reviews with filters."""
        try:
            conn = self._get_conn()
            cur = conn.cursor()

            sql = """
                SELECT r.id, r.review_text, r.overall_rating, r.teaching_quality,
                       r.placement_support, r.infrastructure, r.hostel_life,
                       r.campus_life, r.value_for_money, r.pros, r.cons,
                       r.would_recommend, r.is_verified_review, r.graduation_year,
                       r.sentiment, r.upvotes, r.downvotes,
                       c.name as college_name, p.name as program_name
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
                params.append(f"%{query}%")

            sql += " ORDER BY r.upvotes DESC, r.created_at DESC LIMIT %s"
            params.append(limit)

            cur.execute(sql, params)
            rows = cur.fetchall()
            conn.close()

            results = []
            for row in rows:
                results.append({
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
                })
            return results

        except Exception as e:
            logger.error("search_reviews failed: %s", e)
            return []

    def get_college_stats(self, college_id: str, program_id: str = None) -> dict:
        """Get aggregated statistics for a college."""
        try:
            conn = self._get_conn()
            cur = conn.cursor()

            cur.execute("""
                SELECT college_name, slug, city, state, college_type,
                       total_reviews, avg_overall_rating, avg_teaching_quality,
                       avg_infrastructure, avg_hostel_life, avg_campus_life,
                       avg_value_for_money, total_programs,
                       avg_median_package, avg_placement_percentage
                FROM mv_college_stats
                WHERE college_id = %s::uuid
            """, (college_id,))

            row = cur.fetchone()
            if not row:
                conn.close()
                return {"error": "College not found"}

            result = dict(row)

            # If program specified, get program-specific data
            if program_id:
                cur.execute("""
                    SELECT cp.fees_total, cp.median_package, cp.highest_package,
                           cp.placement_percentage, cp.seats_intake, cp.entrance_exam,
                           p.name as program_name, p.degree, p.duration_years
                    FROM college_programs cp
                    JOIN programs p ON p.id = cp.program_id
                    WHERE cp.college_id = %s::uuid AND cp.program_id = %s::uuid
                """, (college_id, program_id))

                prog_row = cur.fetchone()
                if prog_row:
                    result["program"] = dict(prog_row)

            conn.close()

            # Convert Decimal to float for JSON serialization
            return self._serialize(result)

        except Exception as e:
            logger.error("get_college_stats failed: %s", e)
            return {"error": str(e)}

    def compare_colleges(self, college_ids: List[str], program_id: str) -> List[dict]:
        """Get comparison data for multiple colleges."""
        try:
            conn = self._get_conn()
            cur = conn.cursor()

            results = []
            for cid in college_ids[:4]:
                cur.execute("""
                    SELECT college_name, college_slug, city, state,
                           overall_rating, review_count, placement_percentage,
                           median_package, total_fees, teaching_quality,
                           infrastructure, hostel_life, campus_life,
                           value_for_money, would_recommend_percent,
                           roi_score, weighted_score, rank
                    FROM mv_college_rankings
                    WHERE college_id = %s::uuid AND program_id = %s::uuid
                """, (cid, program_id))

                row = cur.fetchone()
                if row:
                    results.append(self._serialize(dict(row)))

            conn.close()
            return results

        except Exception as e:
            logger.error("compare_colleges failed: %s", e)
            return []

    def get_roi(self, college_id: str, program_id: str) -> dict:
        """Calculate ROI for a college-program combination."""
        try:
            conn = self._get_conn()
            cur = conn.cursor()

            cur.execute("""
                SELECT cp.fees_total, cp.median_package, p.duration_years, p.name,
                       c.name as college_name
                FROM college_programs cp
                JOIN programs p ON p.id = cp.program_id
                JOIN colleges c ON c.id = cp.college_id
                WHERE cp.college_id = %s::uuid AND cp.program_id = %s::uuid
            """, (college_id, program_id))

            row = cur.fetchone()
            conn.close()

            if not row:
                return {"error": "College-program combination not found"}

            fees = float(row["fees_total"] or 0)
            median = float(row["median_package"] or 0)
            duration = row["duration_years"] or 4

            # ROI = (Median Package * 4 - Total Fees) / Duration
            four_year_earning = median * 4
            net_gain = max(0, four_year_earning - fees)
            roi_per_year = net_gain / duration if duration > 0 else 0

            # ROI label
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

        except Exception as e:
            logger.error("get_roi failed: %s", e)
            return {"error": str(e)}

    def _serialize(self, data: dict) -> dict:
        """Convert Decimal and other non-serializable types."""
        from decimal import Decimal
        result = {}
        for k, v in data.items():
            if isinstance(v, Decimal):
                result[k] = float(v)
            elif isinstance(v, dict):
                result[k] = self._serialize(v)
            else:
                result[k] = v
        return result
