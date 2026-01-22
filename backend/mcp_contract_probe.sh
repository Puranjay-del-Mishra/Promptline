#!/usr/bin/env bash
set -euo pipefail

############################################
# REQUIRED VALUES
############################################
API_ID="${API_ID:-702s1q2eid}"
AWS_REGION="${AWS_REGION:-us-east-1}"
FN="${FN:-promptline-mcp-router}"

# Router auth header expects x-internal-token
INTERNAL_TOKEN="${INTERNAL_TOKEN:-${MCP_INTERNAL_API_KEY:-}}"
BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:8080}"

# GitHub repo info (only used for /git/get-file payload examples)
REPO_OWNER="${REPO_OWNER:-${GITHUB_OWNER:-}}"
REPO_NAME="${REPO_NAME:-${GITHUB_REPO:-}}"

# Reasonable defaults for reading a file
GIT_REF_DEFAULT="${GIT_REF_DEFAULT:-config/live}"
GIT_PATH_DEFAULT="${GIT_PATH_DEFAULT:-config/ui.json}"

if [[ -z "${INTERNAL_TOKEN}" ]]; then
  echo "ERROR: set INTERNAL_TOKEN (or MCP_INTERNAL_API_KEY) in env"
  exit 1
fi

############################################
# LOG SETUP
############################################
TS="$(date -u +"%Y%m%dT%H%M%SZ")"
OUT_DIR="${OUT_DIR:-./out}"
mkdir -p "$OUT_DIR"
LOG_FILE="$OUT_DIR/contract_full_${TS}.txt"

exec > >(tee -a "$LOG_FILE") 2>&1

echo "=== MCP + BACKEND CONTRACT FULL PROBE ==="
echo "timestamp_utc=$TS"
echo "api_id=$API_ID"
echo "region=$AWS_REGION"
echo "lambda_function=$FN"
echo "backend_base_url=$BACKEND_BASE_URL"
echo "token_len=$(printf "%s" "$INTERNAL_TOKEN" | wc -c | tr -d ' ')"
echo "repo_owner=${REPO_OWNER:-<unset>}"
echo "repo_name=${REPO_NAME:-<unset>}"
echo "git_ref_default=$GIT_REF_DEFAULT"
echo "git_path_default=$GIT_PATH_DEFAULT"
echo

############################################
# DERIVE API BASE URL
############################################
BASE_URL="$(aws apigatewayv2 get-api --api-id "$API_ID" --region "$AWS_REGION" --query ApiEndpoint --output text)"
echo "MCP_BASE_URL=$BASE_URL"
echo

############################################
# HELPERS
############################################
hr() { echo "------------------------------------------------------------"; }

# Runs a command but never fails the whole script.
soft_run() {
  set +e
  "$@"
  local rc=$?
  set -e
  echo "exit=$rc"
  return 0
}

# Pretty print JSON if possible, otherwise raw.
pp_json() {
  if command -v jq >/dev/null 2>&1; then
    jq . 2>/dev/null || cat
  else
    cat
  fi
}

