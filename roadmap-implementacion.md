# Roadmap de Implementación — Integración Hospitalaria en Dataspace SIMPL

**TFM — Master en Ingeniería Informática**
**Fecha:** Marzo 2026

---

## Visión General del Roadmap

Este documento describe los pasos detallados para implementar ambos enfoques arquitectónicos en local: el **Enfoque A (Split Architecture EDC Nativo)** y el **Enfoque B (Kafka + Kubernetes SPE)**. Cada fase incluye prerrequisitos, tareas técnicas concretas y criterios de validación.

> **Principio de diseño local:** Se elimina toda dependencia no estrictamente necesaria para las pruebas funcionales. Keycloak, HashiCorp Vault, tenant-services y contract-manager **no se usan en local** porque el proyecto `simpl-edc-main` ya incluye un mock de identidad (`SimplIdentityService`) que genera tokens JWT con roles hardcodeados, y los contratos los gestiona EDC internamente sin callback externo.

```
FASE 0  →  FASE 1  →  FASE 2  →  FASE 3  →  FASE 4  →  FASE 5
Entorno     EDC Base   Enfoque A  Enfoque B  Integración  Hardening
Local       Común      (Split)    (Kafka+K8s) y Tests     Producción
```

---

## Stack Mínimo por Enfoque

### Enfoque A — Split Architecture
```
postgres          → estado de EDC (contratos, negociaciones, transferencias)
minio             → bucket de destino de los datos transferidos
edc-provider      → Control Plane + Data Plane (simula el hospital proveedor)
edc-consumer      → Control Plane + Data Plane (simula el investigador consumidor)
nginx             → proxy inverso con IP allowlist (simula el firewall hospitalario)
```

### Enfoque B — Kafka + SPE
```
postgres          → estado de EDC de gobernanza
minio             → bucket de resultados del algoritmo
kafka             → broker de órdenes de ejecución
edc-governance    → Control Plane (solo gobernanza, sin Data Plane)
spe-connector     → desplegado en Minikube (Kafka consumer + K8s sandbox)
```

### Servicios que NO se usan en local y por qué

| Servicio | Motivo |
|---|---|
| Keycloak | `SimplIdentityService` genera tokens JWT mockeados con roles base64 |
| HashiCorp Vault | Secretos en `config.properties` directamente (suficiente para PoC) |
| tenant-services | Los participantes se configuran manualmente en `config.properties` |
| contract-manager | EDC gestiona contratos internamente; el callback URL es opcional (`simpl.contract.manager.url`) |
| ELK Stack | Logs de consola y stdout de Docker suficientes para depuración |
| TLS Gateway / Smart Gateway | Acceso directo por puerto localhost, sin TLS en local |
| EJBCA | Certificados autofirmados o sin TLS en local |

---

## FASE 0 — Preparación del Entorno Local

**Objetivo:** Tener todas las herramientas y servicios de infraestructura listos antes de tocar código.

### 0.1 Herramientas de Desarrollo

| Herramienta | Versión Mínima | Comando de verificación |
|---|---|---|
| Java JDK | 17 | `java -version` |
| Maven | 3.9.x | `mvn -version` |
| Docker Desktop | 24.x | `docker --version` |
| kubectl | 1.28+ | `kubectl version --client` |
| Minikube | 1.32+ | `minikube version` |
| Git | 2.x | `git --version` |
| curl / jq | cualquiera | `curl --version && jq --version` |

```bash
# Instalar en macOS con Homebrew
brew install openjdk@17 maven kubectl minikube jq

# Configurar JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
source ~/.zshrc
```

### 0.2 Cluster Minikube (solo para Enfoque B)

```bash
minikube start \
  --profile simpl-local \
  --cpus 4 \
  --memory 8192 \
  --disk-size 40g \
  --driver docker \
  --kubernetes-version v1.28.0

minikube addons enable metrics-server --profile simpl-local

# Verificar
kubectl get nodes --context=simpl-local
```

### 0.3 Infraestructura Base — Docker Compose Común

Archivo: `infrastructure/docker-compose-common.yml`

