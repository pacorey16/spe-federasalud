# Enfoque A — Split Architecture EDC Nativo

**TFM — Master en Ingeniería Informática | Marzo 2026**

---

## Descripción

Implementación del patrón oficial **Split Architecture** de Eclipse EDC para la integración de hospitales en el dataspace SIMPL. El conector EDC se divide en dos mitades desplegadas en entornos físicamente distintos:

| Componente | Ubicación lógica | Responsabilidad |
|---|---|---|
| **EDC Control Plane (Provider)** | Cloud / DMZ | Catálogo, negociación de contratos, políticas ODRL, orquestación |
| **EDC Data Plane** | Hospital (red interna) | Ejecución de transferencias, acceso a datos locales |
| **NGINX Reverse Proxy** | Hospital (perímetro) | Filtro de IP allowlist — solo el cloud puede llamar al Data Plane |
| **EDC Control Plane (Consumer)** | Cloud / investigador | Consulta catálogo, negocia contrato, inicia transferencia |

La comunicación entre el Control Plane en cloud y el Data Plane en el hospital cruza el firewall hospitalario a través de un **único puerto de entrada** protegido por allowlist de IPs.

---

## Estructura de Directorios

```
enfoque-a-split-architecture/
│
├── README.md                          ← este archivo
│
├── edc-provider/
│   ├── control-plane/
│   │   └── config/
│   │       └── provider-config.properties   ← config del Control Plane (cloud)
│   ├── data-plane/
│   │   └── config/
│   │       └── dataplane-config.properties  ← config del Data Plane (hospital)
│   └── nginx/
│       └── nginx.conf                        ← proxy inverso con IP allowlist
│
└── edc-consumer/
    └── control-plane/
        └── config/
            └── consumer-config.properties    ← config del consumidor (investigador)
```

---

## Prerrequisitos

Antes de arrancar este enfoque, verificar que están en ejecución:

```bash
# Desde la raíz del repo
cd infrastructure
docker compose -f docker-compose-common.yml ps
# → provider-db, consumer-db y minio deben estar healthy

# El JAR de simpl-edc-main debe estar compilado
ls -lh ../simpl-edc-main/target/basic-connector.jar
```

---

## Arranque Completo

### Paso 1 — Infraestructura base

```bash
cd infrastructure
docker compose -f docker-compose-common.yml up -d
docker compose -f docker-compose-enfoque-a.yml up -d
```

### Paso 2 — Control Plane del proveedor (en cloud)

```bash
# Desde la raíz del repo
java -jar simpl-edc-main/target/basic-connector.jar \
  -Dedc.fs.config=enfoque-a-split-architecture/edc-provider/control-plane/config/provider-config.properties &

# Health check
sleep 10
curl -s http://localhost:19191/api/check/health | jq .
# Esperado: { "isSystemHealthy": true }
```

### Paso 3 — Data Plane del hospital (detrás de NGINX)

```bash
java -jar simpl-edc-main/target/basic-connector.jar \
  -Dedc.fs.config=enfoque-a-split-architecture/edc-provider/data-plane/config/dataplane-config.properties &

# Verificar que NGINX llega al Data Plane
curl -s http://localhost:39443/api/check/health
# Esperado: { "isSystemHealthy": true }
```

### Paso 4 — Registrar el Data Plane en el Control Plane

```bash
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

### Paso 5 — Control Plane del consumidor (investigador)

```bash
java -jar simpl-edc-main/target/basic-connector.jar \
  -Dedc.fs.config=enfoque-a-split-architecture/edc-consumer/control-plane/config/consumer-config.properties &

sleep 5
curl -s http://localhost:29191/api/check/health | jq .
```

### Paso 6 — Crear activos, políticas y contratos en el proveedor

```bash
# Asset: dataset de cardiología
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
      "secretAccessKey": "minioadmin"
    }
  }' | jq .

# Política: solo rol RESEARCHER
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

# Definición de contrato
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

### Paso 7 — Prueba de transferencia completa

```bash
chmod +x scripts/enfoque-a/test-full-transfer.sh
./scripts/enfoque-a/test-full-transfer.sh
```

---

## Puertos en Uso

| Proceso | Puerto | Descripción |
|---|---|---|
| Control Plane Provider | 19191 | API / health |
| Control Plane Provider | 19192 | Control (registro Data Plane) |
| Control Plane Provider | 19193 | Management API |
| Control Plane Provider | 19194 | DSP Protocol |
| Control Plane Provider | 19291 | Public (Data Plane público) |
| Data Plane Hospital | 39192 | API interna |
| Data Plane Hospital | 39193 | Control (recibe órdenes del CP) |
| Data Plane Hospital | 39291 | Public (acceso a datos) |
| NGINX Hospital | 39443 | **Puerto de entrada** (IP allowlist) |
| Control Plane Consumer | 29191 | API / health |
| Control Plane Consumer | 29192 | Control |
| Control Plane Consumer | 29193 | Management API |
| Control Plane Consumer | 29194 | DSP Protocol |
| Control Plane Consumer | 29291 | Public |

---

## Criterios de Validación

- [ ] `curl localhost:19191/api/check/health` → `isSystemHealthy: true`
- [ ] `curl localhost:29191/api/check/health` → `isSystemHealthy: true`
- [ ] `curl localhost:39443/api/check/health` → responde (a través de NGINX)
- [ ] `curl -H "X-Forwarded-For: 1.2.3.4" localhost:39443/public` → `403 Forbidden`
- [ ] `GET /management/v3/assets` devuelve `hospital-dataset-cardio-001`
- [ ] Tras ejecutar el script de prueba: el archivo aparece en el bucket `consumer-results` de MinIO (`http://localhost:9001`)
- [ ] Logs de NGINX: `cloud_authorized=1` para las peticiones del Control Plane

---

## Modelo de Seguridad

```
[Investigador] → [Consumer Control Plane] ──DSP/HTTP──► [Provider Control Plane]
                                                                    │
                                                          Orden de transferencia
                                                                    │
                                                                    ▼
                                            [NGINX :39443] ← IP allowlist filtra
                                                    │           (solo IPs cloud)
                                                    ▼
                                            [Data Plane :39291]
                                                    │
                                            Accede a datos locales
                                                    │
                                                    ▼
                                            [MinIO S3 consumer-results]
```

**Debilidad inherente:** El hospital debe abrir el puerto `39443` hacia internet. El filtrado por IP es configurable pero dependiente de que las IPs del cloud sean estáticas.

---

## Relación con los Microservicios SIMPL

| Microservicio | Rol en este enfoque |
|---|---|
| `simpl-edc-main` | Binario único que corre como Control Plane O Data Plane según la config |
| `edcconnectoradapter-main` | **No usado** — se llama directamente a la Management API de EDC |
| `kafka-main` | **No usado** — este enfoque no usa mensajería asíncrona |
| `postgres-cluster-main` | **No usado en local** — se usa postgres:15-alpine en Docker |

---

*Enfoque A — TFM Marzo 2026*
