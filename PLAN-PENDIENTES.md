# Plan de Implementación — Trabajo Pendiente

**TFM — Master en Ingeniería Informática | Marzo 2026**

---

## Estado Actual

| Entregable | Estado |
|---|---|
| Documentación de análisis y roadmap | Completo y corregido |
| Configs EDC (provider, data-plane, consumer) | Completo |
| Docker Compose (common, enfoque-a, enfoque-b) | Completo |
| Manifests Kubernetes (namespace, RBAC, deployment, NetworkPolicy) | Completo |
| Scripts de prueba y métricas | Completo |
| **Código Java del SPE-Connector** | **PENDIENTE** |
| **Dockerfile del SPE-Connector** | **PENDIENTE** |
| **Validación ejecutada del Enfoque A** | **PENDIENTE** |
| **Validación ejecutada del Enfoque B** | **PENDIENTE** |
| **Métricas comparativas reales** | **PENDIENTE** |

---

## Diagrama de Dependencias

```
BLOQUE 0 — Prerrequisitos
    │
    ├──► BLOQUE 1 — Compilar simpl-edc-main
    │        │
    │        └──► BLOQUE 2 — Validar Enfoque A ──────────────────────────────┐
    │                                                                         │
    └──► BLOQUE 3 — SPE-Connector Java (nuevo código)                        │
             │                                                                │
             └──► BLOQUE 4 — Validar Enfoque B ──────────────────────────────┤
                                                                              │
                                                    BLOQUE 5 — Integración EDC→SPE (opcional PoC)
                                                              │
                                                    BLOQUE 6 — Métricas y Validación Final
```

---

## BLOQUE 0 — Verificación de Prerrequisitos

**Objetivo:** Confirmar que el entorno de desarrollo está listo antes de ejecutar ningún paso.

**Dependencias:** Ninguna.

### Tareas

| # | Tarea | Qué verificar |
|---|---|---|
| 0.1 | Java 17 instalado | `java -version` → openjdk 17.x |
| 0.2 | Maven 3.9+ instalado | `mvn -version` |
| 0.3 | JAVA_HOME apunta a Java 17 | `echo $JAVA_HOME` |
| 0.4 | Docker Desktop en ejecución | `docker info` |
| 0.5 | Minikube instalado | `minikube version` |
| 0.6 | kubectl instalado | `kubectl version --client` |
| 0.7 | jq instalado | `jq --version` |
| 0.8 | Variable de entorno `PROJECT_RELEASE_VERSION` definida | **Crítico**: el pom.xml de simpl-edc-main usa `${env.PROJECT_RELEASE_VERSION}` como versión del artefacto — sin esta variable el `mvn package` falla |

**Nota sobre 0.8:** Ejecutar antes de cualquier `mvn`:
```
export PROJECT_RELEASE_VERSION=1.0.0
```

**Criterio de finalización:** Todos los comandos responden sin error.

---

## BLOQUE 1 — Compilar simpl-edc-main

**Objetivo:** Obtener el JAR `basic-connector.jar` que se usa como binario único para los tres procesos del Enfoque A (Control Plane proveedor, Data Plane hospital, Control Plane consumidor).

**Dependencias:** Bloque 0.

### Tareas

| # | Tarea | Descripción |
|---|---|---|
| 1.1 | Acceso al registro Maven de SIMPL | El pom.xml referencia artefactos de `code.europa.eu` (extensiones GXFS MinIO, EDC forked). Verificar si el `settings.xml` de Maven tiene las credenciales o si los JARs están en caché local. Si no: buscar si hay un repositorio público alternativo o si los JARs están disponibles como artefactos locales. |
| 1.2 | Compilar con `mvn clean package -DskipTests` | En el directorio `simpl-edc-main/`. |
| 1.3 | Verificar el JAR generado | `simpl-edc-main/target/basic-connector.jar` debe existir y tener un tamaño razonable (>50MB con todas las dependencias). |
| 1.4 | Smoke test del JAR | Arrancar con una config mínima y verificar que arranca sin excepciones críticas. |

