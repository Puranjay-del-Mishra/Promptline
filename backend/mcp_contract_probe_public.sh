#!/usr/bin/env bash
set -euo pipefail

############################################
# PUBLIC CONTRACT PROBE (NO SECRETS)
# - Tests only public endpoints
# - Works against hosted MCP + hosted EC2 backend
# - Also works locally (but ui-config/policy may 500 if AWS creds are missing)
############################################

# Accept CLI args, with env var fallbacks
MCP_BASE_URL="${MCP_BASE_URL:-}"
BACKEND_BASE_URL="${BACKEND_BASE_URL:-}"

OUT_DIR="${OUT_DIR:-./out}"
TS="$(date -u +"%Y%m%dT%H%M%SZ")"
mkdir -p "$OUT_DIR"
LOG_FILE="$OUT_DIR/public_contract_${TS}.txt"

usage() {
  cat <<EOF
Usage:
  bash backend/mcp_contract_probe_public.sh --mcp <MCP_BASE_URL> --backend <BACKEND_BASE_URL>

Env vars (alternatives):
  MCP_BASE_URL=...
  BACKEND_BASE_URL=...

Examples:
  bash backend/mcp_contract_probe_public.sh \\
    --mcp https://702s1q2eid.execute-api.us-east-1.amazonaws.com \\
    --backend http://54.210.22.147:8080
EOF
}

# Simple arg parser
while [[ $# -gt 0 ]]; do
  case "$1" in
    --mcp) MCP_BASE_URL="${2:-}"; shift 2;;
    --backend) BACKEND_BASE_URL="${2:-}"; shift 2;;
    -h|--help) usage; exit 0;;
    *) echo "Unknown arg: $1"; usage; exit 2;;
  esac
done

if [[ -z "$MCP_BASE_URL" || -z "$BACKEND_BASE_URL" ]]; then
  echo "ERROR: missing --mcp and/or --backend (or set MCP_BASE_URL/BACKEND_BASE_URL)"
  usage
  exit 1
fi

exec > >(tee -a "$LOG_FILE") 2>&1

hr() { echo "------------------------------------------------------------"; }

pp_json() {
  if command -v jq >/dev/null 2>&1; then jq . 2>/dev/null || cat; else cat; fi
}

# Non-fatal HTTP call: prints status, headers (optional), and body
http_probe() {
  local name="$1"
  local url="$2"
  hr
  echo "== $name =="
  echo "GET $url"
  set +e
  local resp
  resp="$(curl -sS -i "$url")"
  local rc=$?
  set -e

  if [[ $rc -ne 0 ]]; then
    echo "curl_exit=$rc"
    echo "❌ request failed"
    return 0
  fi

  # Print status line + body
  echo "$resp" | sed -n '1p'
  echo
  echo "-- body (best-effort json pretty) --"
  # crude split: find first empty line, print remainder
  echo "$resp" | awk 'BEGIN{p=0} /^\r?$/{p=1; next} {if(p) print}' | pp_json
  echo
  return 0
}

echo "=== PUBLIC CONTRACT PROBE ==="
echo "timestamp_utc=$TS"
echo "mcp_base_url=$MCP_BASE_URL"
echo "backend_base_url=$BACKEND_BASE_URL"
echo "log_file=$LOG_FILE"
echo

# MCP public
http_probe "1) MCP GET /healthz (public)" "$MCP_BASE_URL/healthz"

# Backend health (your local run showed /healthz 404; so probe actuator first)
http_probe "2a) Backend GET /actuator/health" "$BACKEND_BASE_URL/actuator/health"
http_probe "2b) Backend GET /healthz (optional)" "$BACKEND_BASE_URL/healthz"

# Backend runtime reads (expected to work on hosted; local may 500 if it needs AWS creds)
http_probe "3a) Backend GET /ui-config (public read)" "$BACKEND_BASE_URL/ui-config"
http_probe "3b) Backend GET /policy (public read)" "$BACKEND_BASE_URL/policy"

hr
echo "DONE. Log saved to: $LOG_FILE"
