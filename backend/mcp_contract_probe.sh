#!/usr/bin/env bash
set -euo pipefail

############################################
# INTERNAL MCP + BACKEND CONTRACT PROBE
# - CLI flags + env var support
# - Captures headers + bodies to ./out
# - Optional AWS-only features:
#     - api gateway endpoint discovery by API_ID
#     - lambda direct invoke
#     - log tail
############################################

OUT_DIR="${OUT_DIR:-./out}"
TS="$(date -u +"%Y%m%dT%H%M%SZ")"
mkdir -p "$OUT_DIR"
LOG_FILE="$OUT_DIR/internal_contract_${TS}.txt"

# Defaults (can override via env or flags)
API_ID="${API_ID:-702s1q2eid}"
AWS_REGION="${AWS_REGION:-us-east-1}"
FN="${FN:-promptline-mcp-router}"

# Router auth header expects x-internal-token
INTERNAL_TOKEN="${INTERNAL_TOKEN:-${MCP_INTERNAL_API_KEY:-}}"

# Backend base URL (local docker or hosted EC2)
BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:8080}"

# If you already know the API endpoint, you can skip API Gateway lookup
MCP_BASE_URL_OVERRIDE="${MCP_BASE_URL:-}"

# Repo info (optional; only used for certain /git/get-file payload examples)
REPO_OWNER="${REPO_OWNER:-${GITHUB_OWNER:-}}"
REPO_NAME="${REPO_NAME:-${GITHUB_REPO:-}}"

# Defaults for reading a file (used in /git/get-file examples)
GIT_REF_DEFAULT="${GIT_REF_DEFAULT:-config/live}"
GIT_PATH_DEFAULT="${GIT_PATH_DEFAULT:-config/ui.json}"

# Runtime paths used in demo diff (only if your repo has these paths)
UI_RUNTIME_PATH="${UI_RUNTIME_PATH:-runtime/ui.json}"
POLICY_RUNTIME_PATH="${POLICY_RUNTIME_PATH:-runtime/policy.json}"

# Demo changes (random by default, override via flags/env for deterministic runs)
RPM="${RPM:-$((100 + RANDOM % 900))}"   # 100-999
MODE="${MODE:-$( ((RANDOM % 2)) && echo strict || echo balanced )}"

# Feature toggles
SKIP_AWS_SNAPSHOT="${SKIP_AWS_SNAPSHOT:-no}"
SKIP_LAMBDA_INVOKE="${SKIP_LAMBDA_INVOKE:-no}"
SKIP_LOG_TAIL="${SKIP_LOG_TAIL:-no}"