**Riesgo principal:** El acceso al registro privado de SIMPL (`code.europa.eu:4567`). Si las dependencias no están en caché local ni en un repositorio público, la compilación falla con `Could not resolve dependencies`. En ese caso, explorar si hay un `settings.xml` ya configurado en el equipo.

**Criterio de finalización:** `ls -lh simpl-edc-main/target/basic-connector.jar` devuelve el archivo.

---

## BLOQUE 2 — Validar Enfoque A (Split Architecture EDC Nativo)

**Objetivo:** Demostrar que la arquitectura Split funciona localmente: un archivo del hospital (en MinIO) se transfiere al bucket del consumidor a través del protocolo DSP, pasando por el NGINX del hospital.

**Dependencias:** Bloque 1.

### Tareas

| # | Tarea | Descripción |
|---|---|---|
| 2.1 | Levantar infraestructura común | `docker compose -f TFM/infrastructure/docker-compose-common.yml up -d`. Verificar que `provider-db` (5432), `consumer-db` (5433) y `minio` (9000/9001) están `healthy`. |
| 2.2 | Levantar NGINX | `docker compose -f TFM/infrastructure/docker-compose-enfoque-a.yml up -d`. Verificar que el contenedor `simpl-nginx-hospital` arranca. |
| 2.3 | Crear buckets en MinIO | Via `minio/mc`: crear `provider-data` y `consumer-results`. |
| 2.4 | Subir datos de prueba al bucket `provider-data` | Subir un JSON de prueba (dataset de cardiología) al path `cardio/dataset-001.json`. |
| 2.5 | Arrancar Control Plane del proveedor | Proceso Java con `TFM/enfoque-a-split-architecture/edc-provider/control-plane/config/provider-config.properties`. Health check en `:19191`. |
| 2.6 | Arrancar Data Plane del hospital | Proceso Java con `TFM/enfoque-a-split-architecture/edc-provider/data-plane/config/dataplane-config.properties`. Este proceso escucha en `:39192/:39291/:39193`. NGINX en `:39443` redirige a él. |
| 2.7 | Registrar el Data Plane en el Control Plane | Llamada `POST /control/v1/dataplanes` al Control Plane para que sepa dónde está el Data Plane. |
| 2.8 | Arrancar Control Plane del consumidor | Proceso Java con `TFM/enfoque-a-split-architecture/edc-consumer/control-plane/config/consumer-config.properties`. Health check en `:29191`. |
| 2.9 | Crear activos, políticas y contratos | Llamadas a la Management API del proveedor (`:19193`): crear asset `hospital-dataset-cardio-001`, política ODRL con restricción `RESEARCHER`, definición de contrato. |
| 2.10 | Ejecutar script de transferencia completa | `./TFM/scripts/enfoque-a/test-full-transfer.sh`. El script automatiza: consultar catálogo → negociar contrato → esperar FINALIZED → iniciar transferencia S3 → verificar COMPLETED. |
| 2.11 | Verificar resultado en MinIO | El archivo aparece en `consumer-results/` en la consola web (`:9001`). |
| 2.12 | Verificar seguridad del Enfoque A | Confirmar que una petición con IP no autorizada a NGINX devuelve `403`. Verificar que los logs de NGINX muestran `cloud_authorized=1` para el Control Plane. |

**Riesgo principal:** `edc.dataplane.embedded.enabled=false` en el Control Plane del proveedor. Aunque la config está así establecida, hay que verificar en tiempo de ejecución que el basic-connector.jar soporta este modo separado (split) o si el jar siempre embebe el Data Plane. Puede requerirse ajuste.

**Criterio de finalización:** El archivo de prueba aparece en el bucket `consumer-results` de MinIO tras ejecutar el script.

---

## BLOQUE 3 — Crear el SPE-Connector (Código Java Nuevo)

**Objetivo:** Implementar el componente central del TFM: un proceso Java que consume órdenes de ejecución de Kafka y lanza Jobs Kubernetes aislados en el hospital.