```yaml
version: '3.8'

services:

  postgres:
    image: postgres:15-alpine
    container_name: simpl-postgres
    environment:
      POSTGRES_USER: edc
      POSTGRES_PASSWORD: edc_password
      POSTGRES_MULTIPLE_DATABASES: edc_provider,edc_consumer
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./common/postgres/init-multiple-dbs.sh:/docker-entrypoint-initdb.d/init.sh
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U edc"]
      interval: 5s
      timeout: 5s
      retries: 5

  minio:
    image: minio/minio:latest
    container_name: simpl-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin123
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 10s
      timeout: 5s
      retries: 3

volumes:
  postgres_data:
  minio_data:
```

```bash
# Levantar infraestructura común
cd infrastructure
docker compose -f docker-compose-common.yml up -d

# Crear bases de datos separadas para provider y consumer
docker exec simpl-postgres psql -U edc -c "CREATE DATABASE edc_provider;"
docker exec simpl-postgres psql -U edc -c "CREATE DATABASE edc_consumer;"

# Crear buckets en MinIO
docker run --rm --network host minio/mc \
  alias set local http://localhost:9000 minioadmin minioadmin123

docker run --rm --network host minio/mc mb local/provider-data
docker run --rm --network host minio/mc mb local/consumer-results

# Verificar
docker compose -f docker-compose-common.yml ps
```

**Criterio de validación de Fase 0:**
- `docker compose ps` → todos `healthy`
- `psql -h localhost -U edc -d edc_provider -c "\l"` → lista las dos bases de datos
- `http://localhost:9001` → consola MinIO accesible (minioadmin / minioadmin123)
- `minikube status --profile simpl-local` → `Running` (si se va a usar Enfoque B)

---

## FASE 1 — Base Común: Compilar y Configurar SIMPL-EDC

**Objetivo:** Compilar el proyecto `simpl-edc-main` y tener las configuraciones base para proveedor y consumidor. Este jar se reutiliza en ambos enfoques.

### 1.1 Compilar simpl-edc-main

```bash
cd simpl-edc-main
mvn clean package -DskipTests

# Verificar que el jar se ha generado
ls -lh target/basic-connector.jar
```

### 1.2 Entender el Mock de Identidad (sin Keycloak)

El proyecto usa `SimplIdentityService` que genera tokens de identidad a partir de un valor base64 hardcodeado en configuración. Los roles disponibles son:

```
CONSUMER      → investigador que consulta catálogos y negocia contratos
RESEARCHER    → rol con acceso a datos clínicos (restricción ODRL)
SD_PUBLISHER  → publica Self-Descriptions en el catálogo
SD_CONSUMER   → consume Self-Descriptions
```

No se necesita ningún servidor de autenticación externo. La configuración en `config.properties` simplemente activa este mock:

```properties
# Identidad mockeada — no requiere Keycloak
edc.iam.sts.oauth.token.url=mock
edc.iam.issuer.id=did:web:provider
```

### 1.3 Configuración del Proveedor (Hospital)

Archivo: `enfoque-a-split-architecture/edc-provider/control-plane/config/provider-config.properties`

```properties
# Identidad
edc.participant.id=simpl-hospital-provider
edc.connector.name=Hospital Provider Connector

# Puertos
web.http.port=19191
web.http.path=/api
web.http.management.port=19193
web.http.management.path=/management
web.http.protocol.port=19194
web.http.protocol.path=/protocol
web.http.public.port=19291
web.http.public.path=/public
web.http.control.port=19192
web.http.control.path=/control

# Protocolo DSP
edc.dsp.callback.address=http://localhost:19194/protocol

# Base de datos (sin Vault — directo en properties para local)
edc.datasource.default.url=jdbc:postgresql://localhost:5432/edc_provider
edc.datasource.default.user=edc
edc.datasource.default.password=edc_password

# Autenticación API (X-Api-Key — sin Vault)
edc.api.auth.key=provider-local-api-key

# MinIO S3
edc.minio.endpoint=http://localhost:9000
edc.minio.access.key=minioadmin
edc.minio.secret.key=minioadmin123

# Contract Manager — NO configurado: EDC gestiona contratos internamente
# simpl.contract.manager.url=   <-- comentado, no usar en local
```

