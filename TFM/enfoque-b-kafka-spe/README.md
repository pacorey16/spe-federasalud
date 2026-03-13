# Enfoque B — SPE-Federasalud: Kafka + Kubernetes Sandbox

**TFM — Master en Ingeniería Informática | Marzo 2026**

---

## Descripción

Implementación del patrón **outbound-only** para la integración hospitalaria en SIMPL. El hospital **nunca expone puertos a internet**. En su lugar:

1. El hospital despliega el **SPE-Connector** en su cluster Kubernetes interno.
2. El SPE-Connector actúa como consumidor Kafka: escucha permanentemente órdenes del cloud.
3. Cuando llega una orden, crea un **namespace Kubernetes efímero** con el algoritmo de análisis.
4. El algoritmo accede a los datos locales (PVC `readOnly`) y sube **solo los resultados** a S3.
5. El namespace se destruye automáticamente (TTL 60s).

**Resultado:** Los datos del paciente nunca salen del hospital. Solo viajan los resultados.

---

## Estructura de Directorios

```
enfoque-b-kafka-spe/
│
├── README.md                     ← este archivo
│
└── k8s/
    ├── namespace.yaml            ← namespace simpl-spe
    ├── rbac.yaml                 ← ServiceAccount + ClusterRole + Binding
    ├── deployment.yaml           ← Deployment del SPE-Connector
    ├── configmap.yaml            ← Configuración del SPE-Connector
    └── network-policy.yaml       ← NetworkPolicy de aislamiento
```

> El código fuente del SPE-Connector está en la raíz del repositorio (es el proyecto Maven principal `spe-federasalud/`).

---

## Prerrequisitos

### Software necesario
```bash
# Verificar versiones mínimas
minikube version          # >= 1.32
kubectl version --client  # >= 1.28
java -version             # >= 17
mvn -version              # >= 3.9

# Docker debe estar en ejecución
docker info | grep "Server Version"
```

### Infraestructura base levantada
```bash
cd infrastructure
docker compose -f docker-compose-common.yml up -d
docker compose -f docker-compose-enfoque-b.yml up -d

# Verificar que Kafka está healthy
docker exec simpl-kafka kafka-topics \
  --bootstrap-server localhost:9092 --list
```

---

## Arranque Completo

### Fase 1 — Preparar Minikube

```bash
# Crear cluster (solo la primera vez)
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
# NAME           STATUS   ROLES           AGE   VERSION
# simpl-local    Ready    control-plane   Xs    v1.28.x
```

### Fase 2 — Compilar el SPE-Connector

```bash
# Desde la raíz del repositorio spe-federasalud/
mvn clean package -DskipTests

# Verificar el JAR generado
ls -lh spe-launcher/target/spe-launcher-1.0.0-SNAPSHOT.jar
```

### Fase 3 — Construir imagen Docker en Minikube

```bash
# Apuntar el daemon Docker al de Minikube
eval $(minikube docker-env --profile simpl-local)

# Construir la imagen (Dockerfile en raíz del repo)
docker build -t simpl/spe-connector:latest .

# Verificar que la imagen está disponible en Minikube
minikube image ls --profile simpl-local | grep spe-connector
# simpl/spe-connector:latest
```

### Fase 4 — Desplegar en Minikube

```bash
# Aplicar todos los manifests en orden
kubectl apply -f enfoque-b-kafka-spe/k8s/namespace.yaml --context simpl-local
kubectl apply -f enfoque-b-kafka-spe/k8s/rbac.yaml --context simpl-local
kubectl apply -f enfoque-b-kafka-spe/k8s/configmap.yaml --context simpl-local
kubectl apply -f enfoque-b-kafka-spe/k8s/deployment.yaml --context simpl-local
kubectl apply -f enfoque-b-kafka-spe/k8s/network-policy.yaml --context simpl-local

# Configurar la dirección Kafka (host.minikube.internal resuelve al host macOS)
kubectl set env deployment/spe-connector \
  SPE_KAFKA_BOOTSTRAP_SERVERS=host.minikube.internal:9094 \
  -n simpl-spe --context simpl-local

# Verificar pods
kubectl get pods -n simpl-spe --context simpl-local
# NAME                             READY   STATUS    RESTARTS
# spe-connector-xxxxxxxxx-xxxxx    1/1     Running   0

# Ver logs en tiempo real
kubectl logs -f deployment/spe-connector -n simpl-spe --context simpl-local
# Esperado: [SPE] Kafka listener started on topic: spe-execution-requests
```

