#!/usr/bin/env bash
set -euo pipefail

############################################
# MCP + BACKEND CONTRACT PROBE (BETTER)
# - Randomized change-set per run (demo-friendly)
# - Captures ensure-pr response and prints PR URL/head branch
# - Fetches BOTH base branch + PR head config files and shows diffs:
#     - ui.rateLimit.rpm
#     - policy.rules.mode
# - Keeps all original contract checks + lambda direct invoke + log tail
############################################

############################################
# REQUIRED VALUES
############################################
API_ID="${API_ID:-702s1q2eid}"
AWS_REGION="${AWS_REGION:-us-east-1}"
FN="${FN:-promptline-mcp-router}"

# Router auth header expects x-internal-token
INTERNAL_TOKEN="${INTERNAL_TOKEN:-${MCP_INTERNAL_API_KEY:-}}"

# Backend for optional direct calls from *wherever this script runs*
BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:8080}"

# GitHub repo info (only used for /git/get-file payload examples)
REPO_OWNER="${REPO_OWNER:-${GITHUB_OWNER:-}}"
REPO_NAME="${REPO_NAME:-${GITHUB_REPO:-}}"

# Reasonable defaults for reading a file
GIT_REF_DEFAULT="${GIT_REF_DEFAULT:-config/live}"
GIT_PATH_DEFAULT="${GIT_PATH_DEFAULT:-config/ui.json}"

# Where router writes canonical runtime artifacts (based on your publish-canonical output)
UI_RUNTIME_PATH="${UI_RUNTIME_PATH:-runtime/ui.json}"
POLICY_RUNTIME_PATH="${POLICY_RUNTIME_PATH:-runtime/policy.json}"

# Change set knobs (override if you want deterministic demo)
RPM="${RPM:-$((100 + RANDOM % 900))}"   # 100-999
MODE="${MODE:-$( ((RANDOM % 2)) && echo strict || echo balanced )}"

# Optional: if you want an easy monotonically increasing demo value:
# RPM="${RPM:-$(( (10#$(date +%S)) + 100 ))}"

if [[ -z "${INTERNAL_TOKEN}" ]]; then
  echo "ERROR: set INTERNAL_TOKEN (or MCP_INTERNAL_API_KEY) in env"
  exit 1
fi

if ! command -v aws >/dev/null 2>&1; then
  echo "ERROR: aws cli not found"
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

echo "=== MCP + BACKEND CONTRACT PROBE (BETTER) ==="
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
echo "runtime_ui_path=$UI_RUNTIME_PATH"
echo "runtime_policy_path=$POLICY_RUNTIME_PATH"
echo "demo_change ui.rateLimit.rpm=$RPM"
echo "demo_change policy.rules.mode=$MODE"
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

soft_run() {
  set +e
  "$@"
  local rc=$?
  set -e
  echo "exit=$rc"
  return 0
}

pp_json() {
  if command -v jq >/dev/null 2>&1; then
    jq . 2>/dev/null || cat
  else
    cat
  fi
}

# print a compact line for demo output
kv() { printf "%-24s %s\n" "$1" "$2"; }

