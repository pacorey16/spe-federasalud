# About the project

This SIMPL EDC (Eclipse Dataspace Connector) is a customized connector implementation designed for the SIMPL European project to secure data exchange between participants in the dataspace.

The connector extends the Eclipse EDC framework with:
- MinIO S3 Extension: Native MinIO S3 support for data transfers (Gaia-X implementation)
- Infrastructure provisioning capabilities
- Contract management extensions
- Enhanced policy constraints and validation
- OpenTelemetry integration for observability
- EDelivery extension to trigger edelivery transfer

## Dependencies

- **Eclipse EDC Framework**: Core dataspace connector functionality
- **PostgreSQL**: Database for storing connector state and policies
- **HashiCorp Vault**: Secure storage for credentials and configuration
- **MinIO S3**: Compatible S3 storage for data transfers (primary)

## Installation

### Local Development Setup

#### Prerequisites
- Java 17+
- Maven 3.6+ (or use included Maven wrapper)
- PostgreSQL database
- Git

#### Steps

1. **Clone the repository**
   ```bash
   git clone git@code.europa.eu:simpl/simpl-open/development/gaia-x-edc/simpl-edc.git
   cd simpl-edc
   ```

2. **Build the application**
   ```bash
   ./mvnw clean install
   ```

3. **Configuration and run**
  See the [local guide](./local/README.MD) for the configuration options and running tutorial.

### Environment Deployment with Helm

For detailed deployment instructions to Kubernetes environments, please refer to:

**[Deployment Guide](./documents/deployment-guide.md)**

The deployment guide covers:
- Vault configuration and setup
- Environment preparation
- Step-by-step deployment process
- Configuration parameters
- Troubleshooting

## How to Use

The following sections describe the main data transfer flows supported by this EDC connector.

Before you start, configure your minio.

Your minio setup should have:
- provider bucket (with a file to transfer, as an example `example-s3.txt`)
- consumer-bucket

## FLOWS:

### TRANSFER PROCESS (MINIO S3 EXTENSION - RECOMMENDED)

This flow demonstrates data transfer using the MinIO S3 extension with native MinioS3-PUSH transfer type.
The flow has been tested and validated to work correctly with the integrated Gaia-X MinIO S3 extension.

#### Prerequisites
- MinIO server running on http://localhost:9000
- MinIO buckets created: `provider-bucket`, `consumer-bucket`
- File `example-s3.txt` exists in `provider-bucket`
- Provider connector on ports 19193/19194
- Consumer connector on ports 29193/29194

#### 1. Create MinioS3 asset on provider side

**Important**: Asset must use `type: "MinioS3"` to work with MinioS3-PUSH transfers.

```bash
curl --location 'http://localhost:19193/management/v3/assets' \
--header 'Content-Type: application/json' \
--header 'X-Api-Key: password' \
--data-raw '{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@id": "example-s3-asset",
  "properties": {
    "name": "Example S3 File",
    "description": "Test file for MinioS3 transfer",
    "contenttype": "text/plain"
  },
  "dataAddress": {
    "type": "MinioS3",
    "bucketName": "provider-bucket",
    "objectName": "example-s3.txt",
    "endpoint": "http://localhost:9000"
  }
}'
```

#### 2. Create policy definition

```bash
curl --location 'http://localhost:19193/management/v3/policydefinitions' \
--header 'Content-Type: application/json' \
--header 'X-Api-Key: password' \
--data-raw '{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@id": "minio-s3-policy",
  "policy": {
    "@context": "http://www.w3.org/ns/odrl.jsonld",
    "@type": "Set",
    "permission": [
      {
        "action": "use"
      }
    ],
    "prohibition": [],
    "obligation": []
  }
}'
```

#### 3. Create contract definition

```bash
curl --location 'http://localhost:19193/management/v3/contractdefinitions' \
--header 'Content-Type: application/json' \
--header 'X-Api-Key: password' \
--data-raw '{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@id": "minio-s3-contract-def",
  "accessPolicyId": "minio-s3-policy",
  "contractPolicyId": "minio-s3-policy",
  "assetsSelector": [{
    "@type": "CriterionDto",
    "operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
    "operator": "=",
    "operandRight": "example-s3-asset"
  }]
}'
```

#### 4. Query provider catalog from consumer

