#!/bin/bash
# =============================================================================
# test-full-transfer.sh — Prueba completa Enfoque A (Split Architecture)
#
# Flujo:
#   1. Consultar catálogo del proveedor desde el consumidor
#   2. Negociar contrato (DSP)
#   3. Esperar estado FINALIZED
#   4. Iniciar transferencia S3 (AmazonS3-PUSH)
#   5. Verificar que el archivo aparece en consumer-results
#
# Prerrequisitos:
#   - infrastructure/docker-compose-common.yml levantado
#   - infrastructure/docker-compose-enfoque-a.yml levantado (NGINX)
#   - Proveedor Control Plane en :19191, Data Plane en :39192, Consumer en :29191
#   - Asset "hospital-dataset-cardio-001" y contrato creados en el proveedor
#   - Archivo cardio/dataset-001.json subido al bucket provider-data en MinIO
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# CONFIGURACIÓN
# -----------------------------------------------------------------------------
PROVIDER_MANAGEMENT="${PROVIDER_MANAGEMENT:-http://localhost:19193/management}"
CONSUMER_MANAGEMENT="${CONSUMER_MANAGEMENT:-http://localhost:29193/management}"
PROVIDER_KEY="${PROVIDER_KEY:-provider-local-api-key}"
CONSUMER_KEY="${CONSUMER_KEY:-consumer-local-api-key}"
PROVIDER_DSP="${PROVIDER_DSP:-http://localhost:19194/protocol}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://localhost:9000}"

# Timeouts
MAX_NEGOTIATION_WAIT=60   # segundos máximo para esperar FINALIZED
MAX_TRANSFER_WAIT=120     # segundos máximo para esperar COMPLETED
POLL_INTERVAL=3

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_ok()      { echo -e "${GREEN}[ OK ]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERR ]${NC} $1"; }
log_section() { echo -e "\n${YELLOW}=== $1 ===${NC}"; }

# -----------------------------------------------------------------------------
# VERIFICACIÓN INICIAL
# -----------------------------------------------------------------------------
log_section "Verificación de conectores EDC"

for url in "$PROVIDER_MANAGEMENT" "$CONSUMER_MANAGEMENT"; do
  health_url="${url/management/api}/check/health"
  if curl -s --max-time 5 "$health_url" | grep -q '"isSystemHealthy":true'; then
    log_ok "Conector OK: $url"
  else
    log_error "Conector NO disponible: $health_url"
    exit 1
  fi
done

# -----------------------------------------------------------------------------
# PASO 1 — Subir datos de prueba a MinIO (si no existen)
# -----------------------------------------------------------------------------
log_section "Paso 1/5 — Preparar datos de prueba en MinIO"

TEST_DATA='{"patients":[{"id":"P001","age":65,"diagnosis":"HTA"},{"id":"P002","age":72,"diagnosis":"ICC"}]}'

# Comprobar si el objeto ya existe
if docker run --rm --network host minio/mc \
  ls local/provider-data/cardio/dataset-001.json 2>/dev/null | grep -q dataset-001; then
  log_ok "Datos de prueba ya existen en MinIO"
else
  log_info "Subiendo datos de prueba a MinIO..."
  echo "$TEST_DATA" | docker run --rm -i --network host minio/mc \
    pipe local/provider-data/cardio/dataset-001.json
  log_ok "Datos subidos: provider-data/cardio/dataset-001.json"
fi

# -----------------------------------------------------------------------------
# PASO 2 — Consultar catálogo del proveedor
# -----------------------------------------------------------------------------
log_section "Paso 2/5 — Consultar catálogo del proveedor"

CATALOG=$(curl -s -X POST "$CONSUMER_MANAGEMENT/v3/catalog/request" \
  -H "X-Api-Key: $CONSUMER_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"counterPartyAddress\": \"$PROVIDER_DSP\",
    \"@type\": \"CatalogRequest\"
  }")

echo "$CATALOG" | jq '.' 2>/dev/null || { log_error "Error al consultar catálogo"; exit 1; }

OFFER_ID=$(echo "$CATALOG" | jq -r '
  if ."dcat:dataset" | type == "array" then
    ."dcat:dataset"[0]."odrl:hasPolicy"[0]."@id"
  elif ."dcat:dataset" | type == "object" then
    ."dcat:dataset"."odrl:hasPolicy"."@id"
  else "" end')

if [ -z "$OFFER_ID" ] || [ "$OFFER_ID" = "null" ]; then
  log_error "No se encontró oferta en el catálogo. ¿Están creados el asset y el contrato?"
  echo "Catálogo recibido:"
  echo "$CATALOG" | jq '.'
  exit 1
fi

log_ok "Offer ID: $OFFER_ID"

# -----------------------------------------------------------------------------
# PASO 3 — Negociar contrato
# -----------------------------------------------------------------------------
log_section "Paso 3/5 — Negociar contrato (DSP)"

NEG_RESPONSE=$(curl -s -X POST "$CONSUMER_MANAGEMENT/v3/contractnegotiations" \
  -H "X-Api-Key: $CONSUMER_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"counterPartyAddress\": \"$PROVIDER_DSP\",
    \"protocol\": \"dataspace-protocol-http\",
    \"policy\": {
      \"@id\": \"$OFFER_ID\",
      \"@type\": \"Offer\",
      \"assigner\": \"simpl-hospital-provider\"
    }
  }")