### 1.4 Configuración del Consumidor (Investigador)

Archivo: `enfoque-a-split-architecture/edc-consumer/control-plane/config/consumer-config.properties`

```properties
edc.participant.id=simpl-researcher-consumer
edc.connector.name=Researcher Consumer Connector

web.http.port=29191
web.http.path=/api
web.http.management.port=29193
web.http.management.path=/management
web.http.protocol.port=29194
web.http.protocol.path=/protocol
web.http.public.port=29291
web.http.public.path=/public
web.http.control.port=29192
web.http.control.path=/control

edc.dsp.callback.address=http://localhost:29194/protocol

edc.datasource.default.url=jdbc:postgresql://localhost:5432/edc_consumer
edc.datasource.default.user=edc
edc.datasource.default.password=edc_password

edc.api.auth.key=consumer-local-api-key

edc.minio.endpoint=http://localhost:9000
edc.minio.access.key=minioadmin
edc.minio.secret.key=minioadmin123
```

### 1.5 Arrancar Ambos Conectores y Crear Activos/Políticas

```bash
# Arrancar el proveedor
java -jar simpl-edc-main/target/basic-connector.jar \
  -Dedc.fs.config=enfoque-a-split-architecture/edc-provider/control-plane/config/provider-config.properties &

# Arrancar el consumidor
java -jar simpl-edc-main/target/basic-connector.jar \
  -Dedc.fs.config=enfoque-a-split-architecture/edc-consumer/control-plane/config/consumer-config.properties &

# Esperar arranque (~10s)
sleep 10

# Verificar health
curl http://localhost:19191/api/check/health
curl http://localhost:29191/api/check/health
```

```bash
# Crear Asset en el proveedor
curl -s -X POST "http://localhost:19193/management/v3/assets" \
  -H "X-Api-Key: provider-local-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "@context": {"@vocab": "https://w3id.org/edc/v0.0.1/ns/"},
    "@id": "hospital-dataset-cardio-001",
    "properties": {
      "name": "Dataset Cardiología — H. La Paz",
      "contenttype": "application/json",
      "description": "Dataset anonimizado pacientes cardiacos 2024"
    },
    "dataAddress": {
      "type": "AmazonS3",
      "bucketName": "provider-data",
      "keyName": "cardio/dataset-001.json",
      "endpointOverride": "http://localhost:9000",
      "accessKeyId": "minioadmin",
      "secretAccessKey": "minioadmin123"
    }
  }' | jq .

# Crear Política de Acceso (rol RESEARCHER requerido)
curl -s -X POST "http://localhost:19193/management/v3/policydefinitions" \
  -H "X-Api-Key: provider-local-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "@context": {"@vocab": "https://w3id.org/edc/v0.0.1/ns/"},
    "@id": "policy-researcher-only",
    "policy": {
      "@type": "Set",
      "permission": [{
        "action": "use",
        "constraint": {
          "leftOperand": "CONSUMPTION_ROLE",
          "operator": "eq",
          "rightOperand": "RESEARCHER"
        }
      }]
    }
  }' | jq .

# Crear Definición de Contrato
curl -s -X POST "http://localhost:19193/management/v3/contractdefinitions" \
  -H "X-Api-Key: provider-local-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "@context": {"@vocab": "https://w3id.org/edc/v0.0.1/ns/"},
    "@id": "contract-def-cardio",
    "accessPolicyId": "policy-researcher-only",
    "contractPolicyId": "policy-researcher-only",
    "assetsSelector": [{
      "@type": "CriterionDto",
      "operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
      "operator": "=",
      "operandRight": "hospital-dataset-cardio-001"
    }]
  }' | jq .
```

**Criterio de validación de Fase 1:**
- `GET /management/v3/assets` → devuelve el asset creado
- `GET /management/v3/contractdefinitions` → devuelve la definición
- Ambos conectores responden en `/api/check/health` con `{ "isSystemHealthy": true }`

