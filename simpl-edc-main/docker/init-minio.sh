#!/bin/sh
set -e

# Install MinIO client
wget https://dl.min.io/client/mc/release/linux-amd64/mc -O /usr/local/bin/mc
chmod +x /usr/local/bin/mc

# Wait for MinIO to be ready
echo "Waiting for MinIO to be ready..."
until nc -z minio 9000; do
  echo "Waiting for MinIO..."
  sleep 2
done

echo "Configuring MinIO client and creating buckets..."

# Configure MinIO client with correct syntax
mc alias set myminio http://minio:9000 minioadmin minioadmin

# Create buckets
echo "Creating provider-bucket..."
mc mb myminio/provider-bucket --ignore-existing

echo "Creating consumer-bucket..."
mc mb myminio/consumer-bucket --ignore-existing

# Upload example file to provider-bucket
echo "Uploading example-s3.txt to provider-bucket..."
mc cp /example-s3.txt myminio/provider-bucket/

echo "Initialization completed successfully."
echo "Buckets created: provider-bucket, consumer-bucket"
echo "File uploaded: example-s3.txt to provider-bucket"
