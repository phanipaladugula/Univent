# Spring API vs Flutter mobile client

This repository’s interactive **frontend** for end users is the Flutter app under [`mobile/univent_mobile`](../../../mobile/univent_mobile). There is **no separate web SPA** in this repo; coverage below compares **Spring Boot controllers** to calls from [`lib/core/network/univent_api.dart`](../../../mobile/univent_mobile/lib/core/network/univent_api.dart) and [`lib/app/app_state.dart`](../../../mobile/univent_mobile/lib/app/app_state.dart).

Use **Swagger UI** to exercise every endpoint:

| Entry point | URL |
| --- | --- |
| Spring Boot directly | `http://localhost:8080/swagger-ui.html` |
| Via Docker nginx | `http://localhost/swagger-ui.html` |

OpenAPI JSON: `http://localhost:8080/v3/api-docs` (or through nginx the same path).

---

## Legend

| Column | Meaning |
| --- | --- |
| **Flutter** | Used from `UniventApiClient` / `AppStateController` (user-facing flow). |
| **Client only** | Method exists on `UniventApiClient` but is **not** called from `app_state` (dead code or reserved). |
| **Not in mobile** | No reference in the Flutter tree; typical for **admin**, **CRUD**, or **future** features. |

---

## Controllers and endpoints

### AuthController — `/api/v1/auth`

| Method | Path | Flutter |
| --- | --- | --- |
| POST | `/register` | Yes (`requestOtp`) |
| POST | `/verify-otp` | Yes (`verifyOtp`) |
| POST | `/refresh` | Yes (`refresh`) |

### AiController — `/api/v1/ai`

| Method | Path | Flutter |
| --- | --- | --- |
| POST | `/chat` | Yes |
| POST | `/summarize` | Yes |
| POST | `/suggest` | Yes |
| GET | `/stats` | **Client only** (`getAiStats` not used in `app_state`) |

### CollegeController — `/api/v1/colleges`

| Method | Path | Flutter |
| --- | --- | --- |
| GET | `/` | Yes (`getColleges` default) |
| GET | `/search` | Yes (query search) |
| GET | `/state/{state}` | Yes (optional branch) |
| GET | `/type/{collegeType}` | Yes (optional branch) |
| GET | `/{id}` | Yes (`getCollegeById`) |
| GET | `/slug/{slug}` | **Not in mobile** |
| POST | `/` | **Not in mobile** (admin/data) |
| PUT | `/{id}` | **Not in mobile** |
| DELETE | `/{id}` | **Not in mobile** |

### ProgramController — `/api/v1/programs`

| Method | Path | Flutter |
| --- | --- | --- |
| GET | `/` | Yes |
| GET | `/category/{category}` | Yes |
| GET | `/{id}` | **Not in mobile** |
| GET | `/slug/{slug}` | **Not in mobile** |
| POST | `/` | **Not in mobile** |
| PUT | `/{id}` | **Not in mobile** |
| DELETE | `/{id}` | **Not in mobile** |

### ReviewController — `/api/v1/reviews`

| Method | Path | Flutter |
| --- | --- | --- |
| POST | `/` | Yes (`submitReview`) |
| GET | `/` | Yes (`getReviews`) |
| GET | `/{reviewId}` | **Not in mobile** |
| POST | `/{reviewId}/vote` | **Not in mobile** |
| POST | `/{reviewId}/comments` | **Not in mobile** |
| GET | `/{reviewId}/comments` | **Not in mobile** |
| POST | `/{reviewId}/flag` | **Not in mobile** |
| DELETE | `/{reviewId}` | **Not in mobile** |
| POST | `/comments/{commentId}/flag` | **Not in mobile** |

### UserController — `/api/v1/users`

| Method | Path | Flutter |
| --- | --- | --- |
| GET | `/me` | Yes (`getProfile`) |
| GET | `/me/reviews` | **Not in mobile** |
| GET | `/me/saved-comparisons` | **Not in mobile** (app uses `/api/v1/compare/saved` instead) |