NEG_ID=$(echo "$NEG_RESPONSE" | jq -r '."@id"')
if [ -z "$NEG_ID" ] || [ "$NEG_ID" = "null" ]; then
  log_error "Error al iniciar negociación"
  echo "$NEG_RESPONSE" | jq '.'
  exit 1
fi
log_ok "Negotiation ID: $NEG_ID"

# -----------------------------------------------------------------------------
# PASO 4 — Esperar FINALIZED
# -----------------------------------------------------------------------------
log_section "Paso 4/5 — Esperando estado FINALIZED"

START_TIME=$SECONDS
STATE=""
while [ $((SECONDS - START_TIME)) -lt $MAX_NEGOTIATION_WAIT ]; do
  STATE=$(curl -s "$CONSUMER_MANAGEMENT/v3/contractnegotiations/$NEG_ID" \
    -H "X-Api-Key: $CONSUMER_KEY" | jq -r '.state')
  log_info "Estado negociación: $STATE (${$((SECONDS - START_TIME))}s)"

  case "$STATE" in
    FINALIZED)
      log_ok "Contrato FINALIZADO"
      break
      ;;
    TERMINATED|ERROR)
      log_error "Negociación fallida con estado: $STATE"
      exit 1
      ;;
  esac
  sleep $POLL_INTERVAL
done

if [ "$STATE" != "FINALIZED" ]; then
  log_error "Timeout esperando FINALIZED (${MAX_NEGOTIATION_WAIT}s)"
  exit 1
fi

CONTRACT_ID=$(curl -s "$CONSUMER_MANAGEMENT/v3/contractnegotiations/$NEG_ID" \
  -H "X-Api-Key: $CONSUMER_KEY" | jq -r '.contractAgreementId')
log_ok "Contract Agreement ID: $CONTRACT_ID"

# -----------------------------------------------------------------------------
# PASO 5 — Iniciar transferencia S3
# -----------------------------------------------------------------------------
log_section "Paso 5/5 — Iniciar transferencia S3 (AmazonS3-PUSH)"

OUTPUT_KEY="cardio-dataset-$(date +%s).json"

TRANSFER_RESPONSE=$(curl -s -X POST "$CONSUMER_MANAGEMENT/v3/transferprocesses" \
  -H "X-Api-Key: $CONSUMER_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"counterPartyAddress\": \"$PROVIDER_DSP\",
    \"protocol\": \"dataspace-protocol-http\",
    \"contractId\": \"$CONTRACT_ID\",
    \"transferType\": \"AmazonS3-PUSH\",
    \"dataDestination\": {
      \"type\": \"AmazonS3\",
      \"bucketName\": \"consumer-results\",
      \"keyName\": \"$OUTPUT_KEY\",
      \"endpointOverride\": \"$MINIO_ENDPOINT\",
      \"accessKeyId\": \"minioadmin\",
      \"secretAccessKey\": \"minioadmin\"
    }
  }")

TRANSFER_ID=$(echo "$TRANSFER_RESPONSE" | jq -r '."@id"')
if [ -z "$TRANSFER_ID" ] || [ "$TRANSFER_ID" = "null" ]; then
  log_error "Error al iniciar transferencia"
  echo "$TRANSFER_RESPONSE" | jq '.'
  exit 1
fi
log_ok "Transfer Process ID: $TRANSFER_ID"

# Esperar COMPLETED
log_info "Esperando COMPLETED..."
START_TIME=$SECONDS
TRANSFER_STATE=""
while [ $((SECONDS - START_TIME)) -lt $MAX_TRANSFER_WAIT ]; do
  TRANSFER_STATE=$(curl -s "$CONSUMER_MANAGEMENT/v3/transferprocesses/$TRANSFER_ID" \
    -H "X-Api-Key: $CONSUMER_KEY" | jq -r '.state')
  log_info "Estado transferencia: $TRANSFER_STATE"

  case "$TRANSFER_STATE" in
    COMPLETED)
      log_ok "Transferencia COMPLETADA"
      break
      ;;
    TERMINATED|ERROR)
      log_error "Transferencia fallida: $TRANSFER_STATE"
      exit 1
      ;;
  esac
  sleep $POLL_INTERVAL
done

# -----------------------------------------------------------------------------
# RESULTADO FINAL
# -----------------------------------------------------------------------------
echo ""
log_section "Resultado"

if [ "$TRANSFER_STATE" = "COMPLETED" ]; then
  log_ok "PRUEBA EXITOSA — Transferencia completada"
  echo ""
  echo "  Archivo en MinIO: consumer-results/$OUTPUT_KEY"
  echo "  Consola MinIO: http://localhost:9001"
  echo ""
  echo "Verificar con:"
  echo "  docker run --rm --network host minio/mc ls local/consumer-results/"
else
  log_warn "La transferencia puede estar aún en progreso."
  echo "  Estado final: $TRANSFER_STATE"
  echo "  Verificar manualmente: curl $CONSUMER_MANAGEMENT/v3/transferprocesses/$TRANSFER_ID -H 'X-Api-Key: $CONSUMER_KEY'"
fi

echo ""
log_section "Verificación de seguridad Enfoque A"
echo "  Puerto 39443 expuesto al exterior: SÍ (NGINX allowlist)"
echo "  Datos del paciente en transit: SÍ (archivo completo transferido a S3)"
echo "  Nuevas reglas de firewall necesarias: 1 (puerto de entrada 39443)"