usage() {
  cat <<EOF
Usage:
  bash backend/mcp_contract_probe_internal.sh [options]

Options:
  --mcp-base-url <url>     Skip API Gateway lookup; use this URL directly
  --api-id <id>            API Gateway API ID (default: $API_ID)
  --region <region>        AWS region (default: $AWS_REGION)
  --fn <lambda_name>       Lambda name (default: $FN)
  --token <token>          Internal token (or set INTERNAL_TOKEN / MCP_INTERNAL_API_KEY)
  --backend <url>          Backend base URL (default: $BACKEND_BASE_URL)

  --repo-owner <owner>     Optional GitHub owner for /git/get-file explicit payload
  --repo-name <name>       Optional GitHub repo  for /git/get-file explicit payload
  --git-ref <ref>          Default ref for /git/get-file (default: $GIT_REF_DEFAULT)
  --git-path <path>        Default path for /git/get-file (default: $GIT_PATH_DEFAULT)

  --rpm <int>              Demo change ui.rateLimit.rpm
  --mode <strict|balanced> Demo change policy.rules.mode

  --skip-aws-snapshot       Skip API routes/integrations/lambda config dump
  --skip-lambda-invoke      Skip lambda direct invoke tests
  --skip-log-tail           Skip aws logs tail

Examples:
  # Full power (needs AWS creds + internal token):
  bash backend/mcp_contract_probe_internal.sh --token "..." --backend "http://EC2:8080"

  # If you already know MCP base URL (still needs internal token):
  bash backend/mcp_contract_probe_internal.sh --mcp-base-url "https://...execute-api...amazonaws.com" --token "..." --backend "http://EC2:8080"
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mcp-base-url) MCP_BASE_URL_OVERRIDE="${2:-}"; shift 2;;
    --api-id) API_ID="${2:-}"; shift 2;;
    --region) AWS_REGION="${2:-}"; shift 2;;
    --fn) FN="${2:-}"; shift 2;;
    --token) INTERNAL_TOKEN="${2:-}"; shift 2;;
    --backend) BACKEND_BASE_URL="${2:-}"; shift 2;;

    --repo-owner) REPO_OWNER="${2:-}"; shift 2;;
    --repo-name) REPO_NAME="${2:-}"; shift 2;;
    --git-ref) GIT_REF_DEFAULT="${2:-}"; shift 2;;
    --git-path) GIT_PATH_DEFAULT="${2:-}"; shift 2;;

    --rpm) RPM="${2:-}"; shift 2;;
    --mode) MODE="${2:-}"; shift 2;;

    --skip-aws-snapshot) SKIP_AWS_SNAPSHOT="yes"; shift 1;;
    --skip-lambda-invoke) SKIP_LAMBDA_INVOKE="yes"; shift 1;;
    --skip-log-tail) SKIP_LOG_TAIL="yes"; shift 1;;

    -h|--help) usage; exit 0;;
    *) echo "Unknown arg: $1"; usage; exit 2;;
  esac
done

exec > >(tee -a "$LOG_FILE") 2>&1

hr() { echo "------------------------------------------------------------"; }

soft_run() {
  set +e
  "$@"
  local rc=$?
  set -e
  echo "exit=$rc"
  return 0
}

pp_json() {
  if command -v jq >/dev/null 2>&1; then jq . 2>/dev/null || cat; else cat; fi
}

kv() { printf "%-26s %s\n" "$1" "$2"; }

jget() {
  local json="$1"
  local expr="$2"
  if command -v jq >/dev/null 2>&1; then
    echo "$json" | jq -r "$expr" 2>/dev/null || true
  else
    true
  fi
}

need_aws() {
  command -v aws >/dev/null 2>&1
}

############################################
# Resolve MCP BASE_URL
############################################
BASE_URL=""
if [[ -n "$MCP_BASE_URL_OVERRIDE" ]]; then
  BASE_URL="$MCP_BASE_URL_OVERRIDE"
else
  if ! need_aws; then
    echo "ERROR: aws cli not found and --mcp-base-url not provided"
    exit 1
  fi
  BASE_URL="$(aws apigatewayv2 get-api --api-id "$API_ID" --region "$AWS_REGION" --query ApiEndpoint --output text)"
fi

############################################
# Basic validation
############################################
if [[ -z "$INTERNAL_TOKEN" ]]; then
  echo "ERROR: missing internal token (set --token or INTERNAL_TOKEN or MCP_INTERNAL_API_KEY)"
  exit 1
fi

echo "=== INTERNAL MCP + BACKEND CONTRACT PROBE ==="
kv "timestamp_utc" "$TS"
kv "mcp_base_url" "$BASE_URL"
kv "backend_base_url" "$BACKEND_BASE_URL"
kv "api_id" "$API_ID"
kv "region" "$AWS_REGION"
kv "lambda_function" "$FN"
kv "token_len" "$(printf "%s" "$INTERNAL_TOKEN" | wc -c | tr -d ' ')"
kv "repo_owner" "${REPO_OWNER:-<unset>}"
kv "repo_name" "${REPO_NAME:-<unset>}"
kv "git_ref_default" "$GIT_REF_DEFAULT"
kv "git_path_default" "$GIT_PATH_DEFAULT"
kv "demo_rpm" "$RPM"
kv "demo_mode" "$MODE"
kv "log_file" "$LOG_FILE"
echo