**Dependencias:** Bloque 0.

**Este bloque es la contribución original del TFM.** Los bloques anteriores usan código preexistente de SIMPL; este bloque es código nuevo.

### Estructura del proyecto Maven a crear

```
spe-federasalud/               ← pom.xml raíz (multi-módulo)
├── spe-model/                 ← POJOs compartidos: ExecutionRequest, ExecutionResult
├── spe-kafka/                 ← Kafka consumer: lee órdenes del topic
├── spe-k8s/                   ← Kubernetes sandbox: crea Jobs efímeros con Fabric8
└── spe-launcher/              ← Entry point: ensambla los módulos y arranca el proceso
```

### Dependencias Maven principales del proyecto nuevo

| Librería | Versión | Módulo que la usa | Función |
|---|---|---|---|
| `org.eclipse.edc:runtime-metamodel` | 0.10.1 | spe-launcher | `ServiceExtension` para arranque EDC-compatible |
| `org.apache.kafka:kafka-clients` | 3.6.1 | spe-kafka | Consumer API de Kafka |
| `io.fabric8:kubernetes-client` | 6.13.4 | spe-k8s | API de Kubernetes (crear namespaces, Jobs, NetworkPolicy) |
| `com.fasterxml.jackson.core:jackson-databind` | 2.x | spe-model | Deserializar mensajes JSON de Kafka |
| `org.slf4j:slf4j-api` + impl | 2.x | todos | Logging |
| `org.junit.jupiter:junit-jupiter` | 5.x | test | Tests unitarios |
| `io.fabric8:kubernetes-server-mock` | 6.13.4 | test | Mock de la API K8s para tests |

### Tareas por módulo

#### 3.1 — `spe-model`: Modelos de datos compartidos

| Tarea | Descripción |
|---|---|
| Crear `pom.xml` del módulo | Solo Jackson como dependencia |
| Definir `ExecutionRequest` | POJO con campos: `executionId`, `algorithmImage`, `command` (lista), `datasetId`, `outputConfig` (endpoint, bucket, key, credenciales) |
| Definir `OutputConfig` | POJO anidado: endpoint S3, bucket, key, accessKey, secretKey |
| Tests de serialización | Verificar que el JSON del topic se deserializa correctamente a `ExecutionRequest` |

#### 3.2 — `spe-kafka`: Consumer Kafka

| Tarea | Descripción |
|---|---|
| Crear `pom.xml` del módulo | Depende de `spe-model` + `kafka-clients` |
| Implementar `KafkaConsumerService` | Clase que en un hilo dedicado hace `poll()` en bucle sobre el topic `spe-execution-requests`. Al recibir un mensaje: deserializar a `ExecutionRequest` y delegar al `SandboxManager` |
| Gestión del offset | Commit manual del offset solo tras éxito del Job K8s (at-least-once delivery) |
| Configuración inyectable | Bootstrap servers, topic, group-id, auto-offset-reset — via propiedades o variables de entorno |
| Tests unitarios | Verificar que mensajes malformados no rompen el loop. Verificar que el offset no se commitea ante fallo. |

#### 3.3 — `spe-k8s`: Kubernetes Sandbox Manager

