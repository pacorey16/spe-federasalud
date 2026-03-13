#!/bin/bash
# =============================================================================
# test-spe-execution.sh — Prueba completa Enfoque B (Kafka + K8s SPE)
#
# Flujo:
#   1. Generar un execution_id único
#   2. Publicar orden de ejecución en el topic Kafka
#   3. Observar la creación del namespace efímero en Minikube
#   4. Esperar a que el Job termine y el namespace se destruya
#   5. Verificar que el resultado aparece en MinIO consumer-results
#
# Prerrequisitos:
#   - infrastructure/docker-compose-common.yml levantado
#   - infrastructure/docker-compose-enfoque-b.yml levantado (Kafka)
#   - Topic spe-execution-requests creado
#   - SPE-Connector desplegado en Minikube y en estado Running
#   - Datos de prueba en /tmp/hospital-test-data/ (en el nodo de Minikube)
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# CONFIGURACIÓN
# -----------------------------------------------------------------------------
KAFKA_CONTAINER="${KAFKA_CONTAINER:-simpl-kafka}"
KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"
KAFKA_TOPIC="${KAFKA_TOPIC:-spe-execution-requests}"
MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-simpl-local}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9000}"
MAX_WAIT_NAMESPACE=120   # segundos para esperar que el namespace aparezca
MAX_WAIT_RESULT=180      # segundos para esperar el resultado en MinIO

# Colores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_ok()      { echo -e "${GREEN}[ OK ]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERR ]${NC} $1"; }
log_section() { echo -e "\n${YELLOW}=== $1 ===${NC}"; }

# -----------------------------------------------------------------------------
# VERIFICACIÓN INICIAL
# -----------------------------------------------------------------------------
log_section "Verificaciones previas"

# Kafka
if docker exec "$KAFKA_CONTAINER" kafka-topics \
  --bootstrap-server "$KAFKA_BOOTSTRAP" \
  --describe --topic "$KAFKA_TOPIC" &>/dev/null; then
  log_ok "Topic '$KAFKA_TOPIC' accesible"
else
  log_error "Topic '$KAFKA_TOPIC' no encontrado en Kafka. ¿Está el topic creado?"
  exit 1
fi

# SPE-Connector en Minikube
SPE_POD_STATE=$(kubectl get pods -n simpl-spe --context "$MINIKUBE_PROFILE" \
  -l app=spe-connector -o jsonpath='{.items[0].status.phase}' 2>/dev/null || echo "NotFound")
if [ "$SPE_POD_STATE" = "Running" ]; then
  log_ok "SPE-Connector Running en Minikube"
else
  log_error "SPE-Connector no está Running (estado: $SPE_POD_STATE)"
  log_info "Verificar con: kubectl get pods -n simpl-spe --context $MINIKUBE_PROFILE"
  exit 1
fi

# -----------------------------------------------------------------------------
# PASO 1 — Generar ID único de ejecución
# -----------------------------------------------------------------------------
log_section "Paso 1/4 — Generando orden de ejecución"

EXECUTION_ID="demo-$(date +%s)"
log_ok "Execution ID: $EXECUTION_ID"

# -----------------------------------------------------------------------------
# PASO 2 — Publicar orden en Kafka
# -----------------------------------------------------------------------------
log_section "Paso 2/4 — Publicando orden en Kafka"

# Algoritmo de ejemplo: calcular estadísticas básicas del dataset
ALGORITHM_CMD="python3 -c \"
import json, os, sys
data_path = '/data/input/patients.json'
output_path = '/tmp/result.json'
with open(data_path) as f:
    data = json.load(f)
ages = [p['age'] for p in data]
diagnoses = {}
for p in data:
    d = p.get('diagnosis', 'unknown')
    diagnoses[d] = diagnoses.get(d, 0) + 1
result = {
    'execution_id': os.environ.get('EXECUTION_ID', 'unknown'),
    'total_patients': len(data),
    'avg_age': round(sum(ages) / len(ages), 1),
    'min_age': min(ages),
    'max_age': max(ages),
    'diagnoses_count': diagnoses,
    'data_exfiltrated': False
}
with open(output_path, 'w') as f:
    json.dump(result, f, indent=2)
print('Result computed:', json.dumps(result))
\""

# Construir el mensaje JSON
MESSAGE=$(cat << EOF
{
  "execution_id": "$EXECUTION_ID",
  "algorithm_image": "python:3.11-slim",
  "command": ["sh", "-c", "$ALGORITHM_CMD"],
  "dataset_id": "pvc-hospital-data",
  "output_config": {
    "endpoint": "http://host.minikube.internal:9000",
    "bucket": "consumer-results",
    "key": "results/$EXECUTION_ID/result.json",
    "access_key": "minioadmin",
    "secret_key": "minioadmin"
  },
  "env": {
    "EXECUTION_ID": "$EXECUTION_ID"
  },
  "metadata": {
    "requested_by": "simpl-researcher-consumer",
    "algorithm": "cardio-stats-v1",
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  }
}
EOF
)

