# Kafka

## Description
This project contains the configuration files required for Kafka deployed using Helm. 
It's using Kraft controllers instead of Zookeepers. 

## Pre-Requisites

Ensure you have the following tools installed before starting the deployment process:
- Git
- Helm
- Kubectl

Additionally, ensure you have access to a Kubernetes cluster where ArgoCD is installed.

The following versions of the elements will be used in the process:

| Pre-Requisites         |     Version     | Description                                                                                                                                     |
| ---------------------- |     :-----:     | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| DNS sub-domain name    |       N/A       | This domain will be used to address all services of the agent. <br/> example: `*.common.int.simpl-europe.eu`   |  
| Kubernetes Cluster     | 1.29.x or newer | Other version *might* work but tests were performed using 1.29.x version   |
| Hashicorp Vault        | 1.18.x or newer | Other version *might* work but tests were performed using 1.18.1 version. <br/> Image used: `hashicorp/vault:1.18.1`    |

Vault is needed if you want to use SASL authentication. 

## Installation

Modify the values file for your preference and deploy as an usual Helm chart. 

Mentionable values:

| Variable name                 |     Example         | Description     |
| ----------------------        |     :-----:         | --------------- |
| kafka.replicas                | 3     | number of replicas  |
| kafka.resources               | - | resources for kafka replicas - standard syntax of requests and limits |
| kafka.topic.replicas          | 2 | number of topic replicas  |
| kafka.topic.insyncreplicas    | 1 | number of in sync replicas |
| kafka.topic.autocreate        | true | enables autocreation of topics |
| kafka.auth.enabled            | true | enables enables SASL plaintext authentication (users are defined in vault secret) |
| kafka.clusterLink             | false | enable or disable cluster link feature |
| kafka.balancer                | false | enable or disable balancer feature |
| kraftController.replicas      | 3 | number of replicas of kraft controllers |
| kraftController.resources     | - | resources for kraft controllers - standard syntax of requests and limits |
| hashicorp.service             | http://vault.commonns.domainsuffix | link to vault ingress
| hashicorp.role                | accessrole_name | name of role for vault access |
| hashicorp.secretEngine        | name | secret engine name in vault |

If kafka.auth.enabled is true - it enables SASL plaintext authentication with users defined in vault secret. <br/>
All the mentioned hashicorp branch values should be populated. <br/>
Secret named *namespace*-kafka-credentials will be used. 