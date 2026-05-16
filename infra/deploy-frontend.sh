#!/usr/bin/env bash
# Build the SPA and publish to S3 + invalidate CloudFront.
#
# Usage:
#   S3_BUCKET=my-demo-bucket \
#   CLOUDFRONT_DISTRIBUTION_ID=E1234ABCD5678 \
#   ./infra/deploy-frontend.sh
#
# Optional:
#   AWS_PROFILE / AWS_REGION — picked up by aws CLI as usual.

set -euo pipefail

: "${S3_BUCKET:?S3_BUCKET is required}"
: "${CLOUDFRONT_DISTRIBUTION_ID:?CLOUDFRONT_DISTRIBUTION_ID is required}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEB_DIR="${SCRIPT_DIR}/../prompt-hubs-web"

echo "▶ Building frontend in ${WEB_DIR}"
cd "${WEB_DIR}"
npm ci
npm run build

echo "▶ Syncing hashed assets (long cache)"
aws s3 sync dist/ "s3://${S3_BUCKET}/" \
  --delete \
  --exclude "index.html" \
  --cache-control "public,max-age=31536000,immutable"

echo "▶ Uploading index.html (no-cache)"
aws s3 cp dist/index.html "s3://${S3_BUCKET}/index.html" \
  --cache-control "no-cache,no-store,must-revalidate"

echo "▶ Invalidating CloudFront"
aws cloudfront create-invalidation \
  --distribution-id "${CLOUDFRONT_DISTRIBUTION_ID}" \
  --paths "/" "/index.html" >/dev/null

echo "✔ Done. Demo should be live within ~30s."
