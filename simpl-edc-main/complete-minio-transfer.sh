#!/bin/bash

# This script demonstrates a complete MinioS3 file transfer using REST API calls.
# It assumes you have two EDC instances (provider and consumer) and a MinIO server
#
# Usage
# Development
# ./complete-minio-transfer.sh
#
# Environment
# ./complete-minio-transfer.sh \
#  "minio.env.com:9000" \
#  "provider.env.com:443" \
#  "provider.env.com:443" \
#  "consumer.env.com:443" \
#  "consumer.env.com:443"
#```

# Allow overriding hosts via environment variables or script arguments
MINIO_HOST="${MINIO_HOST:-localhost:9000}"
PROVIDER_HOST="${PROVIDER_HOST:-localhost:19193}"
PROVIDER_PROTOCOL_HOST="${PROVIDER_PROTOCOL_HOST:-localhost:19194}"
CONSUMER_HOST="${CONSUMER_HOST:-localhost:29193}"
CONSUMER_PROTOCOL_HOST="${CONSUMER_PROTOCOL_HOST:-localhost:29194}"

# Optionally allow passing as arguments: ./script.sh [MINIO_HOST] [PROVIDER_HOST] [PROVIDER_PROTOCOL_HOST] [CONSUMER_HOST] [CONSUMER_PROTOCOL_HOST]
if [ -n "$1" ]; then MINIO_HOST="$1"; fi
if [ -n "$2" ]; then PROVIDER_HOST="$2"; fi
if [ -n "$3" ]; then PROVIDER_PROTOCOL_HOST="$3"; fi
if [ -n "$4" ]; then CONSUMER_HOST="$4"; fi
if [ -n "$5" ]; then CONSUMER_PROTOCOL_HOST="$5"; fi

echo "Complete MinioS3 Transfer: example-s3.txt (Provider → Consumer)"
echo "=================================================================="

echo ""
echo "Prerequisites:"
echo "MinIO server running on $MINIO_HOST"
echo "File 'example-s3.txt' exists in 'provider-bucket'"
echo "Bucket 'consumer-bucket' exists"
echo "Provider connector on ports 19193/19194 (or $PROVIDER_HOST/$PROVIDER_PROTOCOL_HOST)"
echo "Consumer connector on ports 29193/29294 (or $CONSUMER_HOST/$CONSUMER_PROTOCOL_HOST)"

echo ""
echo "Step 1: Create MinioS3 Asset"
echo "================================"

ASSET_RESPONSE=$(curl -s --location "http://$PROVIDER_HOST/management/v3/assets" \
--header 'Content-Type: application/json' \
--header 'X-Api-Key: password' \
--data-raw "{
  \"@context\": {
    \"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\"
  },
  \"@id\": \"example-s3-asset\",
  \"properties\": {
    \"name\": \"Example S3 File\",
    \"description\": \"Test file for MinioS3 transfer\",
    \"contenttype\": \"text/plain\"
  },
  \"dataAddress\": {
    \"type\": \"MinioS3\",
    \"bucketName\": \"provider-bucket\",
    \"objectName\": \"example-s3.txt\",
    \"endpoint\": \"http://$MINIO_HOST\"
  }
}")

echo "Asset created: example-s3-asset"

echo ""
echo "Step 2: Create Policy Definition"
echo "==================================="

POLICY_RESPONSE=$(curl -s --location "http://$PROVIDER_HOST/management/v3/policydefinitions" \
--header 'Content-Type: application/json' \
--header 'X-Api-Key: password' \
--data-raw "{
  \"@context\": {
    \"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\"
  },
  \"@id\": \"minio-s3-policy\",
  \"policy\": {
    \"@context\": \"http://www.w3.org/ns/odrl.jsonld\",
    \"@type\": \"Set\",
    \"permission\": [
      {
        \"action\": \"use\"
      }
    ],
    \"prohibition\": [],
    \"obligation\": []
  }
}")

echo "Policy created: minio-s3-policy"

echo ""
echo "Step 3: Create Contract Definition"
echo "====================================="

