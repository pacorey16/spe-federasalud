#!/bin/bash
# =============================================================================
# comparativa-metricas.sh — Métricas comparativas para el TFM
#
# Ejecutar DESPUÉS de haber probado ambos enfoques.
# Recoge datos reales del sistema para rellenar la tabla comparativa del TFM.
# =============================================================================

set -uo pipefail

MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-simpl-local}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

separator() { echo -e "\n${YELLOW}══════════════════════════════════════════${NC}"; }
metric()    { echo -e "  ${GREEN}▶${NC} $1${BLUE}: ${NC}$2"; }

# =============================================================================
separator
echo -e "${YELLOW}  MÉTRICA 1: Puertos TCP expuestos en el hospital${NC}"
separator

echo ""
echo "--- Enfoque A (con Data Plane + NGINX) ---"
metric "Puerto NGINX (entrada)" "39443/tcp"
echo "  Procesos escuchando:"
ss -tlnp 2>/dev/null | grep -E "39443|39291|39192|39193" || \
  echo "  (no hay procesos activos del Enfoque A en este momento)"

echo ""
echo "--- Enfoque B (SPE en Minikube) ---"
echo "  Servicios expuestos en namespace simpl-spe:"
kubectl get services -n simpl-spe --context "$MINIKUBE_PROFILE" 2>/dev/null || \
  echo "  (Minikube no disponible o namespace no existe)"
metric "Resultado esperado" "No resources found (cero puertos expuestos)"

# =============================================================================
separator
echo -e "${YELLOW}  MÉTRICA 2: Datos del paciente en S3 externo${NC}"
separator

echo ""
echo "--- Bucket provider-data (datos originales del hospital) ---"
docker run --rm --network host minio/mc ls local/provider-data/ 2>/dev/null || \
  echo "  (MinIO no disponible o bucket vacío)"

echo ""
echo "--- Bucket consumer-results (lo que sale del hospital) ---"
docker run --rm --network host minio/mc ls local/consumer-results/ 2>/dev/null || \
  echo "  (MinIO no disponible o bucket vacío)"

echo ""
metric "Enfoque A" "Contiene DATOS ORIGINALES (archivo completo del paciente)"
metric "Enfoque B" "Contiene solo RESULTADOS del algoritmo (sin datos del paciente)"

# =============================================================================
separator
echo -e "${YELLOW}  MÉTRICA 3: Latencia de transferencia${NC}"
separator

echo ""
echo "  Para medir latencia, ejecutar:"
echo "    time ./scripts/enfoque-a/test-full-transfer.sh 2>&1 | tail -3"
echo "    time ./scripts/enfoque-b/test-spe-execution.sh 2>&1 | tail -3"
echo ""
metric "Enfoque A" "Latencia esperada: 2-10s (síncrono, directo)"
metric "Enfoque B" "Latencia esperada: 15-60s (asíncrono, Job K8s)"

# =============================================================================
separator
echo -e "${YELLOW}  MÉTRICA 4: Namespaces K8s efímeros${NC}"
separator

echo ""
echo "  Namespaces con prefijo spe-exec- (activos o recientes):"
kubectl get namespaces --context "$MINIKUBE_PROFILE" 2>/dev/null | grep "spe-exec" || \
  echo "  (ninguno activo en este momento — se destruyen tras 60s)"

# =============================================================================
separator
echo -e "${YELLOW}  MÉTRICA 5: Reglas de firewall necesarias${NC}"
separator

echo ""
metric "Enfoque A" "1 regla nueva de ENTRADA (puerto 39443 desde IPs del cloud)"
metric "Enfoque B" "0 reglas nuevas (solo tráfico SALIENTE al broker Kafka)"

# =============================================================================
separator
echo -e "${YELLOW}  RESUMEN — Tabla para el TFM${NC}"
separator

echo ""
echo "┌──────────────────────────────────────────┬──────────────────┬──────────────────────┐"
echo "│ Métrica                                  │ Enfoque A        │ Enfoque B (SPE)      │"
echo "├──────────────────────────────────────────┼──────────────────┼──────────────────────┤"
echo "│ Puertos TCP expuestos en hospital        │ 39443 (NGINX)    │ NINGUNO              │"
echo "│ Datos del paciente en S3 externo         │ Sí (completo)    │ No (solo resultados) │"
echo "│ Latencia total (orden → datos disponib.) │ ___ ms           │ ___ ms               │"
echo "│ Namespaces K8s efímeros por ejecución    │ N/A              │ 1 (auto-destruido)   │"
echo "│ Reglas de firewall nuevas                │ 1 (entrada)      │ 0                    │"
echo "│ Aprobación TI hospitalaria simulada      │ Requiere apertura│ Sin cambios red      │"
echo "│ Cumplimiento RGPD (minimización datos)   │ Depende políticas│ Por diseño arq.      │"
echo "└──────────────────────────────────────────┴──────────────────┴──────────────────────┘"
echo ""
echo "  Rellenar la latencia con: time ./scripts/enfoque-a/test-full-transfer.sh"
echo "                            time ./scripts/enfoque-b/test-spe-execution.sh"