### Fase 5 — Cargar datos de prueba del hospital

```bash
# Crear directorio de datos en el nodo de Minikube
minikube ssh --profile simpl-local "mkdir -p /tmp/hospital-test-data"

# Cargar dataset de prueba (pacientes cardiológicos anonimizados)
minikube ssh --profile simpl-local "cat > /tmp/hospital-test-data/patients.json << 'EOF'
[
  {\"patient_id\":\"P001\",\"age\":65,\"diagnosis\":\"HTA\",\"systolic_bp\":145,\"diastolic_bp\":92},
  {\"patient_id\":\"P002\",\"age\":72,\"diagnosis\":\"ICC\",\"systolic_bp\":130,\"diastolic_bp\":85},
  {\"patient_id\":\"P003\",\"age\":58,\"diagnosis\":\"HTA\",\"systolic_bp\":155,\"diastolic_bp\":98},
  {\"patient_id\":\"P004\",\"age\":69,\"diagnosis\":\"FA\",\"systolic_bp\":120,\"diastolic_bp\":78},
  {\"patient_id\":\"P005\",\"age\":81,\"diagnosis\":\"ICC\",\"systolic_bp\":138,\"diastolic_bp\":88}
]
EOF"

# Crear PersistentVolume con los datos del hospital
kubectl apply --context simpl-local -f - <<'EOF'
apiVersion: v1
kind: PersistentVolume
metadata:
  name: hospital-data-pv
spec:
  capacity:
    storage: 1Gi
  accessModes:
    - ReadOnlyMany
  persistentVolumeReclaimPolicy: Retain
  hostPath:
    path: /tmp/hospital-test-data
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: pvc-hospital-data
  namespace: simpl-spe
spec:
  accessModes:
    - ReadOnlyMany
  resources:
    requests:
      storage: 1Gi
EOF
```

### Fase 6 — Prueba end-to-end

```bash
chmod +x scripts/enfoque-b/test-spe-execution.sh
./scripts/enfoque-b/test-spe-execution.sh
```

---

## Modelo de Seguridad por Capas

```
Capa 1 — Red:       Cero puertos de entrada al hospital.
                    El hospital conecta OUTBOUND al broker Kafka.
                    El firewall hospitalario NO necesita nuevas reglas de entrada.

Capa 2 — Datos:     PVC montado en modo readOnly en el Job.
                    Los datos NUNCA salen del hospital — solo los resultados.

Capa 3 — Ejecución: Namespace efímero (spe-exec-{id}) destruido tras 60s.
                    NetworkPolicy: deny ALL ingress + deny ALL egress salvo DNS y S3.

Capa 4 — Container: securityContext:
                      runAsNonRoot: true
                      readOnlyRootFilesystem: true
                      capabilities: drop [ALL]

Capa 5 — Recursos:  ResourceQuota: 4 cores / 4GB RAM máx por namespace efímero.
                    Evita que un algoritmo comprometa el nodo (DoS interno).

Capa 6 — Auditoría: Labels en namespace con execution_id, algorithm, timestamp.
                    Kubernetes Events: trazabilidad completa del ciclo de vida.
```

---

## Diagrama de Flujo