```bash
curl --location 'http://localhost:29193/management/v3/catalog/request' \
--header 'Content-Type: application/json' \
--header 'x-api-key: password' \
--data-raw '{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "CatalogRequestDto",
  "counterPartyAddress": "http://localhost:19194/protocol",
  "protocol": "dataspace-protocol-http"
}'
```

Extract the policy ID from the catalog response for the next step.

#### 5. Start contract negotiation

```bash
curl --location 'http://localhost:29193/management/v3/contractnegotiations' \
--header 'Content-Type: application/json' \
--header 'x-api-key: password' \
--data-raw '{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "ContractRequest",
  "counterPartyAddress": "http://localhost:19194/protocol",
  "protocol": "dataspace-protocol-http",
  "policy": {
    "@context": "http://www.w3.org/ns/odrl.jsonld",
    "@id": "POLICY_ID_FROM_CATALOG",
    "@type": "Offer",
    "permission": [
      {
        "action": "use",
        "target": "example-s3-asset"
      }
    ],
    "assigner": "provider",
    "target": "example-s3-asset"
  }
}'
```

#### 6. Wait for contract agreement and get contract ID

Monitor the contract negotiation until it reaches FINALIZED state:

```bash
curl --location 'http://localhost:29193/management/v3/contractnegotiations/NEGOTIATION_ID' \
--header 'x-api-key: password'
```

Extract the `contractAgreementId` from the response.

#### 7. Start MinioS3 transfer process

**Critical**: Use `transferType: "MinioS3-PUSH"` for MinioS3 destinations.

```bash
curl --location 'http://localhost:29193/management/v3/transferprocesses' \
--header 'X-API-Key: password' \
--header 'Content-Type: application/json' \
--data-raw '{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "TransferRequestDto",
  "connectorId": "provider",
  "counterPartyAddress": "http://localhost:19194/protocol",
  "contractId": "CONTRACT_AGREEMENT_ID",
  "assetId": "example-s3-asset",
  "protocol": "dataspace-protocol-http",
  "transferType": "MinioS3-PUSH",
  "dataDestination": {
    "type": "MinioS3",
    "bucketName": "consumer-bucket",
    "objectName": "example-s3.txt",
    "endpoint": "http://localhost:9000"
  }
}'
```

#### 8. Monitor transfer status

```bash
curl --location 'http://localhost:29193/management/v3/transferprocesses/TRANSFER_ID' \
--header 'X-API-Key: password'
```

Monitor until the transfer reaches `COMPLETED` state.

#### 9. Verify successful transfer

Check that the file has been transferred to the consumer bucket:

```bash
# Using MinIO CLI (mc)
mc ls local/consumer-bucket/

# Or using AWS CLI with MinIO endpoint
aws s3 ls consumer-bucket --endpoint-url http://localhost:9000
```

#### Automated Script

For convenience, use the complete automated script:

```bash
./complete-minio-transfer.sh
```

This script automates the entire flow from asset creation to transfer completion.

#### Key Success Factors

- Asset `dataAddress.type` must be `"MinioS3"`
- Transfer `transferType` must be `"MinioS3-PUSH"`
- Destination `type` must be `"MinioS3"`
- Both provider and consumer data planes must support MinioS3 transfers
- MinIO credentials must be properly configured

---

### TRANSFER PROCESS (TRANSFER PUSH TO S3 BUCKET - LEGACY IONOS)

1. Create asset, policy and contract definition on provider side. To do so please execute following http calls

to create asset
```
curl --location 'https://edc-provider.dev.simpl-europe.eu/management/v3/assets' \
--header 'Content-Type: application/json' \
--data-raw '{
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
        "edc": "https://w3id.org/edc/v0.0.1/ns/",
        "odrl": "http://www.w3.org/ns/odrl/2/"
    },
    "@id": "d05abb3c-13ec-4c21-8461-2f17ef12ca60",
    "properties": {
        "id": "d05abb3c-13ec-4c21-8461-2f17ef12ca60"
    },
    "dataAddress": {
        "@type": "DataAddress",
        "region": "de",
        "type": "IonosS3",
        "blobName": "european_health_data.csv",
        "keyName": "test-key-name",
        "storage": "s3-eu-central-1.ionoscloud.com",
        "bucketName": "simpl-provider"
    }
}'
```

to create policy