# curl wrapper that always logs response headers + body
# AUTH_MODE: none | bad | good
call_api() {
  local name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local auth_mode="${5:-none}"

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

aws_snapshot() {
  hr
  echo "== AWS Snapshot: API routes, integrations, lambda config =="
  echo "-- routes --"
  soft_run aws apigatewayv2 get-routes --api-id "$API_ID" --region "$AWS_REGION" \
    --query 'Items[].{RouteKey:RouteKey,Target:Target}' --output table
  echo
  echo "-- integrations --"
  soft_run aws apigatewayv2 get-integrations --api-id "$API_ID" --region "$AWS_REGION" \
    --query 'Items[].{IntegrationId:IntegrationId,IntegrationType:IntegrationType,IntegrationUri:IntegrationUri,PayloadFormatVersion:PayloadFormatVersion}' \
    --output table
  echo
  echo "-- lambda (sanitized) --"
  soft_run aws lambda get-function-configuration \
    --function-name "$FN" \
    --region "$AWS_REGION" \
    --query '{Handler:Handler,Runtime:Runtime,Timeout:Timeout,MemorySize:MemorySize,CodeSize:CodeSize,LastModified:LastModified,EnvKeys:keys(Environment.Variables)}' \
    --output json
  echo
}

lambda_direct_invoke() {
  local name="$1"
  local raw_path="$2"
  local method="$3"
  local body="$4"
  local include_token="$5"  # yes/no

  hr
  echo "== Lambda Invoke: $name =="

  local token_field=""
  if [[ "$include_token" == "yes" ]]; then
    token_field="\"x-internal-token\": \"${INTERNAL_TOKEN}\","
  fi

  local payload="$OUT_DIR/apigwv2_${name// /_}_${TS}.json"
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
  echo "invoking..."
  local out="$OUT_DIR/lambda_out_${name// /_}_${TS}.json"
  soft_run aws lambda invoke --function-name "$FN" --region "$AWS_REGION" \
    --payload "fileb://$payload" \
    "$out"
  echo "lambda_output_file=$out"
  cat "$out" | pp_json
  echo
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

############################################
# 0) Snapshot
############################################
aws_snapshot

############################################
# 1) Router public
############################################
call_api "1) GET /healthz (public)" "GET" "/healthz" "" "none"

############################################
# 2) Router auth gate negatives (one route)
############################################
call_api "2a) POST /config/publish-canonical (no token)" "POST" "/config/publish-canonical" '{}' "none"
call_api "2b) POST /config/publish-canonical (wrong token)" "POST" "/config/publish-canonical" '{}' "bad"

############################################
# 3) Router publish routes (positive)
############################################
call_api "3a) POST /config/publish-canonical (good token)" "POST" "/config/publish-canonical" '{}' "good"
call_api "3b) POST /config/publish-to-s3 (good token)" "POST" "/config/publish-to-s3" '{}' "good"

############################################
# 4) Router: /git/get-file (contract discovery)
# Assumption: request includes ref + path, optionally owner/repo.
############################################
GIT_GETFILE_MIN='{"ref":"'"$GIT_REF_DEFAULT"'","path":"'"$GIT_PATH_DEFAULT"'"}'
call_api "4a) POST /git/get-file (good token, minimal payload)" "POST" "/git/get-file" "$GIT_GETFILE_MIN" "good"

if [[ -n "${REPO_OWNER}" && -n "${REPO_NAME}" ]]; then
  GIT_GETFILE_FULL='{"owner":"'"$REPO_OWNER"'","repo":"'"$REPO_NAME"'","ref":"'"$GIT_REF_DEFAULT"'","path":"'"$GIT_PATH_DEFAULT"'"}'
  call_api "4b) POST /git/get-file (good token, explicit owner/repo)" "POST" "/git/get-file" "$GIT_GETFILE_FULL" "good"
else
  hr
  echo "== 4b) Skipping owner/repo variant: REPO_OWNER/REPO_NAME not set =="
  echo
fi

call_api "4c) POST /git/get-file (bad request: empty body)" "POST" "/git/get-file" '' "good"

############################################
# 5) Router: Phase 0-ish /config/check-live
# Assumption: same request shape as ConfigCheckLiveRequest.
############################################
CHECK_LIVE_REQ='{
  "env":"live",
  "changes":[
    {"target":"ui","op":"set","path":"rateLimit.rpm","value":123},
    {"target":"policy","op":"set","path":"rules.mode","value":"strict"}
  ]
}'
call_api "5a) POST /config/check-live (good token)" "POST" "/config/check-live" "$CHECK_LIVE_REQ" "good"
call_api "5b) POST /config/check-live (missing changes -> expect 400)" "POST" "/config/check-live" '{"env":"live"}' "good"

############################################
# 6) Router: Phase 1 /config/check-open-pr
############################################
call_api "6a) POST /config/check-open-pr (good token)" "POST" "/config/check-open-pr" "$CHECK_LIVE_REQ" "good"
call_api "6b) POST /config/check-open-pr (empty body -> expect 400)" "POST" "/config/check-open-pr" '{}' "good"

############################################
# 7) Router: Phase 2 /config/ensure-pr (may create PR)
############################################
call_api "7a) POST /config/ensure-pr (good token)" "POST" "/config/ensure-pr" "$CHECK_LIVE_REQ" "good"

############################################
# 8) Backend triggers (read runtime + invalidate)
############################################
backend_call "8a) GET /ui-config" "GET" "$BACKEND_BASE_URL/ui-config"
backend_call "8b) GET /policy" "GET" "$BACKEND_BASE_URL/policy"

# You already have this pattern; leaving as a safe best-effort trigger:
backend_call "8c) POST /internal/config-updated (best effort)" "POST" "$BACKEND_BASE_URL/internal/config-updated" \
'{
  "env":"live",
  "updated":["ui","policy"],
  "version":"contract-probe-'$TS'"
}'

############################################
# 9) Lambda direct invoke (raw APIGWv2 contract)
############################################
lambda_direct_invoke "healthz_direct" "/healthz" "GET" "" "no"
lambda_direct_invoke "check_open_pr_direct_no_token" "/config/check-open-pr" "POST" "$CHECK_LIVE_REQ" "no"
lambda_direct_invoke "check_open_pr_direct_good_token" "/config/check-open-pr" "POST" "$CHECK_LIVE_REQ" "yes"

############################################
# 10) Recent logs tail (post-run)
############################################
hr
echo "== 10) Recent Lambda logs (last 20m) =="
soft_run aws logs tail "/aws/lambda/$FN" --since 20m --region "$AWS_REGION"

hr
echo "DONE. Log saved to: $LOG_FILE"
