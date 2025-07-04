#!/usr/bin/env bash
set -euo pipefail

# Debug helpers
log() { echo "[$(date +%T)] $*"; }

log "Checking environment variables..."
: "${OSSRH_USERNAME:?Environment variable OSSRH_USERNAME not set}"
: "${OSSRH_PASSWORD:?Environment variable OSSRH_PASSWORD not set}"

BUNDLE="build/maven-central-bundle.zip"
API_BASE="https://central.sonatype.com/api/v1/publisher"
TOKEN=$(printf "%s:%s" "$OSSRH_USERNAME" "$OSSRH_PASSWORD" | base64)
AUTH_HEADER="Authorization: Bearer $TOKEN"

log "Checking if bundle exists at: $BUNDLE"
if [[ ! -f "$BUNDLE" ]]; then
  log "❌ Bundle not found at $BUNDLE"
  exit 1
fi

log "Uploading bundle..."
DEPLOY_ID=$(curl -s -H "$AUTH_HEADER" \
  -F "bundle=@$BUNDLE" \
  "$API_BASE/upload?publishingType=AUTOMATIC")

if [[ -z "$DEPLOY_ID" || "$DEPLOY_ID" == "null" ]]; then
  log "❌ No deployment ID received. Upload failed."
  exit 1
fi

log "📦 Deployment ID: $DEPLOY_ID"
log "⏳ Polling deployment status (every 10s for up to 30 minutes)..."

FAIL=false
for i in {1..180}; do
  STATUS=$(curl -s -H "$AUTH_HEADER" \
    -X POST \
    "$API_BASE/status?id=$DEPLOY_ID" \
    | jq -r ".deploymentState")
  log "Status = $STATUS"

  case "$STATUS" in
    PUBLISHED|PUBLISHING)
      log "✅ Successfully PUBLISHED to Maven Central."
      exit 0
      ;;
    FAILED)
      log "❌ Deployment FAILED. Cleaning up..."
      curl -s -X DELETE -H "$AUTH_HEADER" \
        "$API_BASE/deployment/$DEPLOY_ID" || true
      FAIL=true
      break
      ;;
    *)
      # PENDING, VALIDATING, VALIDATED, PUBLISHING
      :
      ;;
  esac

  sleep 10
done

if [ "$FAIL" = true ]; then
  exit 1
fi

log "⏰ Timeout: still '$STATUS' after 30 minutes."
exit 1