---

## FASE 2 — Enfoque A: Split Architecture EDC Nativo

**Objetivo:** Separar el Data Plane del proveedor y desplegarlo en el "hospital" con NGINX como proxy de entrada con allowlist de IPs. Simula que el Control Plane está en cloud y el Data Plane en el hospital.

### 2.1 Configuración del Data Plane Hospital

Archivo: `enfoque-a-split-architecture/edc-provider/data-plane/config/dataplane-config.properties`

```properties
edc.participant.id=simpl-hospital-dataplane
edc.connector.name=Hospital Data Plane

# El Data Plane solo expone los puertos de datos y control
web.http.port=39192
web.http.path=/api
web.http.public.port=39291
web.http.public.path=/public
web.http.control.port=39193
web.http.control.path=/control

# URL de validación de tokens — apunta al Control Plane
edc.dataplane.token.validation.endpoint=http://localhost:19192/control/token

# S3
edc.s3.endpoint=http://localhost:9000
edc.s3.access.key=minioadmin
edc.s3.secret.key=minioadmin123
```

### 2.2 Configuración NGINX con Allowlist de IPs

Archivo: `enfoque-a-split-architecture/edc-provider/nginx/nginx.conf`

```nginx
events { worker_connections 1024; }

http {
    # IPs autorizadas del cloud (Control Plane)
    geo $allowed_cloud_ip {
        default       0;
        127.0.0.1     1;    # localhost — desarrollo
        172.17.0.0/16 1;    # red interna Docker
        # PRODUCCION: añadir IPs reales del cloud SIMPL
    }

    log_format audit '$remote_addr [$time_local] "$request" $status '
                     'cloud_authorized=$allowed_cloud_ip';

    upstream hospital_dataplane {
        server localhost:39291;
    }

    server {
        listen 39443;

        access_log /var/log/nginx/access.log audit;

        location / {
            if ($allowed_cloud_ip = 0) {
                return 403 '{"error":"IP not authorized","hint":"Add IP to allowlist"}';
            }
            proxy_pass       http://hospital_dataplane;
            proxy_set_header Host            $host;
            proxy_set_header X-Real-IP       $remote_addr;
            proxy_read_timeout  300s;
            proxy_send_timeout  300s;
        }

        # Health check solo desde localhost
        location /health {
            allow 127.0.0.1;
            deny  all;
            proxy_pass http://hospital_dataplane/api/check/health;
        }
    }
}
```

### 2.3 Docker Compose del Enfoque A

Archivo: `infrastructure/docker-compose-enfoque-a.yml`

```yaml
version: '3.8'

services:

  nginx-hospital:
    image: nginx:1.25-alpine
    container_name: simpl-nginx-hospital
    ports:
      - "39443:39443"
    volumes:
      - ../enfoque-a-split-architecture/edc-provider/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
    extra_hosts:
      - "host.docker.internal:host-gateway"

  # Data Plane del hospital — arrancado como proceso Java externo
  # Ver instrucciones en paso 2.4
```

### 2.4 Arranque Completo del Enfoque A

```bash
# 1. Infraestructura común
cd infrastructure
docker compose -f docker-compose-common.yml up -d

# 2. NGINX
docker compose -f docker-compose-enfoque-a.yml up -d

# 3. Control Plane proveedor (cloud)
java -jar simpl-edc-main/target/basic-connector.jar \
  -Dedc.fs.config=enfoque-a-split-architecture/edc-provider/control-plane/config/provider-config.properties &

# 4. Data Plane hospital (expuesto detrás de NGINX)
java -jar simpl-edc-main/target/basic-connector.jar \
  -Dedc.fs.config=enfoque-a-split-architecture/edc-provider/data-plane/config/dataplane-config.properties &

# 5. Control Plane consumidor (cloud)
java -jar simpl-edc-main/target/basic-connector.jar \
  -Dedc.fs.config=enfoque-a-split-architecture/edc-consumer/control-plane/config/consumer-config.properties &

sleep 10

# 6. Registrar el Data Plane en el Control Plane
curl -s -X POST "http://localhost:19192/control/v1/dataplanes" \
  -H "X-Api-Key: provider-local-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "@context": {"@vocab": "https://w3id.org/edc/v0.0.1/ns/"},
    "@id": "hospital-dataplane-01",
    "url": "http://localhost:39193/control",
    "allowedSourceTypes": ["AmazonS3"],
    "allowedDestTypes": ["AmazonS3"],
    "properties": {
      "publicApiUrl": "http://localhost:39443/public"
    }
  }' | jq .
```