############################################
# Curl helpers
############################################
call_api() {
  local name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local auth_mode="${5:-none}" # none|bad|good

  hr
  echo "== $name =="
  echo "REQUEST: $method $path"
  echo "AUTH_MODE: $auth_mode"
  if [[ -n "$body" ]]; then
    echo "REQUEST_BODY:"
    echo "$body" | pp_json
  else
    echo "REQUEST_BODY: <none>"
  fi
  echo

  local auth_header=()
  if [[ "$auth_mode" == "good" ]]; then
    auth_header=(-H "x-internal-token: $INTERNAL_TOKEN")
  elif [[ "$auth_mode" == "bad" ]]; then
    auth_header=(-H "x-internal-token: WRONG_TOKEN")
  fi

  echo "RESPONSE:"
  soft_run curl -sS -i -X "$method" "$BASE_URL$path" \
    -H "content-type: application/json" \
    "${auth_header[@]}" \
    ${body:+-d "$body"}
  echo
}

call_api_capture_body() {
  local name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local auth_mode="${5:-none}"

  hr
  echo "== $name (CAPTURE) =="

  local auth_header=()
  if [[ "$auth_mode" == "good" ]]; then
    auth_header=(-H "x-internal-token: $INTERNAL_TOKEN")
  elif [[ "$auth_mode" == "bad" ]]; then
    auth_header=(-H "x-internal-token: WRONG_TOKEN")
  fi

  local safe_name="${name//[^a-zA-Z0-9]/_}"
  local hdr_file="$OUT_DIR/headers_${safe_name}_${TS}.txt"
  local body_file="$OUT_DIR/body_${safe_name}_${TS}.json"

  echo "REQUEST: $method $path"
  echo "headers_file=$hdr_file"
  echo "body_file=$body_file"
  echo

  set +e
  curl -sS -D "$hdr_file" -o "$body_file" -X "$method" "$BASE_URL$path" \
    -H "content-type: application/json" \
    "${auth_header[@]}" \
    ${body:+-d "$body"}
  local rc=$?
  set -e

  echo "-- status --"
  head -n 1 "$hdr_file" || true
  echo "-- body --"
  cat "$body_file" | pp_json || true
  echo "exit=$rc"
  echo

  cat "$body_file"
  return 0
}

backend_call() {
  local name="$1"
  local method="$2"
  local url="$3"
  local body="${4:-}"

  hr
  echo "== Backend: $name =="
  echo "REQUEST: $method $url"
  if [[ -n "$body" ]]; then
    echo "REQUEST_BODY:"
    echo "$body" | pp_json
  else
    echo "REQUEST_BODY: <none>"
  fi
  echo

  echo "RESPONSE:"
  if [[ "$method" == "GET" ]]; then
    soft_run curl -sS -i "$url"
  else
    soft_run curl -sS -i -X "$method" "$url" -H "content-type: application/json" ${body:+-d "$body"}
  fi
  echo
}

aws_snapshot() {
  hr
  echo "== AWS Snapshot: routes, integrations, lambda config (sanitized) =="

  if ! need_aws; then
    echo "aws cli not available -> snapshot skipped"
    return 0
  fi

  echo "-- routes --"
  soft_run aws apigatewayv2 get-routes --api-id "$API_ID" --region "$AWS_REGION" \
    --query 'Items[].{RouteKey:RouteKey,Target:Target}' --output table
  echo

  echo "-- integrations --"
  soft_run aws apigatewayv2 get-integrations --api-id "$API_ID" --region "$AWS_REGION" \
    --query 'Items[].{IntegrationId:IntegrationId,IntegrationType:IntegrationType,IntegrationUri:IntegrationUri,PayloadFormatVersion:PayloadFormatVersion}' \
    --output table
  echo

  echo "-- lambda (sanitized: env keys only) --"
  soft_run aws lambda get-function-configuration \
    --function-name "$FN" \
    --region "$AWS_REGION" \
    --query '{Handler:Handler,Runtime:Runtime,Timeout:Timeout,MemorySize:MemorySize,LastModified:LastModified,EnvKeys:keys(Environment.Variables)}' \
    --output json
  echo
}

