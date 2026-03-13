Parameters

---

| name                              | description                                                                                                                            | example                                                                                  |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| deployment.image                  | path to image that should be used during deployment process                                                                            | code.europa.eu:4567/simpl/simpl-open/development/gaia-x-edc/simpl-edc/consumer           |
| deployment.tag                    | image tag that should be used during deployment process                                                                                | latest                                                                                   |
| deployment.imagePullPolicy        | image pull strategy that should be used during deployment process                                                                      | Always                                                                                   |
| deployment.label                  | Label name for deployment.yaml used as unique identifier for given deployment process                                                  | consumer                                                                                 |
| deployment.callbackAddress        | callback address of the edc                                                                                                            | https://edc-consumer.dev.simpl-europe.eu                                                 |
| service.apiPort                   | service port number associated with deployment api-port                                                                                | 29191                                                                                    |
| service.controlPort               | service port number associated with deployment control-port                                                                            | 29192                                                                                    |
| service.managementPort            | service port number associated with deployment management-port                                                                         | 29193                                                                                    |
| service.protocolPort              | service port number associated with deployment protocol-port                                                                           | 29194                                                                                    |
| service.publicPort                | service port number associated with deployment public-port                                                                             | 29291                                                                                    |
| ingress.enabled                   | flag that enable/disable ingress deployment                                                                                            | true                                                                                     |
| ingress.apiPath                   | prefix path for service.apiPort                                                                                                        | /api                                                                                     |
| ingress.protocolPath              | prefix path for service.protocolPort                                                                                                   | /protocol                                                                                |
| ingress.publicPath                | prefix path for service.publicPort                                                                                                     | /public                                                                                  |
| ingress.controlPath               | prefix path for service.controlPort                                                                                                    | /control                                                                                 |
| ingress.managementPath            | prefix path for service.managementPort                                                                                                 | /management                                                                              |
| ingress.host                      | host name under which whole release should be exposed in web                                                                           | edc-consumer.dev.simpl-europe.eu                                                         |
| ingress.tlsSecretName             | secret name for given host                                                                                                             | consumer-simpl-backend-tls                                                               |
| env.edcParticipantId              | id under which deployed EDC will be recognized, its optional field and will be ignored if tls.url.participantIdSource is provided      | consumer                                                                                 |
| env.edcIdsId                      | some other type of id under which deployed EDC will be recognized                                                                      | urn:connector:consumer                                                                   |
| env.datasourceurl                 | connection string to postgres db associated with EDC                                                                                   | jdbc:postgresql://cons-edc-postgres.<your-namespace>.svc.cluster.local:5432/postgres     |
| postgresql.username               | username to authenticate in postgres                                                                                                   | postgres                                                                                 |
| tls.url.participantIdSource       | url to endpoint that returns participant id in authentication provider service. If not provided then env.edcParticipantId will be used | http://authentication-provider.consumer01.svc.cluster.local:8080/v1/credentials          |
| tls.url.authenticationprovider    | url to https://code.europa.eu/simpl/simpl-open/development/iaa/authentication_provider instance                                        | http://authentication-provider.dataprovider01.svc.cluster.local:8080                     |
| javadebug.enabled                 | flag that allows to deploy instance on which remote debugging is possible                                                              | false                                                                                    |
| javadebug.port                    | port under which debug process is exposed                                                                                              | 5006                                                                                     |
| contractmanager.extension.enabled | flag that enable/disable contract manager extension                                                                                    | false                                                                                    |
| contractmanager.url               | domain name of contract manager extension                                                                                              | http://url:8080/contract/v1/                                                             |
| serviceAccount.enabled            | flag that enable/disable deployment of service account                                                                                 | true                                                                                     |
 

# Helm Chart for SIMPL EDC

This directory contains the Helm chart for deploying the SIMPL EDC application to Kubernetes.

## Quick Start

```bash
# Install consumer
helm install consumer --values values-consumer.yaml . --namespace <your-consumer-namespace>

# Install provider
helm install provider --values values-provider.yaml . --namespace <your-provider-namespace>

# Upgrade existing deployment
helm upgrade consumer --values values-consumer.yaml . --namespace <your-consumer-namespace>
```

## Chart Structure

```
charts/
├── Chart.yaml          # Chart metadata
├── values.yaml         # Default configuration values
├── values-consumer.yaml # Consumer-specific values
├── values-provider.yaml # Provider-specific values
└── templates/
    ├── deployment.yaml
    ├── service.yaml
    ├── ingress.yaml
    └── service_account.yaml
```

## Configuration

The chart can be configured by modifying the values files or by providing custom values during installation.

Key configuration options:
- Application image and tag
- Resource limits and requests
- Service configuration
- Ingress settings
- Vault integration settings
- Database connections

## For Complete Deployment Instructions

**See [Deployment Guide](../documents/deployment-guide.md)** for:
- Prerequisites and dependencies
- Vault configuration
- Environment setup
- Step-by-step deployment process
- Verification and troubleshooting

## Chart Development

```bash
# Validate chart syntax
helm lint .

# Render templates locally
helm template . --debug

# Test installation (dry-run)
helm install --dry-run --debug consumer --values values-consumer.yaml .
```