### 2.5 Prueba de Transferencia Completa (Enfoque A)

```bash
#!/bin/bash
# scripts/enfoque-a/test-full-transfer.sh

CONSUMER="http://localhost:29193/management"
KEY="consumer-local-api-key"
PROVIDER_DSP="http://localhost:19194/protocol"

echo "=== [1/4] Consultar catálogo del proveedor ==="
CATALOG=$(curl -s -X POST "$CONSUMER/v3/catalog/request" \
  -H "X-Api-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d "{\"counterPartyAddress\":\"$PROVIDER_DSP\",\"@type\":\"CatalogRequest\"}")

OFFER_ID=$(echo $CATALOG | jq -r '.["dcat:dataset"][0]["odrl:hasPolicy"][0]["@id"]')
echo "Offer ID: $OFFER_ID"

echo "=== [2/4] Negociar contrato ==="
NEG_ID=$(curl -s -X POST "$CONSUMER/v3/contractnegotiations" \
  -H "X-Api-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"counterPartyAddress\":\"$PROVIDER_DSP\",
    \"protocol\":\"dataspace-protocol-http\",
    \"policy\":{\"@id\":\"$OFFER_ID\",\"@type\":\"Offer\",\"assigner\":\"simpl-hospital-provider\"}
  }" | jq -r '."@id"')
echo "Negotiation ID: $NEG_ID"

echo "=== [3/4] Esperar FINALIZED ==="
for i in {1..30}; do
  STATE=$(curl -s "$CONSUMER/v3/contractnegotiations/$NEG_ID" \
    -H "X-Api-Key: $KEY" | jq -r '.state')
  echo "  Estado: $STATE"
  [[ "$STATE" == "FINALIZED" ]] && break
  sleep 2
done

CONTRACT_ID=$(curl -s "$CONSUMER/v3/contractnegotiations/$NEG_ID" \
  -H "X-Api-Key: $KEY" | jq -r '.contractAgreementId')

echo "=== [4/4] Iniciar transferencia S3 ==="
curl -s -X POST "$CONSUMER/v3/transferprocesses" \
  -H "X-Api-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"counterPartyAddress\":\"$PROVIDER_DSP\",
    \"protocol\":\"dataspace-protocol-http\",
    \"contractId\":\"$CONTRACT_ID\",
    \"transferType\":\"AmazonS3-PUSH\",
    \"dataDestination\":{
      \"type\":\"AmazonS3\",
      \"bucketName\":\"consumer-results\",
      \"keyName\":\"cardio-dataset-001.json\",
      \"endpointOverride\":\"http://localhost:9000\",
      \"accessKeyId\":\"minioadmin\",
      \"secretAccessKey\":\"minioadmin123\"
    }
  }" | jq .

echo ""
echo "=== Verificar resultado en MinIO ==="
echo "Abrir: http://localhost:9001 → bucket consumer-results"
```

**Criterio de validación de Fase 2:**
- El Data Plane responde a través de NGINX en `localhost:39443`
- Una petición con IP no autorizada recibe `403`
- El archivo aparece en el bucket `consumer-results` de MinIO tras la transferencia
- Los logs de NGINX muestran `cloud_authorized=1` para las peticiones del Control Plane

---

## FASE 3 — Enfoque B: Kafka + Kubernetes SPE

**Objetivo:** Desplegar el SPE-Connector en Minikube y ejecutar algoritmos sobre datos del hospital de forma aislada. El cloud publica órdenes en Kafka; el hospital las consume sin exponer ningún puerto.