lambda_direct_invoke() {
  local name="$1"
  local raw_path="$2"
  local method="$3"
  local body="$4"
  local include_token="$5"  # yes/no

  if ! need_aws; then
    hr
    echo "== Lambda Invoke: $name skipped (aws cli not available) =="
    return 0
  fi

  hr
  echo "== Lambda Invoke: $name =="

  local token_field=""
  if [[ "$include_token" == "yes" ]]; then
    token_field="\"x-internal-token\": \"${INTERNAL_TOKEN}\","
  fi

  local payload="$OUT_DIR/apigwv2_${name}_${TS}.json"
  cat > "$payload" <<JSON
{
  "version": "2.0",
  "routeKey": "${method} ${raw_path}",
  "rawPath": "${raw_path}",
  "headers": {
    "content-type": "application/json",
    ${token_field}
    "x-contract-probe": "true"
  },
  "requestContext": { "http": { "method": "${method}", "path": "${raw_path}" } },
  "body": $(printf '%s' "$body" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))'),
  "isBase64Encoded": false
}
JSON

  echo "payload_file=$payload"
  local out="$OUT_DIR/lambda_out_${name}_${TS}.json"
  soft_run aws lambda invoke --function-name "$FN" --region "$AWS_REGION" \
    --payload "fileb://$payload" \
    "$out"
  echo "lambda_output_file=$out"
  cat "$out" | pp_json
  echo
}

############################################
# Build change set
############################################
CHECK_LIVE_REQ='{
  "env":"live",
  "changes":[
    {"target":"ui","op":"set","path":"rateLimit.rpm","value":'"$RPM"'},
    {"target":"policy","op":"set","path":"rules.mode","value":"'"$MODE"'"}
  ]
}'

############################################
# 0) Snapshot
############################################
if [[ "$SKIP_AWS_SNAPSHOT" != "yes" ]]; then
  aws_snapshot
else
  hr; echo "== 0) Snapshot skipped =="; echo
fi

############################################
# 1) Public router health
############################################
call_api "1) GET /healthz (public)" "GET" "/healthz" "" "none"

############################################
# 2) Auth gate negatives
############################################
call_api "2a) POST /config/publish-canonical (no token)" "POST" "/config/publish-canonical" '{}' "none"
call_api "2b) POST /config/publish-canonical (wrong token)" "POST" "/config/publish-canonical" '{}' "bad"

############################################
# 3) Publish routes (positive)
############################################
PUBLISH_CANONICAL_BODY="$(call_api_capture_body "3a_POST_publish_canonical" "POST" "/config/publish-canonical" '{}' "good")"
call_api "3b) POST /config/publish-to-s3 (good token)" "POST" "/config/publish-to-s3" '{}' "good"

############################################
# 4) /git/get-file discovery
############################################
GIT_GETFILE_MIN='{"ref":"'"$GIT_REF_DEFAULT"'","path":"'"$GIT_PATH_DEFAULT"'"}'
call_api "4a) POST /git/get-file (good token, minimal payload)" "POST" "/git/get-file" "$GIT_GETFILE_MIN" "good"

if [[ -n "${REPO_OWNER}" && -n "${REPO_NAME}" ]]; then
  GIT_GETFILE_FULL='{"owner":"'"$REPO_OWNER"'","repo":"'"$REPO_NAME"'","ref":"'"$GIT_REF_DEFAULT"'","path":"'"$GIT_PATH_DEFAULT"'"}'
  call_api "4b) POST /git/get-file (good token, explicit owner/repo)" "POST" "/git/get-file" "$GIT_GETFILE_FULL" "good"