| Tarea | Descripción |
|---|---|
| Crear `pom.xml` del módulo | Depende de `spe-model` + `fabric8:kubernetes-client` |
| Implementar `KubernetesSandboxService` | Clase que, dado un `ExecutionRequest`, orquesta toda la operación K8s |
| Sub-tarea: Crear namespace efímero | `spe-exec-{executionId}` con labels de auditoría (execution_id, requested_by, timestamp) |
| Sub-tarea: Crear `ResourceQuota` | Limitar el namespace a 4 cores / 4GB RAM |
| Sub-tarea: Crear `NetworkPolicy` | Deny ALL ingress. Egress solo hacia DNS (53) y endpoint S3 |
| Sub-tarea: Crear `Job` K8s | Con la imagen y comando del `ExecutionRequest`. Montar el PVC del hospital (`readOnly: true`) en `/data/input`. `securityContext`: `runAsNonRoot`, `readOnlyRootFilesystem`, `drop ALL` caps |
| Sub-tarea: Esperar finalización del Job | Watch sobre el Job hasta `Complete` o `Failed`. Timeout configurable (por defecto 5 min) |
| Sub-tarea: Destruir namespace | Delete namespace tras finalización (incluye todos los recursos dentro) |
| Gestión de errores | Si el Job falla o hay timeout: destruir namespace, loguear error, no commitear offset Kafka |
| Tests con mock de K8s | Usar `fabric8:kubernetes-server-mock` para verificar que se crean los recursos correctos sin necesitar un cluster real |

#### 3.4 — `spe-launcher`: Entry Point

| Tarea | Descripción |
|---|---|
| Crear `pom.xml` del módulo | Depende de `spe-kafka` + `spe-k8s`. Plugin `maven-shade-plugin` para generar el fat JAR `spe-launcher-1.0.0-SNAPSHOT.jar` |
| Implementar `SpeConnectorExtension` | `ServiceExtension` de EDC que en `start()` arranca el `KafkaConsumerService` inyectándole el `KubernetesSandboxService`. Permite usar el mismo runtime de EDC para ambos enfoques |
| Alternativa: `main()` standalone | Si no se quiere depender del runtime EDC: un `main()` que lee la configuración de variables de entorno y arranca los servicios directamente. **Más simple para el PoC.** |
| Gestión del ciclo de vida | Shutdown hook para cerrar el consumer Kafka limpiamente al parar el proceso |
| Configuración | Leer `SPE_KAFKA_BOOTSTRAP_SERVERS`, `SPE_KAFKA_TOPIC`, `SPE_K8S_NAMESPACE_PREFIX`, `SPE_K8S_JOB_TTL_SECONDS`, etc. desde variables de entorno (consistente con el `configmap.yaml` ya creado) |

#### 3.5 — Dockerfile

| Tarea | Descripción |
|---|---|
| Crear `Dockerfile` en la raíz | Imagen base `eclipse-temurin:17-jre-alpine`. Copiar el fat JAR. `ENTRYPOINT ["java", "-jar", "/app/spe-launcher.jar"]`. Usuario no-root. |
| Verificar imagen localmente | `docker build` + `docker run` básico para confirmar que arranca sin error |

#### 3.6 — Compilar y verificar

| Tarea | Descripción |
|---|---|
| `mvn clean package -DskipTests` | Desde la raíz del repo. Verificar `spe-launcher/target/spe-launcher-1.0.0-SNAPSHOT.jar` |
| Ejecutar los tests | `mvn test` — especialmente los tests del mock K8s |

**Criterio de finalización del Bloque 3:** `spe-launcher/target/spe-launcher-1.0.0-SNAPSHOT.jar` existe y al ejecutarlo arranca el consumer Kafka sin errores.

---

## BLOQUE 4 — Validar Enfoque B (Kafka + Kubernetes SPE)

**Objetivo:** Demostrar que el SPE-Connector recibe una orden de Kafka, ejecuta un algoritmo sobre datos locales del hospital en un sandbox K8s efímero, y sube solo el resultado a S3 — sin exponer ningún puerto.

**Dependencias:** Bloque 3 completo + infraestructura Kafka levantada.

### Tareas

