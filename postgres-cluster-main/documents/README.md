# PostgreSQL Cluster

## Description
This project contains the configuration files required for PostgreSQL Cluster deployment using Helm, preconfigured for use with SIMPL project.

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

## Installation

Modify the values file for your preference and deploy as an usual Helm chart. 

Mentionable values:

| Variable name                 |     Example | Description     |
| ----------------------        |     :-----: | --------------- |
| name                          | pg-cluster  | name of the cluster  |
| volumeSize                    | 20Gi        | volume sizes  |
| instances                     | 3           | number of postgres instances |
| maxConnections                | 500         | max number of connections to db |
| authorityList                 | below the table | list of agents to create the databases |
| consumerList                  | below the table | list of agents to create the databases |
| providerList                  | below the table | list of agents to create the databases |
| resources                     | - | resources for vault replicas - standard syntax of requests and limits |

Example of agent lists:

  authorityList:
  - authority1
  consumerList:
  - consumer01
  providerList:
  - dataprovider01