```
[Cloud SIMPL-EDC]
    │
    │ Publica mensaje en Kafka topic: spe-execution-requests
    │ { execution_id, algorithm_image, command, dataset_id, output_config }
    ▼
[Kafka Broker] ──────────────────────────────────────────────────────────────
                                                                              │
                                        ┌─────────────────────────────────── │ ──┐
                                        │           HOSPITAL                  │   │
                                        │                                     │   │
                                        │  [SPE-Connector Pod]                │   │
                                        │    (Kafka Consumer)   ◄─────────────┘   │
                                        │         │                               │
                                        │         │ 1. Recibe orden               │
                                        │         ▼                               │
                                        │  [K8s API Server]                       │
                                        │         │                               │
                                        │         │ 2. Crea namespace efímero     │
                                        │         ▼                               │
                                        │  [spe-exec-{id} namespace]              │
                                        │    ┌─────────────────────┐              │
                                        │    │     Job K8s         │              │
                                        │    │  NetworkPolicy:     │              │
                                        │    │   deny all ingress  │              │
                                        │    │   egress: DNS + S3  │              │
                                        │    │                     │              │
                                        │    │  Volume mounts:     │              │
                                        │    │   /data ← PVC       │              │
                                        │    │   (readOnly)        │              │
                                        │    └──────────┬──────────┘              │
                                        │               │ 3. Ejecuta algoritmo    │
                                        │               │    sobre datos locales  │
                                        │               │ 4. Sube RESULTADO a S3  │
                                        │               ▼                         │
                                        │  [Destrucción automática TTL 60s]       │
                                        │                                         │
                                        └─────────────────────────────────────────┘
                                                        │
                                                        │ Solo resultados (no datos)
                                                        ▼
                                        [MinIO S3 consumer-results]
```

---

## Verificaciones de Seguridad

```bash
# 1. Confirmar que el SPE-Connector NO expone ningún servicio
kubectl get services -n simpl-spe --context simpl-local
# Resultado esperado: "No resources found in simpl-spe namespace."

# 2. Verificar NetworkPolicy del namespace efímero (tras enviar una orden)
kubectl get networkpolicy -n spe-exec-XXXXX --context simpl-local

# 3. Verificar que el PVC se montó readOnly en el Job
kubectl get pod -n spe-exec-XXXXX --context simpl-local \
  -o jsonpath='{.items[0].spec.containers[0].volumeMounts}' | jq .

# 4. Verificar que el namespace se destruye automáticamente
kubectl get namespaces --watch --context simpl-local | grep spe-exec
# Debe aparecer y desaparecer en ~60 segundos
```

---

## Relación con los Microservicios SIMPL

| Microservicio | Rol en este enfoque |
|---|---|
| `simpl-edc-main` | Capa de gobernanza en cloud: gestiona contratos y políticas ODRL. Publica órdenes en Kafka tras la negociación. |
| `edcconnectoradapter-main` | **No usado en el PoC** — adaptador para aplicaciones cliente. |
| `kafka-main` | Helm chart para desplegar Kafka en producción (KRaft, cp-server:8.0.1). En local se usa docker-compose con 7.5.0. |
| `postgres-cluster-main` | **No usado en local** — Helm chart para PostgreSQL en producción con Zalando operator. |

---

## Topics Kafka

| Topic | Particiones | Replication | Descripción |
|---|---|---|---|
| `spe-execution-requests` | 3 | 1 (local) | Órdenes de ejecución del cloud hacia el hospital |

### Esquema del mensaje

```json
{
  "execution_id": "demo-1703123456",
  "algorithm_image": "python:3.11-slim",
  "command": ["python", "-c", "...código del algoritmo..."],
  "dataset_id": "pvc-hospital-data",
  "output_config": {
    "endpoint": "http://host.minikube.internal:9000",
    "bucket": "consumer-results",
    "access_key": "minioadmin",
    "secret_key": "minioadmin"
  }
}
```

---

## Criterios de Validación

- [ ] `kubectl get pods -n simpl-spe` → `1/1 Running`
- [ ] Logs del SPE-Connector muestran `[SPE] Kafka listener started`
- [ ] `kubectl get services -n simpl-spe` → vacío (cero puertos expuestos)
- [ ] Tras enviar orden: namespace `spe-exec-{id}` aparece en `kubectl get namespaces`
- [ ] Namespace `spe-exec-{id}` desaparece ~60s después
- [ ] Resultado del algoritmo aparece en MinIO `consumer-results` (http://localhost:9001)
- [ ] Los datos originales de `patients.json` NO aparecen en S3 (solo el resultado)

---

*Enfoque B — SPE-Federasalud — TFM Marzo 2026*