| # | Tarea | Descripción |
|---|---|---|
| 4.1 | Levantar Minikube | `minikube start --profile simpl-local --cpus 4 --memory 8192`. Solo si no está ya en ejecución. |
| 4.2 | Levantar Kafka | `docker compose -f TFM/infrastructure/docker-compose-enfoque-b.yml up -d`. Verificar que `simpl-kafka` está `healthy`. |
| 4.3 | Crear el topic | `kafka-topics --create --topic spe-execution-requests --partitions 3`. Verificar que no existe previamente. |
| 4.4 | Construir imagen en Minikube | `eval $(minikube docker-env --profile simpl-local)` + `docker build -t simpl/spe-connector:latest .` desde la raíz. |
| 4.5 | Aplicar manifests K8s | En orden: `namespace.yaml` → `rbac.yaml` → `configmap.yaml` → `deployment.yaml` → `network-policy.yaml`. Todos en `TFM/enfoque-b-kafka-spe/k8s/`. |
| 4.6 | Verificar arranque del SPE-Connector | `kubectl logs -f deployment/spe-connector -n simpl-spe`. Debe mostrar que el consumer Kafka está listo y escuchando. |
| 4.7 | Cargar datos de prueba | Crear el `PersistentVolume` + `PVC` + copiar `patients.json` en el nodo de Minikube via `minikube ssh`. |
| 4.8 | Ejecutar script de prueba | `./TFM/scripts/enfoque-b/test-spe-execution.sh`. El script publica una orden en Kafka y observa el ciclo de vida del namespace efímero. |
| 4.9 | Verificar resultado en MinIO | El JSON con las estadísticas del dataset (no los datos del paciente) aparece en `consumer-results/`. |
| 4.10 | Verificar seguridad del Enfoque B | `kubectl get services -n simpl-spe` → vacío. Confirmar que el namespace `spe-exec-{id}` aparece y desaparece en <90s. Verificar que en `consumer-results` no hay datos de pacientes. |

**Criterio de finalización:** El resultado del algoritmo aparece en MinIO y `kubectl get services -n simpl-spe` devuelve vacío.

---

## BLOQUE 5 — Integración EDC → SPE (Puente entre enfoques)

**Objetivo:** Conectar el ciclo de gobernanza del Enfoque A (EDC negocia el contrato) con el mecanismo de ejecución del Enfoque B (SPE ejecuta el algoritmo). En el PoC, esto define quién publica en Kafka y cuándo.

**Dependencias:** Bloques 2 y 4 completos.

### Decisión de diseño

Hay dos opciones para el PoC, con distinto nivel de integración:

#### Opción 1 — Script de simulación (recomendada para el PoC)

El script de prueba del Enfoque B simula directamente la publicación en Kafka, sin pasar por EDC. En el TFM se documenta que en producción sería una extensión EDC. **Ventaja:** No modifica simpl-edc-main. **Suficiente para demostrar el concepto.**

| Tarea | Descripción |
|---|---|
| 5.1.1 | Documentar el gap | En el análisis, añadir un apartado "Integración en producción" que explica que `simpl-edc-main` publicaría a Kafka desde un listener `ContractNegotiationListener.finalized()` (hook ya existente en `ContractAgreementListener.java`) |
| 5.1.2 | Ajustar el script de prueba del Enfoque B | El mensaje publicado en Kafka debe incluir los campos del asset negociado por EDC (asset_id, consumer_id, provider_id) para que sea realista |

#### Opción 2 — EDC Extension real (mayor profundidad técnica)

Crear un nuevo módulo `spe-edc-extension` en el SPE-Connector que implementa `ContractNegotiationListener`. Cuando `finalized()` se dispara en el lado CONSUMER del proveedor (hospital), publica la orden en Kafka. Se registra en simpl-edc-main via SPI.

| Tarea | Descripción |
|---|---|
| 5.2.1 | Crear módulo `spe-edc-extension` | Depende de `org.eclipse.edc:contract-spi:0.10.1` + `spe-model` + `kafka-clients` |
| 5.2.2 | Implementar `SpeKafkaPublisherExtension` | `ServiceExtension` que registra un `ContractNegotiationListener`. En `finalized()` con tipo `PROVIDER`: construir un `ExecutionRequest` y publicarlo en el topic Kafka |
| 5.2.3 | Registrar la extensión en simpl-edc-main | Añadir el JAR del módulo al classpath de `basic-connector.jar` y registrar en `META-INF/services` |
| 5.2.4 | Prueba de integración completa | Negociar contrato entre provider y consumer → verificar que el SPE-Connector recibe automáticamente la orden → verificar resultado en MinIO |