```
curl --location 'https://edc-provider.dev.simpl-europe.eu/management/v3/policydefinitions' \
--header 'Content-Type: application/json' \
--data-raw '{
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
        "edc": "https://w3id.org/edc/v0.0.1/ns/",
        "odrl": "http://www.w3.org/ns/odrl/2/"
    },
    "@id": "4ef0cb81-0f86-4907-99c8-9ce3ca5fda0a",
    "@type": "PolicyDefinition",
    "policy": {
        "@type": "odrl:Set",
        "odrl:permission": {
            "odrl:action": {
                "@id": "odrl:use"
            },
            "odrl:constraint": {
                "odrl:leftOperand": {
                    "@id": "odrl:deletion"
                },
                "odrl:operator": {
                    "@id": "odrl:eq"
                },
                "odrl:rightOperand": "after_use"
            }
        },
        "odrl:prohibition": [],
        "odrl:obligation": []  
    }
}'
```

```
curl --location 'https://edc-provider.dev.simpl-europe.eu/management/v3/policydefinitions' \
--header 'Content-Type: application/json' \
--data-raw '{
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
        "edc": "https://w3id.org/edc/v0.0.1/ns/",
        "odrl": "http://www.w3.org/ns/odrl/2/"
    },
    "@id": "85081882-c7f1-4f72-a347-70d42d3fa2dd",
    "@type": "PolicyDefinition",
    "policy": {
        "@type": "odrl:Set",
        "odrl:permission": {
            "odrl:action": {
                "@id": "http://simpl.eu/odrl/actions/search"
            }
        },
        "odrl:prohibition": [],
        "odrl:obligation": []
    }
}'
```

IMPORTANT
If you would like also check the **consumption** constraint function you may create a policy with additional permission. 
This is only valid in case **IamExtension** is enabled as this constraint compares the value of the code field in claim token attributes to the policy consumption field value.
```
curl --location 'https://edc-provider.dev.simpl-europe.eu/management/v3/policydefinitions' \
--header 'Content-Type: application/json' \
--data-raw '{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@id": "eu-policy",
  "policy": {
    "@context": "http://www.w3.org/ns/odrl.jsonld",
    "@type": "Set",
    "permission": [
      {
        "action": "use",
        "constraint": {
          "@type": "AtomicConstraint",
          "leftOperand": "consumption",
          "operator": {
            "@id": "odrl:eq"
          },
          "rightOperand": "CONSUMER"
        }
      }
    ],
    "prohibition": [],
    "obligation": []
  }
}'
```

to create contract definition (which binds given asset to policy)

```
curl --location 'https://edc-provider.dev.simpl-europe.eu/management/v3/contractdefinitions' \
--header 'Content-Type: application/json' \
--data-raw '{
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
        "edc": "https://w3id.org/edc/v0.0.1/ns/",
        "odrl": "http://www.w3.org/ns/odrl/2/"
    },
    "@id": "7073a2e8-6daa-4e90-8317-2a232ff61d82",
    "accessPolicyId": "85081882-c7f1-4f72-a347-70d42d3fa2dd",
    "contractPolicyId": "4ef0cb81-0f86-4907-99c8-9ce3ca5fda0a",
    "assetsSelector": {
        "@type": "Criterion",
        "operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
        "operator": "=",
        "operandRight": "d05abb3c-13ec-4c21-8461-2f17ef12ca60"
    }
}'
```

2. make request to query provider catalogue in order to get dcat:dataset.odrl:hasPolicy.@id

to query provider catalogue by consumer
```
curl --location 'https://edc-consumer.dev.simpl-europe.eu/management/v3/catalog/request' \
--header 'Content-Type: application/json' \
--header 'X-Api-Key: password' \
--data-raw '{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "counterPartyAddress": "https://edc-provider.dev.simpl-europe.eu/protocol",
  "protocol": "dataspace-protocol-http",
  "querySpec": {
    "limit": 10000,
    "filterExpression": [
    ]
  }
}'
```

example response 

