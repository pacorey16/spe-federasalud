# Deployment Guide

This document provides detailed instructions for deploying the SIMPL EDC application to Kubernetes environments using Helm charts.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Vault Configuration](#vault-configuration)
- [Environment Setup](#environment-setup)
- [Deployment Steps](#deployment-steps)
- [Verification](#verification)
- [Troubleshooting](#troubleshooting)

## Prerequisites

Before deploying the application, ensure the following components are installed and configured in your Kubernetes cluster:

### Required Components
1. **Kubernetes cluster**
2. **Helm**
3. **PostgreSQL database**
4. **HashiCorp Vault** with Vault Agent Injector

### Required Access
- Kubernetes cluster admin access
- Vault admin access for initial configuration
- kubectl configured for target cluster

## Vault Configuration

**Important**: The following Vault configuration steps need to be executed only once per cluster. Skip this section if Vault is already configured for your namespace.

### Step 1: Access Vault

```bash
# Get Vault pod name
kubectl get pods -n vault

# Access Vault shell (replace vault-0 with your pod name)
kubectl exec -it vault-0 -n vault -- /bin/sh
```

### Step 2: Authenticate with Vault

```bash
# Login to Vault (you'll need the root token or admin credentials)
vault login
```

### Step 3: Configure Secret Engine

```bash
# Create a KV v2 secret engine (replace 'dev' with your environment name)
vault secrets enable -path=dev kv-v2
```

### Step 4: Enable Kubernetes Authentication

```bash
# Enable Kubernetes auth method
vault auth enable kubernetes

# Configure Kubernetes authentication
vault write auth/kubernetes/config \
    kubernetes_host="https://$KUBERNETES_PORT_443_TCP_ADDR:443"
```

### Step 5: Create Vault Policy

```bash
# Create policy for reading secrets (adjust path as needed)
vault policy write dev-policy - <<EOF
path "dev/data/*" {
   capabilities = ["read"]
}
EOF
```

**Policy configuration explanation**
In this example, `dev-policy` is the name of the policy—it can be any name you choose. The `path "dev/data/*"` should correspond to the secret engine path you enabled in Step 3. Make sure the path matches your actual secret engine configuration.

### Step 6: Create Kubernetes Role

```bash
# Create role binding policy with service accounts and namespaces
vault write auth/kubernetes/role/gaiax-edc_role \
    bound_service_account_names=gaiax-edc-dev* \
    bound_service_account_namespaces=gaiax-edc-dev* \
    policies=dev-policy \
    ttl=24h
```

**Role Configuration Explanation:**

- `gaiax-edc_role` is the name of the Vault role. You can choose any name for this role.
- `gaiax-edc-dev*` is used as a wildcard pattern for both service account names and Kubernetes namespaces. This means:
    - The service account name must start with `gaiax-edc-dev`.
    - The service account must be created in a namespace that also starts with `gaiax-edc-dev`.
- If you use a different naming convention for your namespaces or service accounts, update the role configuration accordingly to match your actual names.
- `dev-policy` refers to the Vault policy created in the previous steps.

## Environment Setup

### Required Environment Variables

The following secrets need to be configured in Vault for each deployment:

| Variable | Description | Example                                            |
|----------|-------------|----------------------------------------------------|
| `edc_ionos_access_key` | IONOS S3 access key | Contact gaiax team                                 |
| `edc_ionos_endpoint` | IONOS S3 endpoint | Contact gaiax team                                 |
| `edc_ionos_endpoint_region` | IONOS S3 region | `de`                                               |
| `edc_ionos_secret_key` | IONOS S3 secret key | Contact gaiax team                                 |
| `edc_ionos_token` | IONOS S3 token | Contact gaiax team                                 |
| `contractmanager_apikey` | Contract manager API key | Contact gaiax team                                 |
| `edc_datasource_default_password` | Default database password | Your DB password                                   |
| `edc_datasource_policy_password` | Policy database password | Your DB password                                   |
| `otel_experimental_log_level` | OpenTelemetry log level | `debug`                                            |
| `otel_exporter_otlp_endpoint` | OTLP exporter endpoint | `http://collector.yourdomain.com`                  |
| `otel_exporter_otlp_protocol` | OTLP protocol | `http/protobuf`                                    |
| `otel_instrumentation_http_url_connection_enabled` | HTTP instrumentation | `true` or `false`                                  |
| `otel_instrumentation_jdbc_enabled` | JDBC instrumentation | `true` or `false`                                  |
| `otel_instrumentation_jersey_enabled` | Jersey instrumentation | `true` or `false`                                  |
| `otel_instrumentation_servlet_enabled` | Servlet instrumentation | `true` or `false`                                  |
| `otel_logs_exporter` | Logs exporter | `none`                                             |
| `otel_metrics_exporter` | Metrics exporter | `none`                                             |
| `otel_resource_attributes` | Resource attributes | `service.name=app-name,deployment.environment=env` |
| `otel_traces_exporter` | Traces exporter | `otlp`                                             |
| `fr_gxfs_s3_access_key` | MinIO S3 access key | `yoursecret`                                       |
| `fr_gxfs_s3_secret_key` | MinIO S3 secret key | `yoursecret`                                       |
| `fr_gxfs_s3_endpoint` | MinIO S3 endpoint URL | `http://miniohost:minioport`                       |

### Step 1: Prepare Application Secrets

Create the required secrets in Vault using the naming convention that matches your Helm chart configuration.

**For Consumer deployment:**
```bash
vault kv put gaia-x/<your-consumer-namespace>-simpl-edc \
    edc_ionos_access_key="your_access_key" \
    edc_ionos_endpoint="your_endpoint" \
    edc_ionos_endpoint_region="de" \
    edc_ionos_secret_key="your_secret" \
    edc_ionos_token="your_token" \
    contractmanager_apikey="your_api_key" \
    edc_datasource_default_password="db_password" \
    edc_datasource_policy_password="db_password" \
    fr_gxfs_s3_access_key="yoursecret" \
    fr_gxfs_s3_secret_key="yoursecret" \
    fr_gxfs_s3_endpoint="http://miniohost:minioport" \
    otel_experimental_log_level="debug" \
    otel_exporter_otlp_endpoint="http://collector.yourdomain.com" \
    otel_exporter_otlp_protocol="http/protobuf" \
    otel_instrumentation_http_url_connection_enabled="true" \
    otel_instrumentation_jdbc_enabled="true" \
    otel_instrumentation_jersey_enabled="true" \
    otel_instrumentation_servlet_enabled="true" \
    otel_logs_exporter="none" \
    otel_metrics_exporter="none" \
    otel_resource_attributes="service.name=edc-consumer,deployment.environment=dev" \
    otel_traces_exporter="otlp"
```

**For Provider deployment:**
```bash
vault kv put gaia-x/<your-provider-namespace>-simpl-edc \
    edc_ionos_access_key="your_access_key" \
    edc_ionos_endpoint="your_endpoint" \
    edc_ionos_endpoint_region="de" \
    edc_ionos_secret_key="your_secret" \
    edc_ionos_token="your_token" \
    contractmanager_apikey="your_api_key" \
    edc_datasource_default_password="db_password" \
    edc_datasource_policy_password="db_password" \
    fr_gxfs_s3_access_key="yoursecret" \
    fr_gxfs_s3_secret_key="yoursecret" \
    fr_gxfs_s3_endpoint="http://miniohost:minioport" \
    otel_experimental_log_level="debug" \
    otel_exporter_otlp_endpoint="http://collector.yourdomain.com" \
    otel_exporter_otlp_protocol="http/protobuf" \
    otel_instrumentation_http_url_connection_enabled="true" \
    otel_instrumentation_jdbc_enabled="true" \
    otel_instrumentation_jersey_enabled="true" \
    otel_instrumentation_servlet_enabled="true" \
    otel_logs_exporter="none" \
    otel_metrics_exporter="none" \
    otel_resource_attributes="service.name=edc-provider,deployment.environment=dev" \
    otel_traces_exporter="otlp"
```

## Deployment Steps

### Step 1: Prepare Namespace

```bash
# Create namespaces if they don't exist
kubectl create namespace <your-consumer-namespace>
kubectl create namespace <your-provider-namespace>
```

### Step 2: Verify Dependencies

Ensure PostgreSQL databases are available and accessible from the cluster.

### Step 3: Deploy with Helm

Navigate to the charts directory and deploy both consumer and provider:

```bash
# Navigate to charts directory
cd charts

# Install consumer
helm install consumer --values values-consumer.yaml . \
    --namespace <your-consumer-namespace>

# Install provider
helm install provider --values values-provider.yaml . \
    --namespace <your-provider-namespace>
```

### Step 4: Monitor Deployment

```bash
# Watch consumer deployment
kubectl get pods -w -n <your-consumer-namespace>

# Watch provider deployment
kubectl get pods -w -n <your-provider-namespace>
```

## Verification

### Check Application Status

```bash
# Verify consumer pods are running
kubectl get pods -n <your-consumer-namespace>

# Verify provider pods are running
kubectl get pods -n <your-provider-namespace>

## Configuration Parameters

The following table describes the key configuration parameters available in the Helm chart:

| Parameter | Description | Example |
|-----------|-------------|---------|
| `deployment.image` | Container image path | `code.europa.eu:4567/simpl/simpl-open/development/gaia-x-edc/simpl-edc/consumer` |
| `deployment.tag` | Image tag | `latest` |
| `deployment.imagePullPolicy` | Image pull policy | `Always` |
| `deployment.label` | Deployment label | `consumer` |
| `deployment.callbackAddress` | EDC callback address | `https://edc-consumer.dev.simpl-europe.eu` |
| `service.apiPort` | API service port | `29191` |
| `service.controlPort` | Control service port | `29192` |
| `service.managementPort` | Management service port | `29193` |
| `service.protocolPort` | Protocol service port | `29194` |
| `service.publicPort` | Public service port | `29291` |
| `ingress.enabled` | Enable ingress | `true` |
| `ingress.host` | Ingress hostname | `edc-consumer.dev.simpl-europe.eu` |
| `ingress.clusterIssuer` | Ingress cluster issuer | `dev-prod` |
| `env.edcParticipantId` | id under which deployed EDC will be recognized, its optional field and will be ignored if tls.url.participantIdSource is provided | `consumer` |
| `env.edcIdsId` | EDC IDS identifier | `urn:connector:consumer` |
| `env.datasourceurl` | Database connection string | `jdbc:postgresql://host:5432/db` |
| `contractmanager.extension.enabled` | Enable contract manager extension | `false` |
| `javadebug.enabled` | Enable Java debugging | `false` |
| `tls.url.participantIdSource` | url to endpoint that returns participant id in authentication provider service. If not provided then env.edcParticipantId will be used | http://authentication-provider.consumer01.svc.cluster.local:8080/v1/credentials |

### MinIO S3 Configuration

The following MinIO S3 configuration parameters should be stored in Vault at the path specified by `secretConfigPath`:

| Parameter | Description | Example |
|-----------|-------------|---------|
| `fr_gxfs_s3_access_key` | MinIO S3 access key | `yoursecret` |
| `fr_gxfs_s3_secret_key` | MinIO S3 secret key | `yoursecret` |
| `fr_gxfs_s3_endpoint` | MinIO S3 endpoint URL | `http://miniohost:minioport` |

**Note:** These values are automatically injected as environment variables from Vault secrets during deployment. The Vault agent template exports all key-value pairs from the configured secret path as environment variables.

## Troubleshooting

### Common Issues

**Pod fails to start with Vault errors:**
- Verify Vault role and policy configuration
- Check service account name matches Vault role binding
- Ensure secret path exists in Vault

**Database connection errors:**
- Verify PostgreSQL is accessible from the cluster
- Check database credentials in Vault
- Validate connection string format

**Application cannot reach external services:**
- Check network policies and firewall rules
- Verify DNS resolution
- Test connectivity from within the cluster