### 3.1 Infraestructura Kafka

Archivo: `infrastructure/docker-compose-enfoque-b.yml`

```yaml
version: '3.8'

services:

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: simpl-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: simpl-kafka
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
      - "9094:9094"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,PLAINTEXT_HOST://localhost:9094
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
    healthcheck:
      test: ["CMD", "kafka-topics", "--bootstrap-server", "localhost:9092", "--list"]
      interval: 10s
      timeout: 10s
      retries: 5

  # UI para inspeccionar topics Kafka en local
  redpanda-console:
    image: docker.redpanda.com/redpandadata/console:latest
    container_name: simpl-kafka-ui
    ports:
      - "8080:8080"
    environment:
      KAFKA_BROKERS: kafka:9092
    depends_on:
      - kafka
```

```bash
# Levantar Kafka + UI
cd infrastructure
docker compose -f docker-compose-common.yml up -d
docker compose -f docker-compose-enfoque-b.yml up -d

# Crear topic
docker exec simpl-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --topic spe-execution-requests \
  --partitions 3 \
  --replication-factor 1

# Verificar topic creado
docker exec simpl-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic spe-execution-requests

# UI Kafka accesible en http://localhost:8080
```

### 3.2 Compilar el SPE-Connector

```bash
cd spe-federasalud
mvn clean package -DskipTests

ls -lh spe-launcher/target/spe-launcher-1.0.0-SNAPSHOT.jar
```

### 3.3 Construir y Publicar la Imagen Docker en Minikube

```bash
# Apuntar Docker al daemon de Minikube
eval $(minikube docker-env --profile simpl-local)

# Construir imagen dentro del contexto de Minikube
cd spe-federasalud
docker build -t simpl/spe-connector:latest .

# Verificar que la imagen está disponible en Minikube
minikube image ls --profile simpl-local | grep spe-connector
```

### 3.4 Desplegar en Minikube

```bash
# Namespace del hospital
kubectl create namespace simpl-spe --context simpl-local

# RBAC — ServiceAccount + ClusterRole + Binding
kubectl apply -f spe-federasalud/k8s/deployment.yaml --context simpl-local

# Datos de prueba del hospital: PersistentVolume
kubectl apply --context simpl-local -f - <<EOF
apiVersion: v1
kind: PersistentVolume
metadata:
  name: hospital-data-pv
spec:
  capacity:
    storage: 1Gi
  accessModes: [ReadOnlyMany]
  hostPath:
    path: /tmp/hospital-test-data
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pvc-hospital-data
  namespace: simpl-spe
spec:
  accessModes: [ReadOnlyMany]
  resources:
    requests:
      storage: 1Gi
EOF

# Cargar datos de prueba en el hostPath de Minikube
minikube ssh --profile simpl-local "mkdir -p /tmp/hospital-test-data && \
  echo '[{\"patient_id\":\"P001\",\"age\":65,\"diagnosis\":\"HTA\"},{\"patient_id\":\"P002\",\"age\":72,\"diagnosis\":\"ICC\"}]' \
  > /tmp/hospital-test-data/patients.json"

# Configurar la URL del Kafka (host.minikube.internal resuelve al host desde Minikube)
kubectl set env deployment/spe-connector \
  SPE_KAFKA_BOOTSTRAP_SERVERS=host.minikube.internal:9094 \
  -n simpl-spe --context simpl-local

# Verificar pods
kubectl get pods -n simpl-spe --context simpl-local
kubectl logs -f deployment/spe-connector -n simpl-spe --context simpl-local
```

### 3.5 Prueba End-to-End del Enfoque B