```
{
    "@id": "8fb40c56-df8e-49dd-88c6-7ca2d2178ce5",
    "@type": "dcat:Catalog",
    "dcat:dataset": {
        "@id": "d05abb3c-13ec-4c21-8461-2f17ef12ca60",
        "@type": "dcat:Dataset",
        "odrl:hasPolicy": {
            "@id": "NzA3M2EyZTgtNmRhYS00ZTkwLTgzMTctMmEyMzJmZjYxZDgy:ZDA1YWJiM2MtMTNlYy00YzIxLTg0NjEtMmYxN2VmMTJjYTYw:Mjk3N2ZlNGQtODM2Yy00NzQyLWEyZDQtMzY4NTE3ZTM5YjBk",
            "@type": "odrl:Offer",
            "odrl:permission": {
                "odrl:action": {
                    "@id": "odrl:use"
                },
                "odrl:constraint": {
                    "odrl:leftOperand": {
                        "@id": "odrl:deletion"
                    },
                    "odrl:operator": {
                        "@id": "odrl:eq"
                    },
                    "odrl:rightOperand": "after_use"
                }
            },
            "odrl:prohibition": [],
            "odrl:obligation": []
        },
        "dcat:distribution": [
            {
                "@type": "dcat:Distribution",
                "dct:format": {
                    "@id": "HttpData-PULL"
                },
                "dcat:accessService": {
                    "@id": "ce5db7cc-dbae-47cf-b730-bc064e7735c5",
                    "@type": "dcat:DataService",
                    "dcat:endpointDescription": "dspace:connector",
                    "dcat:endpointUrl": "http://localhost:19194/protocol",
                    "dcat:endpointURL": "http://localhost:19194/protocol"
                }
            },
            {
                "@type": "dcat:Distribution",
                "dct:format": {
                    "@id": "IonosS3-PUSH"
                },
                "dcat:accessService": {
                    "@id": "ce5db7cc-dbae-47cf-b730-bc064e7735c5",
                    "@type": "dcat:DataService",
                    "dcat:endpointDescription": "dspace:connector",
                    "dcat:endpointUrl": "http://localhost:19194/protocol",
                    "dcat:endpointURL": "http://localhost:19194/protocol"
                }
            },
            {
                "@type": "dcat:Distribution",
                "dct:format": {
                    "@id": "HttpData-PUSH"
                },
                "dcat:accessService": {
                    "@id": "ce5db7cc-dbae-47cf-b730-bc064e7735c5",
                    "@type": "dcat:DataService",
                    "dcat:endpointDescription": "dspace:connector",
                    "dcat:endpointUrl": "http://localhost:19194/protocol",
                    "dcat:endpointURL": "http://localhost:19194/protocol"
                }
            },
            {
                "@type": "dcat:Distribution",
                "dct:format": {
                    "@id": "Infrastructure-PUSH"
                },
                "dcat:accessService": {
                    "@id": "ce5db7cc-dbae-47cf-b730-bc064e7735c5",
                    "@type": "dcat:DataService",
                    "dcat:endpointDescription": "dspace:connector",
                    "dcat:endpointUrl": "http://localhost:19194/protocol",
                    "dcat:endpointURL": "http://localhost:19194/protocol"
                }
            }
        ],
        "id": "d05abb3c-13ec-4c21-8461-2f17ef12ca60"
    },
    "dcat:distribution": [],
    "dcat:service": {
        "@id": "ce5db7cc-dbae-47cf-b730-bc064e7735c5",
        "@type": "dcat:DataService",
        "dcat:endpointDescription": "dspace:connector",
        "dcat:endpointUrl": "http://localhost:19194/protocol",
        "dcat:endpointURL": "http://localhost:19194/protocol"
    },
    "dspace:participantId": "provider",
    "@context": {
        "dcat": "http://www.w3.org/ns/dcat#",
        "dct": "http://purl.org/dc/terms/",
        "odrl": "http://www.w3.org/ns/odrl/2/",
        "dspace": "https://w3id.org/dspace/v0.8/",
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
        "edc": "https://w3id.org/edc/v0.0.1/ns/"
    }
}
```

so for this example id = NzA3M2EyZTgtNmRhYS00ZTkwLTgzMTctMmEyMzJmZjYxZDgy:ZDA1YWJiM2MtMTNlYy00YzIxLTg0NjEtMmYxN2VmMTJjYTYw:Mjk3N2ZlNGQtODM2Yy00NzQyLWEyZDQtMzY4NTE3ZTM5YjBk

3. start negotiation and check negotiation status

