# Promptline — Runtime Config Orchestration System (Spring Boot + AWS MCP Router)

> **Reviewer-friendly summary:** Promptline is a runtime configuration system that demonstrates a production-style config pipeline:  
> **config in Git → validated/orchestrated by a router service → published as canonical runtime artifacts to S3 → consumed by a backend with cache invalidation**.

This repository contains:
-  **Spring Boot Backend** (serves runtime config + persistence)
-  **MCP Router** (**AWS Lambda** behind **API Gateway**) (validates/coordinates config + publishes to S3 + PR orchestration)
-  **Contract Probes**
  - **Public Probe** (no secrets) → anyone can run
  - **Internal Probe** (full protected flow) → reviewers can request access by emailing me.

---

## Table of Contents

- [1. What Promptline Does](#1-what-promptline-does)
- [2. Runtime Config Model](#2-runtime-config-model)
- [3. Components](#3-components)
  - [3.1 Spring Boot Backend](#31-spring-boot-backend)
  - [3.2 MCP Router (AWS Lambda + API Gateway)](#32-mcp-router-aws-lambda--api-gateway)
  - [3.3 Git Config Branches](#33-git-config-branches)
  - [3.4 S3 Runtime Artifacts](#34-s3-runtime-artifacts)
- [4. Quickstart for Reviewers](#4-quickstart-for-reviewers-recommended)
- [5. Contract probes — Review Hosted Services](#5-contract-probes--review-hosted-services)
  - [5.1 Public Probe (No Secrets)](#51-public-probe-no-secrets)
  - [5.2 Internal Probe (Full Flow, Requires Access)](#52-internal-probe-full-flow-requires-access)
  - [5.3 How to Get Internal Access](#53-how-to-get-internal-access)
- [6. Run Locally on Docker](#6-run-locally-on-docker)
- [7. Successs Criteria](#7-success-criteria)

---

## 1. What Promptline Does

Promptline is a human in the loop, Devops agent that manages runtime configuration in a safe, testable, production-style way.

It ensures runtime config changes are:
- **tracked in Git**
- **validated before promotion**
- **published to S3 in canonical runtime format**
- **consumed by a backend service** without requiring a redeploy
- **refreshable via cache invalidation** when desired

This design is commonly used for:
- feature flags / experiments
- policy engines
- rollout controls
- distributed systems runtime tuning

---

## 2. Runtime Config Model

Promptline manages two configuration domains:

### UI Runtime Config
Represents runtime knobs for UI behavior.
Example field:
- `rateLimit.rpm`

### Policy Runtime Config
Represents runtime decision rules.
Example field:
- `rules.mode` (e.g., `strict` or `balanced`)

The backend reads canonical runtime JSON from S3:
- `runtime/ui.json`
- `runtime/policy.json`

---

## 3. Components

### 3.1 Spring Boot Backend

Backend responsibilities:
- hosts application endpoints
- persists entities in Postgres
- reads runtime artifacts from S3 using AWS SDK
- caches reads and supports invalidation

Backend endpoints:
- `GET /actuator/health`
- `GET /ui-config`
- `GET /policy`
- `POST /internal/config-updated`

**Important:** If you run locally without AWS credentials, `/ui-config` and `/policy` can return `500`.  
That’s expected unless you provide AWS credentials or use the hosted stack (check Option B in section 4).

---

### 3.2 MCP Router (AWS Lambda + API Gateway)

Router responsibilities:
- config orchestration control plane
- validates change sets
- publishes canonical runtime artifacts to S3
- optional PR detection/creation for config changes
- safe internal-token authentication for protected routes
- reads files from Git via `/git/get-file`

Router endpoints:
- Public:
  - `GET /healthz`
- Protected (require header `x-internal-token`):
  - `POST /config/publish-canonical`
  - `POST /config/publish-to-s3`
  - `POST /config/check-live`
  - `POST /config/check-open-pr`
  - `POST /config/ensure-pr`
  - `POST /git/get-file`

---

### 3.3 Git Config Branches

Config source-of-truth lives in config branches (typical):
- `config/live`

It contains:
- `config/ui.json`
- `config/policy.json`

The router fetches these to produce canonical runtime artifacts.

---

### 3.4 S3 Runtime Artifacts

Canonical runtime artifacts are written to stable S3 paths:
- `runtime/ui.json`
- `runtime/policy.json`

The backend reads these at runtime.

---


## 4. Quickstart for Reviewers 

This section paired with the subsequent ones, validate the real end-to-end runtime-config contract (routes, auth gates, payload validation, and expected responses) without the reviewer needing to understand the full codebase.

What to look for in the dumps: GET /healthz → 200, protected routes (like /config/*, /git/get-file) return 401 without token + 400 on bad payloads + 200 on valid requests with clear decisions (ex: NEEDS_PR).

How the system flows: Git (config/live) → MCP Router (API Gateway → Lambda) → S3 runtime artifacts → Spring Boot backend (/ui-config, /policy), with POST /internal/config-updated acting as cache invalidation so runtime changes apply without redeploys.

---

## 5. Contract probes — Review Hosted Services

Contract probes can help anyone validate the system’s real, deployed API behavior end-to-end (routes, auth gates, payloads, integrations). It's desinged to be a repeatable, automated contract check—without needing full source context.

The test probes are divided into two options for security, described below:

Option A:

This is the recommended reviewer path:
- no Docker required
- no AWS credentials required for the public probe
- highest-signal verification

For a more comprehensive contract probe, refer option B.

---

### 5.1 Public Probe (No Secrets)

**Public Probe** is designed so anyone can run it safely.

It tests:
- Router `GET /healthz` (public)
- Backend `GET /actuator/health` (public)

#### Run Public Probe

```
bash backend/mcp_contract_probe_public.sh \
  --mcp "<MCP_BASE_URL>" \
  --backend "<BACKEND_BASE_URL>"
```

Option B:
This probe validates the real deployed stack:
- Router: AWS API Gateway + Lambda
- Backend: hosted Spring Boot (EC2)
- Runtime artifacts: AWS S3

### 5.2 Internal Probe (Full Flow, Requires Access)

```
bash backend/mcp_contract_probe_internal.sh \
  --mcp-base-url "<MCP_BASE_URL>" \
  --backend "<BACKEND_BASE_URL>" \
  --token "<INTERNAL_TOKEN>" \
  --api-id "<API_ID>" \
  --region "<AWS_REGION>" \
  --fn "<LAMBDA_FUNCTION_NAME>"
```

### 6. Run Locally on Docker

This repo supports running the Spring Boot backend + Postgres locally using Docker. This is mainly for reviewers who want to validate boot + DB wiring + service structure.

Once Docker is up, these are expected to work:

1) Postgres boots + initializes

2) Spring Boot builds + starts on port 8080

3) LLM client wiring initializes (safe fallback to noop)

4) Backend health endpoint works

```
curl -sS http://localhost:8080/actuator/health | jq .
```

To test out the docker with the real env variables, shoot me  an email at **puranjaymishra0@gmail.com**!

By default, the Docker setup will boot correctly without secrets, but config-backed routes like:

* GET /ui-config

* GET /policy

will return 500 unless the backend can authenticate to AWS and read runtime artifacts from S3.

If you email me, I’ll share a minimal set of safe reviewer credentials (scoped to read-only config access) so you can run the full local backend + real hosted runtime config on your machine.

Step 1 — Create a .env file at repo root

Create a file named .env (same level as docker-compose.yml) and paste the values I provide:

```
# ---------- DATABASE ----------
POSTGRES_DB=promptline
POSTGRES_USER=promptline
POSTGRES_PASSWORD=promptline
POSTGRES_PORT=5432

# ---------- BACKEND ----------
BACKEND_PORT=8080

# ---------- AWS / RUNTIME CONFIG ----------
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=__REDACTED__
AWS_SECRET_ACCESS_KEY=__REDACTED__

# Where runtime artifacts exist (S3 paths)
S3_BUCKET=__REDACTED__
S3_RUNTIME_PREFIX=runtime
```

Docker Compose automatically loads .env from the repo root.
You don’t need to export these manually if .env exists.

Step 2 — Start Docker

From repo root:

```
docker compose down -v
docker compose up --build
```

This will:

1) Start Postgres

2) Build + start the Spring Boot backend container

3) Inject your .env values into the backend container

Step 3 — Verify Local Backend Health:

```
curl -sS http://localhost:8080/actuator/health | jq .
```

Expected:
```
{
  "status": "UP"
}
```

Step 4 — Verify Runtime Config Endpoints (Requires AWS env vars)

Once the AWS env vars are set correctly, these should return real JSON instead of 500:

```
curl -sS http://localhost:8080/ui-config | jq .
curl -sS http://localhost:8080/policy | jq .
```

Expected:

* valid JSON responses for UI runtime config and policy runtime config

* no AWS credential errors in the backend logs

* If /ui-config and /policy still fail

That almost always means one of these is missing or incorrect:

1) AWS_ACCESS_KEY_ID

2) AWS_SECRET_ACCESS_KEY

3) AWS_REGION

4) S3_BUCKET

You can confirm Docker is receiving the environment variables by inspecting the backend container:

```
docker compose exec backend env | grep AWS
```

If those values show up, your container is configured correctly.

## 7. Success Criteria

Below are ways one can validate Promptline:

1) Public probe returns 200 for /healthz and /actuator/health

2) Protected router routes return 401 when token is missing

3) Internal probe confirms Git → S3 publish → backend reads updated config