```bash
#!/bin/bash
# scripts/enfoque-b/test-spe-execution.sh

EXECUTION_ID="demo-$(date +%s)"

echo "=== Publicando orden de ejecución en Kafka ==="
echo "{
  \"execution_id\": \"$EXECUTION_ID\",
  \"algorithm_image\": \"python:3.11-slim\",
  \"command\": [\"python\", \"-c\",
    \"import json, os; \
     data=json.load(open('/data/input/patients.json')); \
     result={'total':len(data),'avg_age':sum(p['age'] for p in data)/len(data)}; \
     open('/tmp/result.json','w').write(json.dumps(result))\"],
  \"dataset_id\": \"pvc-hospital-data\",
  \"output_config\": {
    \"endpoint\": \"http://host.minikube.internal:9000\",
    \"bucket\": \"consumer-results\",
    \"access_key\": \"minioadmin\",
    \"secret_key\": \"minioadmin123\"
  }
}" | docker exec -i simpl-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic spe-execution-requests \
  --property "parse.key=true" \
  --property "key.separator=|" <<< "$EXECUTION_ID|$(cat)"

echo ""
echo "=== Observando namespace efímero en Minikube ==="
echo "Ejecutar en otra terminal:"
echo "  watch kubectl get namespaces --context simpl-local | grep spe-exec"
echo ""
echo "=== Log del SPE Connector ==="
kubectl logs -f deployment/spe-connector -n simpl-spe --context simpl-local &

echo ""
echo "=== Verificar resultado en MinIO (tras ~30s) ==="
echo "  http://localhost:9001 → bucket consumer-results → buscar $EXECUTION_ID"
```

### 3.6 Verificaciones de Seguridad

```bash
# Verificar que el namespace efímero se crea y destruye
kubectl get namespaces --watch --context simpl-local | grep spe-exec

# Verificar NetworkPolicy del Job
kubectl get networkpolicy spe-isolation \
  -n spe-exec-$EXECUTION_ID --context simpl-local -o yaml

# Verificar que no hay puertos de entrada expuestos en simpl-spe
kubectl get services -n simpl-spe --context simpl-local
# Resultado esperado: "No resources found" (el SPE no expone ningún servicio)

# Verificar que el PVC se montó como readOnly
kubectl get pod -n spe-exec-$EXECUTION_ID --context simpl-local \
  -o jsonpath='{.items[0].spec.containers[0].volumeMounts}' | jq .
```

**Criterio de validación de Fase 3:**
- El log del SPE muestra `[SPE] Kafka listener started`
- Al publicar la orden, aparece `[SPE-K8S] Creating namespace: spe-exec-...`
- El namespace `spe-exec-{id}` aparece y desaparece en ~60s
- `kubectl get services -n simpl-spe` → vacío (cero puertos expuestos)
- El resultado del algoritmo aparece en el bucket `consumer-results` de MinIO

---

## FASE 4 — Integración y Métricas Comparativas

**Objetivo:** Ejecutar ambos enfoques y registrar las métricas que justifican la elección del Enfoque B en el TFM.

### 4.1 Script de Métricas Comparativas

```bash
#!/bin/bash
# scripts/comparativa-metricas.sh

echo "======================================"
echo "  MÉTRICA 1: Puertos expuestos"
echo "======================================"
echo "--- Enfoque A (con Data Plane + NGINX) ---"
ss -tlnp | grep -E "39443|39291|39192"
echo ""
echo "--- Enfoque B (SPE en Minikube) ---"
kubectl get services -n simpl-spe --context simpl-local
echo "  (esperado: ningún servicio expuesto)"

echo ""
echo "======================================"
echo "  MÉTRICA 2: Datos del paciente en S3"
echo "======================================"
echo "--- Bucket provider-data (datos originales del hospital) ---"
docker run --rm --network host minio/mc ls local/provider-data/
echo ""
echo "--- Bucket consumer-results (lo que llega al cloud) ---"
docker run --rm --network host minio/mc ls local/consumer-results/
echo "  Enfoque A: aparecen los DATOS ORIGINALES"
echo "  Enfoque B: aparecen solo los RESULTADOS del algoritmo"

echo ""
echo "======================================"
echo "  MÉTRICA 3: Latencia de transferencia"
echo "======================================"
echo "Medir con: time ./scripts/enfoque-a/test-full-transfer.sh"
echo "      vs:  time ./scripts/enfoque-b/test-spe-execution.sh"
```

### 4.2 Tabla de Resultados (rellenar tras las pruebas)

