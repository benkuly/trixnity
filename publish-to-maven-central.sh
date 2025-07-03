#!/usr/bin/env bash
set -euo pipefail

BUNDLE="build/maven-central-bundle.zip"
API_BASE="https://central.sonatype.com/api/v1/publisher"
TOKEN=$(printf "%s:%s" "$OSSRH_USERNAME" "$OSSRH_PASSWORD" | base64)
AUTH_HEADER="Authorization: Bearer $TOKEN"

echo "Uploading bundle: $BUNDLE..."
DEPLOY_ID=$(curl -s -H "$AUTH_HEADER" \
  -F "bundle=@$BUNDLE" \
  "$API_BASE/upload?publishingType=AUTOMATIC")

if [[ -z "$DEPLOY_ID" ]]; then
  echo "❌ No deployment ID received; upload likely failed."
  exit 1
fi

echo "📦 Deployment ID: $DEPLOY_ID"
echo "⏳ Polling deployment status (every 10s for up to 10 minutes)..."

FAIL=false
for i in {1..60}; do
  STATUS=$(curl -s -H "$AUTH_HEADER" \
    -X POST \
    "$API_BASE/status?id=$DEPLOY_ID" \
    | jq -r ".deploymentState")
  echo "  [$(date +%T)] Status = $STATUS"

  case "$STATUS" in
    PUBLISHED)
      echo "✅ Successfully PUBLISHED to Maven Central."
      exit 0
      ;;
    FAILED)
      echo "❌ Deployment FAILED. Cleaning up..."
      curl -s -X DELETE -H "$AUTH_HEADER" \
        "$API_BASE/deployment/$DEPLOY_ID" || true
      FAIL=true
      break
      ;;
    *)
      # PENDING, VALIDATING, VALIDATED, PUBLISHING
      ;;
  esac

  sleep 10
done

if [ "$FAIL" = true ]; then
  exit 1
fi

echo "⏰ Timeout: still '$STATUS' after 10 minutes."
exit 1