**Recomendación para el TFM:** Implementar la Opción 1 para el PoC funcional y documentar la Opción 2 como diseño de producción. Esto simplifica el trabajo sin perder rigor técnico.

**Criterio de finalización:** Existe documentación clara de cómo EDC dispararía el SPE (mediante `ContractNegotiationListener.finalized()`), ya sea como extensión real o como descripción de diseño.

---

## BLOQUE 6 — Métricas Comparativas y Validación Final

**Objetivo:** Recoger los datos reales de ambos enfoques para completar la tabla comparativa del TFM y obtener evidencia empírica de las diferencias.

**Dependencias:** Bloques 2 y 4 completos.

### Tareas

| # | Tarea | Descripción |
|---|---|---|
| 6.1 | Ejecutar `comparativa-metricas.sh` | Genera la tabla con datos del sistema en tiempo real. |
| 6.2 | Medir latencia Enfoque A | `time ./TFM/scripts/enfoque-a/test-full-transfer.sh`. Anotar el tiempo total. |
| 6.3 | Medir latencia Enfoque B | `time ./TFM/scripts/enfoque-b/test-spe-execution.sh`. Anotar el tiempo total. |
| 6.4 | Rellenar tabla comparativa | Actualizar los campos `___ ms` en `analisis-enfoques-arquitectonicos.md` y `roadmap-implementacion.md` con los valores reales medidos. |
| 6.5 | Capturas de pantalla | Consola MinIO mostrando los buckets. Logs de NGINX con `cloud_authorized`. Logs del SPE-Connector con el ciclo de vida del namespace. `kubectl get namespaces` mostrando el namespace efímero. |
| 6.6 | Verificación de seguridad final del Enfoque A | Petición a NGINX con IP no autorizada → debe recibir `403`. |
| 6.7 | Verificación de seguridad final del Enfoque B | `kubectl get services -n simpl-spe` → vacío. Contenido de `consumer-results/` → solo estadísticas, no datos de pacientes. |

**Criterio de finalización:** La tabla comparativa en `analisis-enfoques-arquitectonicos.md` tiene todos los campos rellenos con datos reales.

---

## Resumen de Esfuerzo

| Bloque | Tipo de trabajo | Complejidad |
|---|---|---|
| 0 — Prerrequisitos | Verificación de entorno | Baja |
| 1 — Compilar simpl-edc-main | Construcción de proyecto existente | Baja-Media (riesgo: acceso al registro Maven) |
| 2 — Validar Enfoque A | Configuración y ejecución | Media (orquestar 3 procesos Java + Docker) |
| 3 — SPE-Connector Java | **Desarrollo de código nuevo** | **Alta** (es la contribución original del TFM) |
| 4 — Validar Enfoque B | Despliegue en Minikube | Media (depende del Bloque 3) |
| 5 — Integración EDC→SPE | Diseño / código opcional | Baja (Opción 1) o Media (Opción 2) |
| 6 — Métricas y validación | Ejecución y documentación | Baja |

---

## Orden de Ejecución Recomendado

```
1. Bloque 0  → verificar entorno (30 min)
2. Bloque 3  → escribir el código Java del SPE-Connector (el núcleo, ~4-8h)
3. Bloque 1  → compilar simpl-edc-main (30 min, riesgo: registro Maven)
4. Bloque 2  → validar Enfoque A (1-2h)
5. Bloque 4  → validar Enfoque B (1-2h)
6. Bloque 5  → integración EDC→SPE (30 min si Opción 1, 2-3h si Opción 2)
7. Bloque 6  → métricas y cierre (1h)
```

**Motivo del orden:** El Bloque 3 (SPE-Connector) no depende de que simpl-edc-main compile, por lo que se puede desarrollar en paralelo o incluso antes. Es la tarea de mayor duración y bloquea los Bloques 4 y 5.

---

*Plan generado — TFM Marzo 2026*