```
curl --location 'https://edc-consumer.dev.simpl-europe.eu/management/v3/contractnegotiations' \
--header 'Content-Type: application/json' \
--data-raw '{
    "@context": {
      "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
    },
    "@type": "ContractRequest",
    "counterPartyAddress": "https://edc-provider.dev.simpl-europe.eu/protocol",
    "protocol": "dataspace-protocol-http",
    "policy": {
        "@context": {
            "@vocab": "https://w3id.org/edc/v0.0.1/ns/",
            "edc": "https://w3id.org/edc/v0.0.1/ns/",
            "odrl": "http://www.w3.org/ns/odrl/2/"
        },
        "@id": "NzA3M2EyZTgtNmRhYS00ZTkwLTgzMTctMmEyMzJmZjYxZDgy:ZDA1YWJiM2MtMTNlYy00YzIxLTg0NjEtMmYxN2VmMTJjYTYw:Mjk3N2ZlNGQtODM2Yy00NzQyLWEyZDQtMzY4NTE3ZTM5YjBk",
        "@type": "odrl:Offer",
        "odrl:permission": {
            "odrl:action": {
                "@id": "odrl:use"
            },
            "odrl:constraint": {
                "odrl:leftOperand": {
                    "@id": "odrl:deletion"
                },
                "odrl:operator": {
                    "@id": "odrl:eq"
                },
                "odrl:rightOperand": "after_use"
            }
        },
        "odrl:assigner": {
            "@id": "provider"
        },
        "odrl:target": {
            "@id": "d05abb3c-13ec-4c21-8461-2f17ef12ca60"
        }
    }
}'
```
policy id need to match with id we fetched in 2.

to check negotiation status

```
curl --location 'https://edc-consumer.dev.simpl-europe.eu/management/v3/contractnegotiations/e9f04287-4147-445a-bf41-ba3d34be1f83'
```
you need to provide your own contract negotiation id that is returned in response in previous call


4. If you have Edc extension for contract manager callback enabled(In helm charts contractmanager.extension.enabled).
then contract negotiation state won't be automatically moved to state `FINALIZED` preventing you to start transfer process
If it's enabled contract manager's url and api key can be configured by changing **contractmanager.url** in values.yaml
and **contractmanager_apikey** in hashicorp Vault