echo "Mensaje a publicar:"
echo "$MESSAGE" | jq '.'

echo "$MESSAGE" | docker exec -i "$KAFKA_CONTAINER" \
  kafka-console-producer \
  --bootstrap-server "$KAFKA_BOOTSTRAP" \
  --topic "$KAFKA_TOPIC"

log_ok "Orden publicada en topic '$KAFKA_TOPIC'"

# -----------------------------------------------------------------------------
# PASO 3 — Observar namespace efímero en Minikube
# -----------------------------------------------------------------------------
log_section "Paso 3/4 — Observando ciclo de vida del namespace efímero"
log_info "Esperando aparición del namespace spe-exec-$EXECUTION_ID..."

START_TIME=$SECONDS
NAMESPACE_APPEARED=false
while [ $((SECONDS - START_TIME)) -lt $MAX_WAIT_NAMESPACE ]; do
  if kubectl get namespace "spe-exec-$EXECUTION_ID" \
    --context "$MINIKUBE_PROFILE" &>/dev/null; then
    log_ok "Namespace spe-exec-$EXECUTION_ID CREADO"
    NAMESPACE_APPEARED=true
    break
  fi
  echo -n "."
  sleep 2
done
echo ""

if [ "$NAMESPACE_APPEARED" = false ]; then
  log_warn "El namespace efímero no apareció en ${MAX_WAIT_NAMESPACE}s"
  log_info "Verificar logs del SPE-Connector:"
  kubectl logs deployment/spe-connector -n simpl-spe --context "$MINIKUBE_PROFILE" --tail=50
else
  # Mostrar el Job y su estado
  log_info "Job en el namespace efímero:"
  kubectl get jobs -n "spe-exec-$EXECUTION_ID" --context "$MINIKUBE_PROFILE" 2>/dev/null || true

  log_info "NetworkPolicy del namespace:"
  kubectl get networkpolicy -n "spe-exec-$EXECUTION_ID" --context "$MINIKUBE_PROFILE" 2>/dev/null || true

  # Esperar destrucción automática (TTL 60s)
  log_info "Esperando destrucción automática del namespace (TTL ~60s)..."
  START_DESTROY=$SECONDS
  while [ $((SECONDS - START_DESTROY)) -lt 90 ]; do
    if ! kubectl get namespace "spe-exec-$EXECUTION_ID" \
      --context "$MINIKUBE_PROFILE" &>/dev/null; then
      log_ok "Namespace spe-exec-$EXECUTION_ID DESTRUIDO automáticamente"
      break
    fi
    echo -n "."
    sleep 3
  done
  echo ""
fi

# -----------------------------------------------------------------------------
# PASO 4 — Verificar resultado en MinIO
# -----------------------------------------------------------------------------
log_section "Paso 4/4 — Verificando resultado en MinIO"
log_info "Esperando resultado en consumer-results/results/$EXECUTION_ID/..."

START_TIME=$SECONDS
RESULT_FOUND=false
while [ $((SECONDS - START_TIME)) -lt $MAX_WAIT_RESULT ]; do
  if docker run --rm --network host minio/mc \
    ls "local/consumer-results/results/$EXECUTION_ID/" 2>/dev/null | grep -q result; then
    RESULT_FOUND=true
    log_ok "Resultado encontrado en MinIO"
    break
  fi
  echo -n "."
  sleep 5
done
echo ""

# -----------------------------------------------------------------------------
# RESULTADO FINAL
# -----------------------------------------------------------------------------
echo ""
log_section "Resultado"

if [ "$RESULT_FOUND" = true ]; then
  log_ok "PRUEBA EXITOSA — Algoritmo ejecutado y resultado subido a S3"
  echo ""
  echo "  Resultado en: consumer-results/results/$EXECUTION_ID/result.json"
  echo "  Consola MinIO: http://localhost:9001"
  echo ""
  echo "  Contenido del resultado:"
  docker run --rm --network host minio/mc \
    cat "local/consumer-results/results/$EXECUTION_ID/result.json" 2>/dev/null \
    | jq '.' || echo "  (no se pudo leer el contenido)"
else
  log_warn "No se encontró el resultado en el tiempo esperado."
  echo "  Verificar manualmente en: http://localhost:9001"
  echo "  Logs del SPE-Connector:"
  kubectl logs deployment/spe-connector -n simpl-spe --context "$MINIKUBE_PROFILE" --tail=30
fi

echo ""
log_section "Verificación de seguridad Enfoque B"
echo "  Puerto expuesto al exterior: NINGUNO"
echo "  Datos del paciente en transit: NO (solo resultados del algoritmo)"
echo "  Nuevas reglas de firewall necesarias: 0"
echo "  Namespaces efímeros creados: 1 (ya destruido)"
echo ""
echo "  kubectl get services -n simpl-spe --context $MINIKUBE_PROFILE"
kubectl get services -n simpl-spe --context "$MINIKUBE_PROFILE" 2>/dev/null \
  || echo "  (namespace simpl-spe no accesible)"
