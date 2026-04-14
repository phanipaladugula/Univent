# Univent Free-Tier Deployment Guide

This guide explains how to deploy the polyglot Univent architecture for **nearly zero cost** using modern free tiers and cloud platforms. 

Because we have a Microservices architecture with Kafka and a Vector DB (Qdrant), a simple serverless platform won't work out of the box. We need a combination of managed databases and Docker hosting.

## Architecture Overview for Free Tier

| Service | Technology | Free Tier Provider |
| :--- | :--- | :--- |
| **PostgreSQL** | Database | **Neon** or **Supabase** (Free 500MB DB) |
| **Redis** | Caching/WS | **Upstash** (Free 10K commands/day) |
| **Kafka** | Event Bus | **Upstash Kafka** (Free 10K msg/day) |
| **Qdrant** | Vector DB | **Qdrant Cloud** (Free 1GB Cluster) |
| **Spring Boot Core** | Application | **Render** or **Railway** (Free/Starter Web Service) |
| **Go Edge Service** | API Gateway | **Koyeb** or **Render** (Free Web Service) |
| **Python AI Worker**| Worker | **Render** (Background Worker) |

---

## Step-by-Step Deployment

### 1. Provision Free Managed Data Stores (The "Hard" Part)

We do not want to run Postgres, Redis, Kafka, or Qdrant on a tiny 512MB VPS container because they will crash due to out-of-memory errors. The trick is to use managed DB free tiers:

1. **Postgres via Neon.tech**
   - Create an account: `neon.tech`
   - Create a free project and Postgres 16 database.
   - Copy the connection string. Set this as `DB_URL` in your `.env`.
   
2. **Redis via Upstash**
   - Create an account: `console.upstash.com`
   - Create a Redis database. Copy the "URL" connection string.
   - Set this as `REDIS_URL` in Go config, and parse it for Spring Boot.

3. **Kafka via Upstash**
   - In the Upstash console, create a Kafka Serverless Cluster.
   - Go to "Topics" and create these three topics: `review.submitted`, `review.processed`, `notification.outbound`, `audit.events`.
   - Under "Details", copy the Endpoint (Brokers), Username, and Password.
   - Set up your Apps to use SASL Authentication for Kafka. (Since Upstash uses SASL, you will need to add the `security.protocol=SASL_SSL` to your app configs if deploying to production. For local Docker, plaintext works fine).

4. **Vector DB via Qdrant Cloud**
   - Create an account: `cloud.qdrant.io`
   - Provision the free 1GB cluster.
   - Create an API key. Update the Python AI Worker env vars to point to this host and pass the API Key.

### 2. Prepare Application Configuration

Create a central `.env` on your deployment platform combining all details:

```env
# Database
POSTGRES_URL=postgres://[user]:[password]@[neon_hostname]/univent?sslmode=require
DB_URL=jdbc:postgresql://[neon_hostname]/univent?sslmode=require
DB_USERNAME=[user]
DB_PASSWORD=[password]

# Redis
REDIS_URL=rediss://default:[password]@[upstash_hostname]:[port]

# Kafka
KAFKA_BROKERS=[upstash_kafka_endpoint]

# Security
JWT_SECRET=super_secure_random_hex_string
ENCRYPTION_SECRET=super_secure_encryption_key

# AI
GEMINI_API_KEY=your_google_gemini_api_key

# Minio (If no Minio, use AWS S3 Free Tier or Cloudflare R2 Free Tier)
# ... Cloudflare R2 gives 10GB free matching S3 API ...
```

### 3. Deploy the Containers (Render / Koyeb)

You have three Dockerfiles ready in your repository:
1. `Backend/Springboot/Univent-Backend/Dockerfile`
2. `Backend/Go/edge-service/Dockerfile`
3. `Backend/Python/ai-worker/Dockerfile`

**Using Render (Easiest)**
1. Connect you GitHub repo to Render.com.
2. Click **New -> Web Service**.
3. Choose the repo. Specify the Docker build context as `Backend/Springboot/Univent-Backend`.
4. Paste the `.env` variables from Step 2 into the Environment section.
5. Repeat for **Go Edge** (Web Service).
6. Repeat for **Python AI Worker** (Background Worker -> no web port needed).

**Important:** Point the Python and Go applications to the exact HTTP URL of the Spring Boot application on Render using the env vars:
- `SPRING_BOOT_URL=https://your-spring-app.onrender.com`

---

## Alternative: Single $5/month VPS Deployment

If managing multiple serverless platforms is annoying, you can throw the **entire architecture** onto a single Virtual Private Server using the provided `docker-compose.yml`.

### The "Almost Free" Option: Oracle Cloud ARM (Always Free)
Oracle provides a 4 OCPU ARM compute instance with 24GB RAM entirely for free. This is powerful enough to run your entire stack.

1. Create an Oracle Cloud account and provision the **Always Free Ampere A1 Compute Instance**.
2. Install Docker and Docker Compose on the Ubuntu instance.
3. Clone your repository.
4. Copy the `.env.example` to `.env` and fill in the passwords.
5. Run the stack:
   ```bash
   cd infrastructure
   docker compose up -d
   ```
6. Open port 80 on your VPS Firewall to expose the NGINX API Gateway. 

*Note: You may need to tweak Docker image architectures in `docker-compose.yml` to ensure they pull ARM-compatible images (e.g., Qdrant has an unofficial ARM image or you run it natively). Go, Python, and Java build seamlessly on ARM.*