CONTRACT_DEF_RESPONSE=$(curl -s --location "http://$PROVIDER_HOST/management/v3/contractdefinitions" \
--header 'Content-Type: application/json' \
--header 'X-Api-Key: password' \
--data-raw "{
  \"@context\": {
    \"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\"
  },
  \"@id\": \"minio-s3-contract-def\",
  \"accessPolicyId\": \"minio-s3-policy\",
  \"contractPolicyId\": \"minio-s3-policy\",
  \"assetsSelector\": [{
    \"@type\": \"CriterionDto\",
    \"operandLeft\": \"https://w3id.org/edc/v0.0.1/ns/id\",
    \"operator\": \"=\",
    \"operandRight\": \"example-s3-asset\"
  }]
}")

echo "Contract definition created: minio-s3-contract-def"

echo ""
echo "Step 4: Query Catalog"
echo "========================"

CATALOG_RESPONSE=$(curl -s --location "http://$CONSUMER_HOST/management/v3/catalog/request" \
--header 'Content-Type: application/json' \
--header 'x-api-key: password' \
--data-raw "{
  \"@context\": {
    \"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\"
  },
  \"@type\": \"CatalogRequestDto\",
  \"counterPartyAddress\": \"http://$PROVIDER_PROTOCOL_HOST/protocol\",
  \"protocol\": \"dataspace-protocol-http\"
}")

# Extract policy ID from catalog response for the specific asset
POLICY_ID=$(echo "$CATALOG_RESPONSE" | jq -r '."dcat:dataset" | select(."@id" == "example-s3-asset") | ."odrl:hasPolicy"."@id"' 2>/dev/null)

if [ "$POLICY_ID" = "null" ] || [ -z "$POLICY_ID" ]; then
    echo "Could not extract policy ID from catalog"
    echo "Catalog response: $CATALOG_RESPONSE"
    exit 1
fi

echo "Catalog queried, policy ID: $POLICY_ID"

echo ""
echo "Step 5: Start Contract Negotiation"
echo "====================================="

CONTRACT_NEG_RESPONSE=$(curl -s --location "http://$CONSUMER_HOST/management/v3/contractnegotiations" \
--header 'Content-Type: application/json' \
--header 'x-api-key: password' \
--data-raw "{
  \"@context\": {
    \"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\"
  },
  \"@type\": \"ContractRequest\",
  \"counterPartyAddress\": \"http://$PROVIDER_PROTOCOL_HOST/protocol\",
  \"protocol\": \"dataspace-protocol-http\",
  \"policy\": {
    \"@context\": \"http://www.w3.org/ns/odrl.jsonld\",
    \"@id\": \"$POLICY_ID\",
    \"@type\": \"Offer\",
    \"permission\": [
      {
        \"action\": \"use\",
        \"target\": \"example-s3-asset\"
      }
    ],
    \"assigner\": \"provider\",
    \"target\": \"example-s3-asset\"
  }
}")

CONTRACT_ID=$(echo "$CONTRACT_NEG_RESPONSE" | jq -r '."@id"' 2>/dev/null)

if [ "$CONTRACT_ID" = "null" ] || [ -z "$CONTRACT_ID" ]; then
    echo "Contract negotiation failed"
    echo "Response: $CONTRACT_NEG_RESPONSE"
    exit 1
fi

echo "Contract negotiation started: $CONTRACT_ID"

echo ""
echo "Step 6: Wait for Contract Agreement"
echo "======================================"

# Wait for contract to complete
for i in {1..10}; do
    sleep 2
    CONTRACT_STATUS=$(curl -s --location "http://$CONSUMER_HOST/management/v3/contractnegotiations/$CONTRACT_ID" \
    --header 'x-api-key: password')
    
    AGREEMENT_ID=$(echo "$CONTRACT_STATUS" | jq -r '.contractAgreementId' 2>/dev/null)
    STATE=$(echo "$CONTRACT_STATUS" | jq -r '.state' 2>/dev/null)
    
    echo "Contract state: $STATE"
    
    if [ "$AGREEMENT_ID" != "null" ] && [ -n "$AGREEMENT_ID" ]; then
        echo "Contract agreement completed: $AGREEMENT_ID"
        break
    fi
    
    if [ $i -eq 10 ]; then
        echo "Contract negotiation timeout"
        exit 1
    fi
done

echo ""
echo "Step 7: Start MinioS3 Transfer"
echo "=================================="