else
  hr
  echo "== 4b) Skipping explicit owner/repo variant: REPO_OWNER/REPO_NAME not set =="
  echo
fi

call_api "4c) POST /git/get-file (bad request: empty body -> expect 400)" "POST" "/git/get-file" '' "good"

############################################
# 5) /config/check-live
############################################
call_api "5a) POST /config/check-live (good token)" "POST" "/config/check-live" "$CHECK_LIVE_REQ" "good"
call_api "5b) POST /config/check-live (missing changes -> expect 400)" "POST" "/config/check-live" '{"env":"live"}' "good"

############################################
# 6) /config/check-open-pr
############################################
call_api "6a) POST /config/check-open-pr (good token)" "POST" "/config/check-open-pr" "$CHECK_LIVE_REQ" "good"
call_api "6b) POST /config/check-open-pr (empty body -> expect 400)" "POST" "/config/check-open-pr" '{}' "good"

############################################
# 7) /config/ensure-pr (capture)
############################################
ENSURE_BODY="$(call_api_capture_body "7a_POST_ensure_pr" "POST" "/config/ensure-pr" "$CHECK_LIVE_REQ" "good")"

PR_URL="$(jget "$ENSURE_BODY" '.pr.htmlUrl // empty')"
HEAD_REF="$(jget "$ENSURE_BODY" '.headBranch // .pr.headRef // empty')"
BASE_REF="$(jget "$ENSURE_BODY" '.baseBranch // .pr.baseRef // "'"$GIT_REF_DEFAULT"'"')"
DECISION="$(jget "$ENSURE_BODY" '.decision // empty')"

hr
echo "== ENSURE-PR SUMMARY =="
kv "decision" "${DECISION:-<unknown>}"
kv "pr_url" "${PR_URL:-<none>}"
kv "base_ref" "${BASE_REF:-<unknown>}"
kv "head_ref" "${HEAD_REF:-<unknown>}"
kv "requested_rpm" "$RPM"
kv "requested_mode" "$MODE"
echo

############################################
# 8) Backend reads + config updated trigger
############################################
# Note: Your local docker may 500 on ui-config/policy if it reads from S3 without creds.
backend_call "8a) GET /actuator/health" "GET" "$BACKEND_BASE_URL/actuator/health"
backend_call "8b) GET /ui-config" "GET" "$BACKEND_BASE_URL/ui-config"
backend_call "8c) GET /policy" "GET" "$BACKEND_BASE_URL/policy"

backend_call "8d) POST /internal/config-updated" "POST" "$BACKEND_BASE_URL/internal/config-updated" \
'{
  "env":"live",
  "updated":["ui","policy"],
  "version":"contract-probe-'"$TS"'"
}'

############################################
# 9) Lambda direct invoke (raw contract)
############################################
if [[ "$SKIP_LAMBDA_INVOKE" != "yes" ]]; then
  lambda_direct_invoke "healthz_direct" "/healthz" "GET" "" "no"
  lambda_direct_invoke "check_open_pr_direct_no_token" "/config/check-open-pr" "POST" "$CHECK_LIVE_REQ" "no"
  lambda_direct_invoke "check_open_pr_direct_good_token" "/config/check-open-pr" "POST" "$CHECK_LIVE_REQ" "yes"
else
  hr; echo "== 9) Lambda direct invoke skipped =="; echo
fi

############################################
# 10) Recent log tail
############################################
if [[ "$SKIP_LOG_TAIL" != "yes" ]]; then
  hr
  echo "== 10) Recent Lambda logs (last 20m) =="
  if need_aws; then
    soft_run aws logs tail "/aws/lambda/$FN" --since 20m --region "$AWS_REGION"
  else
    echo "aws cli not available -> log tail skipped"
  fi
else
  hr; echo "== 10) Log tail skipped =="; echo
fi

hr
echo "DONE. Log saved to: $LOG_FILE"