# Extract a jq field from a JSON string, safely
jget() {
  local json="$1"
  local expr="$2"
  if command -v jq >/dev/null 2>&1; then
    echo "$json" | jq -r "$expr" 2>/dev/null || true
  else
    # jq not installed -> no extraction
    true
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

# Capturing variant (returns body to stdout; still logs headers/body in file)
call_api_capture_body() {
  local name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local auth_mode="${5:-none}"

  hr
  echo "== $name (CAPTURE) =="
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

  # Separate headers + body cleanly.
  local hdr_file="$OUT_DIR/headers_${name//[^a-zA-Z0-9]/_}_${TS}.txt"
  local body_file="$OUT_DIR/body_${name//[^a-zA-Z0-9]/_}_${TS}.json"

  echo "RESPONSE_HEADERS_FILE: $hdr_file"
  echo "RESPONSE_BODY_FILE:    $body_file"
  echo

  set +e
  curl -sS -D "$hdr_file" -o "$body_file" -X "$method" "$BASE_URL$path" \
    -H "content-type: application/json" \
    "${auth_header[@]}" \
    ${body:+-d "$body"}
  local rc=$?
  set -e

  echo "-- headers --"
  cat "$hdr_file" || true
  echo "-- body --"
  cat "$body_file" | pp_json || true
  echo "exit=$rc"
  echo

  cat "$body_file"
  return 0
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

# Fetch file via router /git/get-file (returns JSON response body)
git_get_file() {
  local ref="$1"
  local path="$2"
  curl -sS -X POST "$BASE_URL/git/get-file" \
    -H "content-type: application/json" \
    -H "x-internal-token: $INTERNAL_TOKEN" \
    -d '{"ref":"'"$ref"'","path":"'"$path"'"}'
}

# Extract a JSON "content" string and parse it as JSON if possible
# Prints:
#  - value of key expression if given
#  - or the whole parsed JSON
extract_inner_json() {
  local outer="$1"
  local inner
  inner="$(jget "$outer" '.content // empty')"
  if [[ -z "$inner" ]]; then
    echo ""
    return 0
  fi
  # inner is a JSON file stored as a string
  if command -v jq >/dev/null 2>&1; then
    echo "$inner" | jq . 2>/dev/null || echo "$inner"
  else
    echo "$inner"
  fi
}

# Pretty demo diff: base vs head for the two fields
show_pr_value_diff() {
  local base_ref="$1"
  local head_ref="$2"

  hr
  echo "== DEMO: CONFIG DIFF (base vs PR head) =="

  echo "-- fetching base ($base_ref) --"
  local base_ui_resp base_pol_resp
  base_ui_resp="$(git_get_file "$base_ref" "$UI_RUNTIME_PATH" || true)"
  base_pol_resp="$(git_get_file "$base_ref" "$POLICY_RUNTIME_PATH" || true)"

  echo "-- fetching head ($head_ref) --"
  local head_ui_resp head_pol_resp
  head_ui_resp="$(git_get_file "$head_ref" "$UI_RUNTIME_PATH" || true)"
  head_pol_resp="$(git_get_file "$head_ref" "$POLICY_RUNTIME_PATH" || true)"

  # Extract values
  local base_rpm head_rpm base_mode head_mode
  if command -v jq >/dev/null 2>&1; then
    base_rpm="$(jget "$(echo "$base_ui_resp" | jq -r '.content // ""' 2>/dev/null || echo "")" '.rateLimit.rpm // empty')"
    head_rpm="$(jget "$(echo "$head_ui_resp" | jq -r '.content // ""' 2>/dev/null || echo "")" '.rateLimit.rpm // empty')"
    base_mode="$(jget "$(echo "$base_pol_resp" | jq -r '.content // ""' 2>/dev/null || echo "")" '.rules.mode // empty')"
    head_mode="$(jget "$(echo "$head_pol_resp" | jq -r '.content // ""' 2>/dev/null || echo "")" '.rules.mode // empty')"
  else
    base_rpm=""
    head_rpm=""
    base_mode=""
    head_mode=""
  fi

  echo
  kv "base_branch" "$base_ref"
  kv "pr_head_branch" "$head_ref"
  echo

  kv "ui.rateLimit.rpm (base)" "${base_rpm:-<unknown>}"
  kv "ui.rateLimit.rpm (head)" "${head_rpm:-<unknown>}"
  kv "policy.rules.mode (base)" "${base_mode:-<unknown>}"
  kv "policy.rules.mode (head)" "${head_mode:-<unknown>}"

  echo
  if [[ -n "${base_rpm:-}" && -n "${head_rpm:-}" && "$base_rpm" != "$head_rpm" ]]; then
    echo "✅ rpm changed: $base_rpm -> $head_rpm"
  elif [[ -n "${base_rpm:-}" && -n "${head_rpm:-}" ]]; then
    echo "⚠️ rpm did not change (base == head). That usually means ensure-pr returned an existing PR without updates."
  else
    echo "ℹ️ Could not compute rpm diff (jq missing or file missing)."
  fi

  if [[ -n "${base_mode:-}" && -n "${head_mode:-}" && "$base_mode" != "$head_mode" ]]; then
    echo "✅ mode changed: $base_mode -> $head_mode"
  elif [[ -n "${base_mode:-}" && -n "${head_mode:-}" ]]; then
    echo "⚠️ mode did not change (base == head)."
  else
    echo "ℹ️ Could not compute mode diff."
  fi

  echo
  echo "-- raw router responses saved in log; for quick peek: --"
  echo "base_ui_found=$(jget "$base_ui_resp" '.found // empty') base_ui_path=$UI_RUNTIME_PATH"
  echo "head_ui_found=$(jget "$head_ui_resp" '.found // empty') head_ui_path=$UI_RUNTIME_PATH"
  echo "base_policy_found=$(jget "$base_pol_resp" '.found // empty') base_policy_path=$POLICY_RUNTIME_PATH"
  echo "head_policy_found=$(jget "$head_pol_resp" '.found // empty') head_policy_path=$POLICY_RUNTIME_PATH"
  echo
}

############################################
# BUILD CHANGESET (randomized by default)
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
PUBLISH_CANONICAL_BODY="$(call_api_capture_body "3a_POST_publish_canonical" "POST" "/config/publish-canonical" '{}' "good")"
call_api "3b) POST /config/publish-to-s3 (good token)" "POST" "/config/publish-to-s3" '{}' "good"

############################################
# 4) Router: /git/get-file (contract discovery)
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
############################################
call_api "5a) POST /config/check-live (good token)" "POST" "/config/check-live" "$CHECK_LIVE_REQ" "good"
call_api "5b) POST /config/check-live (missing changes -> expect 400)" "POST" "/config/check-live" '{"env":"live"}' "good"

############################################
# 6) Router: Phase 1 /config/check-open-pr
############################################
call_api "6a) POST /config/check-open-pr (good token)" "POST" "/config/check-open-pr" "$CHECK_LIVE_REQ" "good"
call_api "6b) POST /config/check-open-pr (empty body -> expect 400)" "POST" "/config/check-open-pr" '{}' "good"

############################################
# 7) Router: Phase 2 /config/ensure-pr (capture + demo diff)
############################################
ENSURE_BODY="$(call_api_capture_body "7a_POST_ensure_pr" "POST" "/config/ensure-pr" "$CHECK_LIVE_REQ" "good")"

# Extract PR info (best-effort)
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

# If ensure-pr produced a head ref, show base vs head diff for demo
if [[ -n "${HEAD_REF}" ]]; then
  show_pr_value_diff "$BASE_REF" "$HEAD_REF"
else
  hr
  echo "== DEMO DIFF SKIPPED: could not extract head_ref from ensure-pr response =="
  echo
fi

############################################
# 8) Backend triggers (read runtime + invalidate)
############################################
backend_call "8a) GET /ui-config" "GET" "$BACKEND_BASE_URL/ui-config"
backend_call "8b) GET /policy" "GET" "$BACKEND_BASE_URL/policy"

backend_call "8c) POST /internal/config-updated (best effort)" "POST" "$BACKEND_BASE_URL/internal/config-updated" \
'{
  "env":"live",
  "updated":["ui","policy"],
  "version":"contract-probe-'"$TS"'"
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
