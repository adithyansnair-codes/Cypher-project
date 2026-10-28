#!/usr/bin/env bash
set -euo pipefail

# Creates the private, encrypted, versioned evidence bucket used by CYPHER.
# Run this from the EC2 instance with an AWS identity that may administer S3.
#
#   ./infra/aws/bootstrap-s3.sh [bucket-name] [ec2-role-name]
#
# When the role name is omitted, it is read from EC2 instance metadata.

REGION="${AWS_REGION:-ap-south-1}"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
BUCKET="${1:-cypher-media-${ACCOUNT_ID}-${REGION}}"
ROLE_NAME="${2:-}"

if [[ -z "$ROLE_NAME" ]]; then
  TOKEN="$(curl -fsS -X PUT \
    -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' \
    http://169.254.169.254/latest/api/token)"
  ROLE_NAME="$(curl -fsS \
    -H "X-aws-ec2-metadata-token: ${TOKEN}" \
    http://169.254.169.254/latest/meta-data/iam/security-credentials/)"
fi

if [[ -z "$ROLE_NAME" ]]; then
  echo "Could not determine the EC2 role. Pass it as the second argument." >&2
  exit 1
fi

ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}"

if [[ "$REGION" == "us-east-1" ]]; then
  aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" >/dev/null
else
  aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" \
    --create-bucket-configuration "LocationConstraint=${REGION}" >/dev/null
fi

aws s3api put-public-access-block \
  --bucket "$BUCKET" \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

aws s3api put-bucket-encryption \
  --bucket "$BUCKET" \
  --server-side-encryption-configuration \
  '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"},"BucketKeyEnabled":false}]}'

aws s3api put-bucket-versioning \
  --bucket "$BUCKET" \
  --versioning-configuration Status=Enabled

aws s3api put-bucket-lifecycle-configuration \
  --bucket "$BUCKET" \
  --lifecycle-configuration \
  '{"Rules":[{"ID":"expire-old-evidence-versions","Status":"Enabled","Filter":{"Prefix":"cypher/incidents/"},"NoncurrentVersionExpiration":{"NoncurrentDays":90},"AbortIncompleteMultipartUpload":{"DaysAfterInitiation":7}}]}'

POLICY="$(
  python3 - "$BUCKET" "$ROLE_ARN" <<'PY'
import json
import sys

bucket, role = sys.argv[1:3]
print(json.dumps({
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "DenyInsecureTransport",
            "Effect": "Deny",
            "Principal": "*",
            "Action": "s3:*",
            "Resource": [f"arn:aws:s3:::{bucket}", f"arn:aws:s3:::{bucket}/*"],
            "Condition": {"Bool": {"aws:SecureTransport": "false"}},
        },
        {
            "Sid": "CypherEvidenceObjects",
            "Effect": "Allow",
            "Principal": {"AWS": role},
            "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
            "Resource": f"arn:aws:s3:::{bucket}/cypher/incidents/*",
        },
    ],
}, separators=(",", ":")))
PY
)"

aws s3api put-bucket-policy --bucket "$BUCKET" --policy "$POLICY"

echo "S3 evidence bucket ready: s3://${BUCKET}/cypher/incidents/"
echo "EC2 role: ${ROLE_ARN}"
echo
echo "Set these values for the application service:"
echo "S3_BUCKET=${BUCKET}"
echo "AWS_REGION=${REGION}"
echo "S3_MEDIA_PREFIX=cypher/incidents"