| Métrica | Enfoque A (Split) | Enfoque B (Kafka+SPE) |
|---|---|---|
| Puertos TCP expuestos en hospital | `39443` (NGINX) | Ninguno |
| Datos del paciente en S3 externo | Sí (archivo completo) | No (solo resultados) |
| Latencia total (orden → datos) | ___ ms | ___ ms |
| Namespaces K8s efímeros creados | N/A | 1 por ejecución |
| Reglas de firewall nuevas necesarias | 1 (puerto entrada) | 0 |
| Aprobación TI simulada | Requiere justificación | Sin cambios red |

---

## FASE 5 — Hardening para Producción (Documentación)

Esta fase documenta las mejoras necesarias para llevar cada enfoque a un entorno hospitalario real. No se implementan en el PoC pero se incluyen en el TFM como trabajo futuro.

### 5.1 Hardening del Enfoque A

| Mejora | Descripción | Impacto |
|---|---|---|
| mTLS entre Control Plane y Data Plane | Certificados mutuos, no solo filtro de IP | Elimina spoofing de IP |
| Gestión dinámica de allowlist | Script que actualiza NGINX si cambian IPs del cloud | Evita downtime por cambio de IP |
| Renovación automática de certificados | cert-manager en K8s | Elimina expiración manual |
| HashiCorp Vault | Mover secretos (S3 keys, API keys) fuera de properties | Cumplimiento secretos en producción |
| Rate limiting en NGINX | `limit_req_zone` para evitar abuso | Protección DoS |
| PostgreSQL HA | Galera Cluster o RDS | Eliminar SPOF de base de datos |

### 5.2 Hardening del Enfoque B

| Mejora | Descripción | Prioridad |
|---|---|---|
| Kafka SASL_SSL | Reemplazar `PLAINTEXT` por autenticación mTLS | Crítica |
| K8s Secrets para credenciales S3 | El mensaje Kafka lleva un `secret_ref`, no las credenciales | Crítica |
| Registry privado para imágenes | Solo ejecutar imágenes firmadas de un registry autenticado | Alta |
| OPA/Gatekeeper para imágenes | Validar digest de imagen antes de ejecutar el Job | Alta |
| Egress con IP fija de S3 | NetworkPolicy con `ipBlock` específico, no rango abierto | Alta |
| Falco para detección de anomalías | Alertas si un Job intenta conexiones no permitidas | Media |
| HashiCorp Vault | Secretos del SPE gestionados por Vault Agent Injector | Media |
| HL7/FHIR adapter | Soporte para datasets en formato clínico estándar | Media |
| Multi-consumer por hospital | Particiones Kafka dedicadas por hospital para escala horizontal | Baja |

---

## Resumen de Fases y Entregables

| Fase | Nombre | Entregable Principal |
|---|---|---|
| **0** | Entorno Local | `docker-compose-common.yml` funcional + Minikube operativo |
| **1** | EDC Base Común | Conectores EDC arrancados + activos/políticas/contratos creados |
| **2** | Enfoque A: Split Architecture | Transferencia S3 funcional con NGINX allowlist |
| **3** | Enfoque B: Kafka + SPE | Jobs K8s efímeros ejecutados + resultados en MinIO |
| **4** | Métricas Comparativas | Tabla de resultados con datos reales de las pruebas |
| **5** | Hardening | Checklists y descripción para producción (trabajo futuro) |

---

## Diagrama de Dependencias entre Fases

```
Fase 0 — Entorno Local
    │
    └──► Fase 1 — EDC Base Común
              │
              ├──► Fase 2 — Enfoque A (Split Architecture)  ──┐
              │                                                 │
              └──► Fase 3 — Enfoque B (Kafka + SPE)  ─────────┤
                                                               │
                                                    Fase 4 — Métricas Comparativas
                                                               │
                                                    Fase 5 — Hardening Producción
```

> Las Fases 2 y 3 son completamente independientes entre sí y pueden desarrollarse en paralelo una vez completada la Fase 1.

---

*Documento generado en el contexto del TFM — Marzo 2026*