### VerificationController — `/api/v1/verification`

| Method | Path | Flutter |
| --- | --- | --- |
| POST | `/upload` | Yes |
| GET | `/status` | **Not in mobile** (profile may still show status from `/users/me`) |
| DELETE | `/id-card` | **Not in mobile** |

### AdminVerificationController — `/api/v1/admin/verification`

| Method | Path | Flutter |
| --- | --- | --- |
| GET | `/pending` | **Not in mobile** (admin) |
| GET | `/{userId}/id-card` | **Not in mobile** |
| POST | `/{userId}/approve` | **Not in mobile** |
| POST | `/{userId}/reject` | **Not in mobile** |

### ComparisonController — `/api/v1/compare`

| Method | Path | Flutter |
| --- | --- | --- |
| POST | `/colleges` | Yes |
| POST | `/save` | Yes |
| GET | `/saved` | Yes |
| DELETE | `/saved/{comparisonId}` | **Not in mobile** |

### NewsController — `/api/v1/news`

| Method | Path | Flutter |
| --- | --- | --- |
| GET | `/` | Yes (`getNewsFeed`) |
| POST | `/posts` | **Not in mobile** |
| POST | `/articles/{articleId}/upvote` | **Not in mobile** |
| POST | `/posts/{postId}/upvote` | **Not in mobile** |
| POST | `/refresh` | **Not in mobile** (admin RSS refresh) |

### RankingController — `/api/v1/rankings`

| Method | Path | Flutter |
| --- | --- | --- |
| GET | `/program/{programId}` | **Not in mobile** |
| GET | `/search` | **Not in mobile** |
| GET | `/leaderboard/{category}` | **Not in mobile** |
| GET | `/college/{collegeId}/stats` | **Not in mobile** |
| GET | `/top` | **Not in mobile** |

### CollegeProgramController — `/api/v1/college-programs`

| Method | Path | Flutter |
| --- | --- | --- |
| GET | `/college/{collegeId}` | **Not in mobile** |
| GET | `/program/{programId}` | **Not in mobile** |
| POST | `/` | **Not in mobile** |
| DELETE | `/college/{collegeId}/program/{programId}` | **Not in mobile** |

### AdminReviewController — `/api/v1/admin/reviews`

| Method | Path | Flutter |
| --- | --- | --- |
| GET | `/pending`, `/published`, `/flagged` | **Not in mobile** (admin) |
| PUT | `/{reviewId}/approve`, `/{reviewId}/reject` | **Not in mobile** |
| POST | `/{reviewId}/reprocess` | **Not in mobile** |

### AdminNewsController — `/api/v1/admin/news`

| Method | Path | Flutter |
| --- | --- | --- |
| GET | `/posts/pending` | **Not in mobile** |
| PUT | `/posts/{postId}/approve`, `/reject` | **Not in mobile** |

### AdminFlagController — `/api/v1/admin/flags`

| Method | Path | Flutter |
| --- | --- | --- |
| GET | `/pending`, `/stats` | **Not in mobile** |
| PUT | `/{flagId}/resolve` | **Not in mobile** |

### TestController — `/api/v1/test`

| Method | Path | Flutter |
| --- | --- | --- |
| GET | `/token`, `/public` | **Not in mobile** (dev/test helpers) |

---

## Summary

- **Admin** and **content-management** APIs are intentionally **not** wired in the Flutter shell; use **Swagger** with an **ADMIN** JWT to test them.
- **Student** gaps worth a product pass include: rankings, review detail/votes/comments/flags, program by id/slug, user “my reviews”, verification status/delete, delete saved comparison, news upvotes/posts, and wiring **`getAiStats`** if you want diagnostics in the app.

Nginx also routes some traffic to **Go edge** (e.g. `/api/v1/notifications`, `/api/v1/admin/audit`); those are **not** Spring controllers—see the Go service OpenAPI or code if you add Swagger there later.
