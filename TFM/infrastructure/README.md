# Infraestructura Local — Docker Compose

**TFM — Master en Ingeniería Informática | Marzo 2026**

---

## Descripción

Este directorio contiene los archivos Docker Compose para levantar la infraestructura de soporte del PoC. Están separados en tres ficheros para permitir levantar solo lo necesario según el enfoque a probar.

| Archivo | Propósito | Uso |
|---|---|---|
| `docker-compose-common.yml` | Infraestructura base compartida | **Siempre necesario** |
| `docker-compose-enfoque-a.yml` | NGINX proxy hospital | Solo para Enfoque A |
| `docker-compose-enfoque-b.yml` | Kafka + ZooKeeper + UI | Solo para Enfoque B |

---

## docker-compose-common.yml

### Servicios incluidos

#### provider-db (PostgreSQL para el proveedor)
- **Imagen:** `postgres:15-alpine`
- **Puerto:** `5432:5432`
- **Usuario/Password:** `edc` / `edc_password`
- **Base de datos:** `postgres`
- **Función:** Almacena el estado del conector proveedor (activos, contratos, políticas, negociaciones, transferencias)

#### consumer-db (PostgreSQL para el consumidor)
- **Imagen:** `postgres:15-alpine`
- **Puerto:** `5433:5432` ← atención al mapeo (5433 en el host → 5432 en el contenedor)
- **Usuario/Password:** `edc` / `edc_password`
- **Base de datos:** `postgres`
- **Función:** Almacena el estado del conector consumidor

#### minio (Almacenamiento S3 compatible)
- **Imagen:** `minio/minio:latest`
- **Puerto API:** `9000:9000`
- **Puerto Consola:** `9001:9001`
- **Credenciales:** `minioadmin` / `minioadmin`
- **Consola web:** http://localhost:9001
- **Buckets creados:** `provider-data` (datos del hospital) y `consumer-results` (resultados)

---

## docker-compose-enfoque-a.yml

### Servicios incluidos

#### nginx-hospital (Proxy inverso con IP allowlist)
- **Imagen:** `nginx:1.25-alpine`
- **Puerto:** `39443:39443`
- **Config:** `../enfoque-a-split-architecture/edc-provider/nginx/nginx.conf`
- **Función:** Filtra el tráfico entrante al Data Plane del hospital por IP de origen

> El Data Plane del hospital se ejecuta como proceso Java externo (no en Docker), por lo que NGINX apunta a `host.docker.internal:39291`.

---

## docker-compose-enfoque-b.yml

### Servicios incluidos

#### zookeeper
- **Imagen:** `confluentinc/cp-zookeeper:7.5.0`
- **Puerto interno:** `2181`
- **Función:** Coordinación del broker Kafka en entorno local
- **Nota:** Solo para local. En producción se usa KRaft sin Zookeeper (ver `kafka-main/` Helm chart con cp-server:8.0.1)

#### kafka
- **Imagen:** `confluentinc/cp-kafka:7.5.0`
- **Puerto externo:** `9094` (para acceso desde el host / Minikube)
- **Puerto interno:** `9092` (para comunicación entre contenedores Docker)
- **Función:** Broker de mensajes — el cloud publica órdenes de ejecución aquí
- **Topic a crear:** `spe-execution-requests`

#### redpanda-console (Kafka UI)
- **Imagen:** `docker.redpanda.com/redpandadata/console:latest`
- **Puerto:** `8080:8080`
- **URL:** http://localhost:8080
- **Función:** Interfaz web para inspeccionar topics, mensajes y offsets en local

---

## Comandos de Uso

### Levantar todo para Enfoque A

```bash
cd infrastructure

# Infraestructura base
docker compose -f docker-compose-common.yml up -d

# NGINX (Enfoque A)
docker compose -f docker-compose-enfoque-a.yml up -d

# Crear buckets MinIO (esperar ~5s a que minio esté healthy)
docker run --rm --network host minio/mc alias set local http://localhost:9000 minioadmin minioadmin
docker run --rm --network host minio/mc mb local/provider-data --ignore-existing
docker run --rm --network host minio/mc mb local/consumer-results --ignore-existing
```

### Levantar todo para Enfoque B

```bash
cd infrastructure

# Infraestructura base
docker compose -f docker-compose-common.yml up -d

# Kafka (Enfoque B)
docker compose -f docker-compose-enfoque-b.yml up -d

# Crear buckets MinIO
docker run --rm --network host minio/mc alias set local http://localhost:9000 minioadmin minioadmin
docker run --rm --network host minio/mc mb local/provider-data --ignore-existing
docker run --rm --network host minio/mc mb local/consumer-results --ignore-existing

# Crear topic Kafka
docker exec simpl-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --topic spe-execution-requests \
  --partitions 3 \
  --replication-factor 1
```

### Ver estado

```bash
docker compose -f docker-compose-common.yml ps
docker compose -f docker-compose-enfoque-a.yml ps   # si aplica
docker compose -f docker-compose-enfoque-b.yml ps   # si aplica
```

### Parar todo

```bash
docker compose -f docker-compose-enfoque-b.yml down
docker compose -f docker-compose-enfoque-a.yml down
docker compose -f docker-compose-common.yml down

# Para borrar también los volúmenes (reset completo)
docker compose -f docker-compose-common.yml down -v
```

---

## Credenciales y Endpoints Rápidos

| Servicio | URL | Credenciales |
|---|---|---|
| MinIO API | http://localhost:9000 | minioadmin / minioadmin |
| MinIO Consola | http://localhost:9001 | minioadmin / minioadmin |
| PostgreSQL Provider | localhost:5432 | edc / edc_password |
| PostgreSQL Consumer | localhost:5433 | edc / edc_password |
| Kafka (host) | localhost:9094 | — (PLAINTEXT en local) |
| Kafka UI (Redpanda) | http://localhost:8080 | — |

---

*Infraestructura local — TFM Marzo 2026*
