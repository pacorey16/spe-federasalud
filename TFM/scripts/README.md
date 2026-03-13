# Scripts de Prueba y Métricas

**TFM — Master en Ingeniería Informática | Marzo 2026**

---

## Descripción

Este directorio contiene los scripts de automatización para validar los dos enfoques y recoger las métricas comparativas.

| Script | Enfoque | Propósito |
|---|---|---|
| `enfoque-a/test-full-transfer.sh` | A | Prueba completa: catálogo → contrato → transferencia S3 |
| `enfoque-b/test-spe-execution.sh` | B | Prueba completa: publicar orden Kafka → Job K8s → resultado S3 |
| `comparativa-metricas.sh` | Ambos | Recoger métricas para la tabla comparativa del TFM |

---

## Uso Rápido

### Enfoque A — Transferencia completa

```bash
# Prerrequisito: infraestructura common + enfoque-a levantados
# Prerrequisito: conectores EDC arrancados (proveedor + data plane + consumidor)
# Prerrequisito: activos, políticas y contratos creados en el proveedor

chmod +x scripts/enfoque-a/test-full-transfer.sh
./scripts/enfoque-a/test-full-transfer.sh
```

### Enfoque B — Ejecución de algoritmo SPE

```bash
# Prerrequisito: infraestructura common + enfoque-b levantados
# Prerrequisito: SPE-Connector desplegado en Minikube y Running
# Prerrequisito: datos de prueba cargados en /tmp/hospital-test-data/

chmod +x scripts/enfoque-b/test-spe-execution.sh
./scripts/enfoque-b/test-spe-execution.sh
```

### Métricas comparativas

```bash
# Ejecutar DESPUÉS de haber probado ambos enfoques
chmod +x scripts/comparativa-metricas.sh
./scripts/comparativa-metricas.sh
```

---

## Variables de Entorno Configurables

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `PROVIDER_MANAGEMENT` | `http://localhost:19193/management` | URL Management API del proveedor |
| `CONSUMER_MANAGEMENT` | `http://localhost:29193/management` | URL Management API del consumidor |
| `PROVIDER_KEY` | `provider-local-api-key` | API Key del proveedor |
| `CONSUMER_KEY` | `consumer-local-api-key` | API Key del consumidor |
| `PROVIDER_DSP` | `http://localhost:19194/protocol` | Endpoint DSP del proveedor |
| `MINIO_ENDPOINT` | `http://localhost:9000` | URL de MinIO |

Para sobreescribir:
```bash
PROVIDER_KEY=mi-clave ./scripts/enfoque-a/test-full-transfer.sh
```

---

*Scripts de prueba — TFM Marzo 2026*