TRANSFER_RESPONSE=$(curl -s --location "http://$CONSUMER_HOST/management/v3/transferprocesses" \
--header 'X-API-Key: password' \
--header 'Content-Type: application/json' \
--data-raw "{
  \"@context\": {
    \"@vocab\": \"https://w3id.org/edc/v0.0.1/ns/\"
  },
  \"@type\": \"TransferRequestDto\",
  \"connectorId\": \"provider\",
  \"counterPartyAddress\": \"http://$PROVIDER_PROTOCOL_HOST/protocol\",
  \"contractId\": \"$AGREEMENT_ID\",
  \"assetId\": \"example-s3-asset\",
  \"protocol\": \"dataspace-protocol-http\",
  \"transferType\": \"MinioS3-PUSH\",
  \"dataDestination\": {
    \"type\": \"MinioS3\",
    \"bucketName\": \"consumer-bucket\",
    \"objectName\": \"example-s3.txt\",
    \"endpoint\": \"http://$MINIO_HOST\"
  }
}")

TRANSFER_ID=$(echo "$TRANSFER_RESPONSE" | jq -r '."@id"' 2>/dev/null)

if [ "$TRANSFER_ID" = "null" ] || [ -z "$TRANSFER_ID" ]; then
    echo "Transfer initiation failed"
    echo "Response: $TRANSFER_RESPONSE"
    exit 1
fi

echo "Transfer initiated: $TRANSFER_ID"

echo ""
echo "Step 8: Monitor Transfer Status"
echo "=================================="

for i in {1..15}; do
    sleep 3
    TRANSFER_STATUS=$(curl -s --location "http://$CONSUMER_HOST/management/v3/transferprocesses/$TRANSFER_ID" \
    --header 'X-API-Key: password')
    
    STATE=$(echo "$TRANSFER_STATUS" | jq -r '.state' 2>/dev/null)
    echo "Transfer state: $STATE"
    
    if [ "$STATE" = "COMPLETED" ]; then
        echo "Transfer completed successfully!"
        break
    elif [ "$STATE" = "TERMINATED" ] || [ "$STATE" = "FAILED" ]; then
        echo "Transfer failed with state: $STATE"
        echo "Transfer details: $TRANSFER_STATUS"
        exit 1
    fi
    
    if [ $i -eq 15 ]; then
        echo "Transfer taking longer than expected, check status manually:"
        echo "curl -H 'X-API-Key: password' 'http://$CONSUMER_HOST/management/v3/transferprocesses/$TRANSFER_ID'"
        break
    fi
done

echo ""
echo "Step 9: Verify File Transfer"
echo "==============================="

echo "Using pre-configured MinIO client (mc)..."
echo "Note: This assumes 'mc' is already configured with an alias pointing to $MINIO_HOST"

echo ""
echo "Checking if file exists in source bucket (provider-bucket):"
if mc stat local/provider-bucket/example-s3.txt >/dev/null 2>&1; then
    echo "✅ Source file exists: provider-bucket/example-s3.txt"
    mc stat local/provider-bucket/example-s3.txt
else
    echo "❌ Source file NOT found: provider-bucket/example-s3.txt"
fi

echo ""
echo "Checking if file exists in destination bucket (consumer-bucket):"
if mc stat local/consumer-bucket/example-s3.txt >/dev/null 2>&1; then
    echo "✅ Transfer successful! File exists: consumer-bucket/example-s3.txt"
    mc stat local/consumer-bucket/example-s3.txt
else
    echo "❌ Transfer verification failed! File NOT found: consumer-bucket/example-s3.txt"
fi

echo ""
echo "List all files in both buckets:"
echo "Provider bucket contents:"
mc ls local/provider-bucket/ 2>/dev/null || echo "Cannot list provider bucket or bucket is empty"
echo ""
echo "Consumer bucket contents:"
mc ls local/consumer-bucket/ 2>/dev/null || echo "Cannot list consumer bucket or bucket is empty"

echo ""
echo "If mc is not configured, you need to set it up first:"
echo "  mc alias set local http://$MINIO_HOST YOUR_ACCESS_KEY YOUR_SECRET_KEY"

echo ""
echo "Transfer Summary:"
echo "==================="
echo "Source: provider-bucket/example-s3.txt (MinioS3)"
echo "Destination: consumer-bucket/example-s3.txt (MinioS3)"
echo "Transfer Type: MinioS3-PUSH"
echo "Contract ID: $AGREEMENT_ID"
echo "Transfer ID: $TRANSFER_ID"