The extension communicates with 2 endpoints in [Contract manager's API](https://code.europa.eu/simpl/simpl-open/development/contract-billing/contract/-/blob/develop/http/openapi3.json):

`POST /contract/v1/credentials/agreements/{contractAgreementId}/definitions/{contractDefinitionId}`

`POST /contract/v1/agreements/{contractAgreementId}/definitions/{contractDefinitionId}/status`

in order to push contract negotiation state to `FINALIZED` following http calls need to be executed

```
curl --location --request POST 'https://edc-consumer.dev.simpl-europe.eu/api/signed/84329eb5-5010-4fbe-bc20-a66ed7330d14/true' \
--header 'Accept: application/json'
```

If the negotiation was signed by Consumer it awaits to be either signed or terminated on Provider's side. This is done by sending the following request to the Provider extension endpoint:

```
curl --location --request POST 'https://edc-provider.dev.simpl-europe.eu/api/signed/b1616f63-4024-48d3-ac03-56b4353f941e/true' \
--header 'Accept: application/json'
```
Please notice that **:contractNegotiationId** would be different would be different on Provider's side (it can be obtained for example from logs).

after this when we again will check contract negotiation status on consumer side we should see state `FINALIZED`

if contractmanager.extension.enabled is disabled in helm charts then contract negotiation state should almost  immediately transit to 
state `FINALIZED` and no additional http calls are needed



5. start transfer process (pass the value from  contractAgreementId from second http call in pt 3. into contractId field)

```
curl --location 'https://edc-consumer.dev.simpl-europe.eu/management/v3/transferprocesses' \
--header 'Content-Type: application/json' \
--data-raw '{
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
    },
    "@type": "TransferRequestDto",
    "connectorId": "provider",
    "counterPartyAddress": "https://edc-provider.dev.simpl-europe.eu/protocol",
    "contractId": "3c1209b7-99e4-4eea-afb3-40ba65a652f3",
    "protocol": "dataspace-protocol-http",
    "transferType": "IonosS3-PUSH",
    "dataDestination": {
        "type": "IonosS3",
        "region": "de",
        "storage": "s3-eu-central-1.ionoscloud.com",
        "bucketName": "simpl-consumer",
        "objectName": "european_health_data.csv",
        "path": "folder/",
        "keyName": "test-key-name"
    }
}'
```

to check status of transfer process 

```
curl --location 'https://edc-consumer.dev.simpl-europe.eu/management/v3/transferprocesses/a58ce4ae-b9c1-423b-8659-e93133b17376'
```

as an additional confirmation that transfer was success we can check date of file european_health_data.csv in S3 bucket by
executing following aws cli command
```
aws s3 ls simpl-consumer --endpoint-url https://s3-eu-central-1.ionoscloud.com --recursive
```

6. Once finished IMPORTANT to do the deprovisioning on consumer side (id -> transfer Id from the response of step 5)
```
curl --location --request POST 'https://edc-consumer.dev.simpl-europe.eu/management/v3/transferprocesses/b580a471-8a95-4ece-8a43-5e5266291852/deprovision' \
--header 'Accept: application/json'
```


##TRANSFER PROCESS (PROVISION CLOUD RESOURCES)

1. create asset

```
curl --location 'https://edc-provider.dev.simpl-europe.eu/management/v3/assets' \
--header 'Content-Type: application/json' \
--data-raw '{
    "@context": {
        "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
    },
    "@id": "asset-671",
    "properties": {
        "name": "Test Infrastructure Asset"
    },
    "dataAddress": {
        "type": "Infrastructure",
        "provisioningAPI": "https://be.infrastructure.dev.simpl-europe.eu/scripts/trigger",
        "deploymentScriptId": "fe31140e-3038-469b-895b-186778ce8ae2"
    }
}'
```

provisioningAPI -> this is url for triggering the deployment. Full api can be seen
https://be.infrastructure.dev.simpl-europe.eu/swagger-ui/index.html#/
At the time being deploymentScriptId": "fe31140e-3038-469b-895b-186778ce8ae2 should be used

2. create policy

```
curl --location 'https://edc-provider.dev.simpl-europe.eu/management/v3/policydefinitions' \
--header 'Content-Type: application/json' \
--data-raw '{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@id": "eu-policy",
  "policy": {
    "@context": "http://www.w3.org/ns/odrl.jsonld",
    "@type": "Set",
    "permission": [
      {
        "action": "use",
        "constraint": {
          "@type": "AtomicConstraint",
          "leftOperand": "location",
          "operator": {
            "@id": "odrl:eq"
          },
          "rightOperand": "eu"
        }
      }
    ],
    "prohibition": [],
    "obligation": []
  }
}'
```

3. create contract definition

```
curl --location 'https://edc-provider.dev.simpl-europe.eu/management/v3/contractdefinitions' \
--header 'Content-Type: application/json' \
--data-raw '{
   "@context": {
   "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
   },
   "@id": "1",
   "accessPolicyId": "eu-policy",
   "contractPolicyId": "eu-policy",
   "assetsSelector": [{
       "@type": "CriterionDto",
       "operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
       "operator": "=",
       "operandRight": "asset-671"
   }]
   }'
```

4. start negotiation

```
curl --location 'https://edc-consumer.dev.simpl-europe.eu/management/v3/contractnegotiations' \
--header 'Content-Type: application/json' \
--data-raw '{
  "@context": {
    "@vocab": "https://w3id.org/edc/v0.0.1/ns/"
  },
  "@type": "ContractRequest",
  "counterPartyAddress": "https://edc-provider.dev.simpl-europe.eu/protocol",
  "protocol": "dataspace-protocol-http",
  "policy": {
    "@context": "http://www.w3.org/ns/odrl.jsonld",
    "@id": "MQ==:YXNzZXQtNjcx:NDM4ODkxNGEtNzAyZi00NDhjLThhMmYtZWYxZDI1NWU0ZjE5",
    "@type": "Offer",
    "permission": [
      {
        "action": "use",
        "target": "asset-671",
        "constraint": {
          "@type": "AtomicConstraint",
          "leftOperand": "location",
          "operator": "eq",
          "rightOperand": "eu"
        }
      }
    ],
    "assigner": "provider",
    "target": "asset-671"
  }
}'
```

id need to be substituted with actual id provided by provider catalogue (check first flow in order to fetch provider catalogue by consumer)

4.1 If you have Edc extension for contract manager callback enabled(In helm charts contractmanager.extension.enabled), next steps are described in previous flow in step 4.


5. Check negotiation status in order to fetch contractAgreementId

```
curl --location 'https://edc-consumer.dev.simpl-europe.eu/management/v3/contractnegotiations/864e554b-5878-4308-89b2-73691ecc3ac7'
```

paste id of your contract negotiation returned in step 4

6. Start transfer

```
curl --location 'https://edc-consumer.dev.simpl-europe.eu/management/v3/transferprocesses' \
--header 'Content-Type: application/json' \
--data-raw '{
    "@context":{
        "edc":"https://w3id.org/edc/v0.0.1/ns/"
    },
    "connectorId":"provider",
    "counterPartyAddress": "https://edc-provider.dev.simpl-europe.eu/protocol",
    "contractId":"3adbf119-7c48-4591-b77c-c1b57755e743",
    "protocol":"dataspace-protocol-http",
    "transferType": "Infrastructure-PUSH",
    "dataDestination":{
        "type":"Infrastructure",
        "consumerEmail": "someuser@xptodomain.com"
    }
}'
```

contractId - id from pt 5

after this we can check provider logs and find entries like:

```
INFO 2024-12-11T11:58:45.479747 [SimplDataPlaneExtension] Script trigger request successful send to provisioningAPI https://be.infrastructure.dev.simpl-europe.eu/scripts/trigger, transferProcessId: e0bd591f-26e9-48cb-b54e-8398dd7b1d9b, deploymentScriptId: fe31140e-3038-469b-895b-186778ce8ae2
DEBUG 2024-12-11T11:58:45.481792 [DataPlaneManagerImpl] DataFlow e0bd591f-26e9-48cb-b54e-8398dd7b1d9b is now in state COMPLETED
DEBUG 2024-12-11T11:58:46.337929 [DataPlaneManagerImpl] DataFlow e0bd591f-26e9-48cb-b54e-8398dd7b1d9b is now in state NOTIFIED
DEBUG 2024-12-11T11:58:46.780283 TransferProcess: ID e0bd591f-26e9-48cb-b54e-8398dd7b1d9b. send transfer completion to http://localhost:29194/protocol
DEBUG 2024-12-11T11:58:46.816646 TransferProcess: ID e0bd591f-26e9-48cb-b54e-8398dd7b1d9b. send transfer completion to http://localhost:29194/protocol
DEBUG 2024-12-11T11:58:46.830356 [TransferProcessManagerImpl] TransferProcess e0bd591f-26e9-48cb-b54e-8398dd7b1d9b is now in state COMPLETED
DEBUG 2024-12-11T11:58:46.84161 [TransferProcessManagerImpl] TransferProcess e0bd591f-26e9-48cb-b54e-8398dd7b1d9b is now in state DEPROVISIONING
DEBUG 2024-12-11T11:58:47.898469 TransferProcess: ID e0bd591f-26e9-48cb-b54e-8398dd7b1d9b. deprovisioning
DEBUG 2024-12-11T11:58:47.905196 [TransferProcessManagerImpl] TransferProcess e0bd591f-26e9-48cb-b54e-8398dd7b1d9b is now in state DEPROVISIONED

```

if we see them, that means that process ended successfully

7. Once finished IMPORTANT to do the deprovisioning on consumer side (id -> transfer Id from the response of step 6)
```
curl --location --request POST 'https://edc-consumer.dev.simpl-europe.eu/management/v3/transferprocesses/b580a471-8a95-4ece-8a43-5e5266291852/deprovision' \
--header 'Accept: application/json'
```

8. How to check if file exists in MinIO bucket
```
mc alias set local https://miniourl.dev ACCESS_KEY SECRET_KEY
mc ls local/consumer-bucket
```

### TRANSFER PROCESS (EDELIVERY EXTENSION)
Please check postman collection for EDelivery for sample requests for all calls. Local running EDC consumer and EDC provider are required to plug and play this collection
1. Create asset, policy and contract definition on provider side.
Asset creation payload is including edelivery api url:
"edeliveryTriggerAPI": "http://localhost:8084/simpl-edelivery-trigger"
If EDelivery is not deployed somewhere then a mock endpoint can be used that simulates EDelivery behaviour.
This mock should provide a successful response like this when called by EDC provider:
{
   "operationId": "7fec24aa-2ea4-4965-9ca6-3b554001c032",
   "status": "success",
   "message": "Request received"
}
2. Make request to query provider catalogue in order to get dcat:dataset.odrl:hasPolicy.@id
3. Start negotiation and check negotiation status
4. Start transfer process (pass the value from  contractAgreementId from second http call in pt 3. into contractId field)
5. Check status of transfer process