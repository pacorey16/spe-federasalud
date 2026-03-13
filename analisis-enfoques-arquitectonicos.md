# Análisis de Enfoques Arquitectónicos para la Integración Hospitalaria en el Dataspace SIMPL

**TFM — Master en Ingeniería Informática**
**Fecha:** Marzo 2026

---

## Introducción

El presente documento analiza dos enfoques arquitectónicos para resolver el problema de integración segura entre hospitales y el dataspace europeo SIMPL. El reto central es el siguiente: ¿cómo permitir que algoritmos de análisis externos ejecuten sobre datos clínicos locales, respetando la seguridad perimetral hospitalaria, el cumplimiento del RGPD y la interoperabilidad con los estándares europeos de dataspaces?

Ambos enfoques se han implementado como prueba de concepto (PoC) y son complementarios en su propósito global, pero difieren fundamentalmente en el modelo de red, el protocolo de comunicación y las implicaciones operacionales para el entorno hospitalario.

---

## Contexto: El Problema de la Integración Hospitalaria

Los hospitales manejan datos altamente sensibles (historiales clínicos, diagnósticos, imágenes médicas) sujetos a regulaciones estrictas como el RGPD y la normativa sanitaria europea. Cuando un investigador o sistema externo quiere ejecutar un algoritmo sobre esos datos, surgen dos preguntas críticas:

1. **¿Cómo autorizar el acceso?** — Mediante contratos, políticas y gobernanza de datos.
2. **¿Cómo ejecutar técnicamente sin exponer los datos?** — Aquí es donde divergen los dos enfoques.

El denominador común de ambos enfoques es el uso del framework **Eclipse EDC (Eclipse Dataspace Connector)** como base de gobernanza, y el estándar **Dataspace Protocol (DSP)** para la negociación de contratos.

> **Nota sobre el entorno local:** Para las pruebas de este TFM se prescinde de Keycloak, HashiCorp Vault, tenant-services y contract-manager. El proyecto `simpl-edc-main` incluye un mock de identidad (`SimplIdentityService`) que genera tokens JWT con roles hardcodeados, y los contratos son gestionados internamente por EDC sin callback externo. El stack local se reduce a: **PostgreSQL + MinIO + los conectores EDC** (más Kafka y Minikube para el Enfoque B).

---

## Enfoque A: Extensión EDC Nativa — Split Architecture (Síncrono)

### Descripción General

Este enfoque implementa el patrón oficial **Split Architecture** de Eclipse EDC, dividiendo el conector en dos mitades desplegadas en entornos distintos:

- **EDC Control Plane (Cloud):** El cerebro del sistema. Gestiona el catálogo de activos, la negociación de contratos, las políticas ODRL y la orquestación de transferencias.
- **EDC Data Plane (Hospital):** El músculo del sistema. Ejecuta las transferencias de datos reales, accediendo a los datos locales del hospital bajo las instrucciones del Control Plane.

La comunicación entre el cloud y el Data Plane hospitalario se realiza a través de un **Proxy Inverso NGINX** configurado con una allowlist de IPs del cloud, de forma que solo los sistemas autorizados pueden alcanzar el Data Plane.

### Diagrama de Arquitectura

```
┌───────────────────────────────────────┐    ┌──────────────────────────────────────────┐
│                CLOUD                  │    │                 HOSPITAL                  │
│                                       │    │                                           │
│  ┌─────────────────────────────────┐  │    │  ┌───────────────────────────────────┐   │
│  │       EDC Control Plane         │  │    │  │      NGINX Reverse Proxy          │   │
│  │                                 │  │    │  │   (IP Allowlist: IPs del Cloud)   │   │
│  │  - Catálogo de activos          │  │    │  └─────────────────┬─────────────────┘   │
│  │  - Negociación de contratos     │  │    │                    │                      │
│  │  - Políticas ODRL               │◄─┼────┼────────────────────┘ DSP/HTTP             │
│  │  - Gestión de transferencias    │  │    │                    │                      │
│  │  - Orquestación Data Plane      │──┼────┼────────────────────►                      │
│  └─────────────────────────────────┘  │    │  ┌─────────────────▼─────────────────┐   │
│                                       │    │  │       EDC Data Plane              │   │
│  ┌─────────────────────────────────┐  │    │  │                                   │   │
│  │     PostgreSQL + HashiCorp      │  │    │  │  - Recibe órdenes de transferencia│   │
│  │          Vault                  │  │    │  │  - Accede a datos locales         │   │
│  └─────────────────────────────────┘  │    │  │  - Puerto expuesto (filtrado)     │   │
│                                       │    │  │  - Envía datos a S3/destino       │   │
│  ┌─────────────────────────────────┐  │    │  └───────────────────────────────────┘   │
│  │       MinIO S3 / Storage        │◄─┼────┼──────────────────────────────────────    │
│  └─────────────────────────────────┘  │    │                                           │
└───────────────────────────────────────┘    └──────────────────────────────────────────┘
```

### Flujo de Operación

1. El investigador (consumidor) consulta el catálogo del Control Plane en cloud.
2. Se negocia un contrato mediante el Dataspace Protocol (DSP) entre el Control Plane del consumidor y el del proveedor (hospital).
3. Una vez firmado el contrato, el Control Plane ordena al Data Plane hospitalario iniciar la transferencia.
4. NGINX intercepta la petición entrante y valida que proviene de una IP autorizada.
5. El Data Plane accede a los datos locales y los transfiere al destino (S3, HTTP).
6. El proceso es síncrono: el Control Plane espera confirmación del Data Plane.

### Tecnologías Principales

| Componente | Tecnología | Local (PoC) | Producción |
|---|---|---|---|
| Framework base | Eclipse EDC 0.10.1 | Igual | Igual |
| Protocolo | Dataspace Protocol (DSP) / HTTP | Igual | Igual |
| Proxy inverso | NGINX con allowlist de IPs | Igual | mTLS + allowlist dinámica |
| Base de datos | PostgreSQL | 1 instancia Docker | PostgreSQL HA / RDS |
| Gestión de secretos | — | Props directas en fichero | HashiCorp Vault |
| Identidad/Auth | `SimplIdentityService` (mock JWT) | Tokens base64 hardcoded | Keycloak + mTLS |
| Almacenamiento | MinIO S3 | Docker local | MinIO / AWS S3 |
| Seguridad | API Keys + ODRL | X-Api-Key en header | JWT firmado + Vault |
| Observabilidad | — | Logs de consola | OpenTelemetry + ELK |

### Fortalezas

- **Estándar oficial:** Implementa el patrón Split Architecture documentado por Eclipse EDC y compatible con la especificación Gaia-X.
- **Interoperabilidad nativa:** Al usar DSP, es interoperable de facto con cualquier conector EDC del ecosistema europeo de dataspaces.
- **Gobernanza completa:** ODRL permite definir políticas complejas (restricciones geográficas, por rol, temporales).
- **Arquitectura probada:** Eclipse EDC tiene comunidad activa y casos de uso en producción.
- **Menor latencia:** Comunicación síncrona directa entre Control Plane y Data Plane.
- **Sin dependencias de mensajería:** No requiere infraestructura Kafka ni brokers adicionales.

### Debilidades

- **Puerto de entrada expuesto:** El hospital debe abrir un puerto accesible desde internet (aunque filtrado por IP).
- **La nube inicia la conexión:** Modelo push desde el exterior hacia el hospital — contraviene las políticas de seguridad perimetral de la mayoría de centros sanitarios.
- **Superficie de ataque real:** Un NGINX mal configurado, un fallo en la allowlist o un cambio de IP del cloud puede comprometer la seguridad.
- **Aprobación institucional difícil:** Los departamentos de TI hospitalarios raramente aprueban la apertura de puertos de entrada, independientemente de los filtros aplicados.
- **Dependencia de IPs fijas:** Si el cloud usa IPs dinámicas o CDN, la allowlist se vuelve inmanejable.

---

## Enfoque B: SPE-Federasalud — Kafka + Kubernetes Sandbox (Asíncrono)

### Descripción General

Este enfoque implementa un patrón **outbound-only**: el hospital nunca expone puertos a internet. En su lugar, el hospital despliega un componente ligero (SPE-Connector) que escucha permanentemente un topic de Kafka alojado en la nube. Cuando llega una orden de ejecución, el SPE-Connector crea un entorno de ejecución efímero y aislado dentro del Kubernetes del hospital, ejecuta el algoritmo sobre los datos locales, y sube únicamente los resultados a un bucket S3 en la nube.

### Diagrama de Arquitectura

```
┌───────────────────────────────────────┐    ┌──────────────────────────────────────────┐
│                CLOUD                  │    │                 HOSPITAL                  │
│                                       │    │                                           │
│  ┌─────────────────────────────────┐  │    │  ┌───────────────────────────────────┐   │
│  │       Kafka Broker              │  │    │  │       SPE-Connector               │   │
│  │  Topic: spe-execution-requests  │◄─┼────┼──│    (Kafka Consumer Thread)        │   │
│  └─────────────────────────────────┘  │    │  │                                   │   │
│                                       │    │  │  OUTBOUND ONLY — cero puertos     │   │
│  ┌─────────────────────────────────┐  │    │  │  expuestos. El hospital conecta   │   │
│  │       SIMPL-EDC                 │  │    │  │  al cloud, nunca al revés.        │   │
│  │  (Gobernanza y Contratos)       │──┼────┼──►                                   │   │
│  │  Publica orden en Kafka         │  │    │  └─────────────────┬─────────────────┘   │
│  └─────────────────────────────────┘  │    │                    │ Orden recibida       │
│                                       │    │                    ▼                      │
│  ┌─────────────────────────────────┐  │    │  ┌───────────────────────────────────┐   │
│  │     S3 Results Bucket           │◄─┼────┼──│    K8s Namespace Efímero          │   │
│  └─────────────────────────────────┘  │    │  │    spe-exec-{executionId}         │   │
└───────────────────────────────────────┘    │  │                                   │   │
                                             │  │  - NetworkPolicy: deny all ingress│   │
                                             │  │  - PVC datos: readOnly mount      │   │
                                             │  │  - Egress solo → DNS + S3         │   │
                                             │  │  - runAsNonRoot, drop ALL caps    │   │
                                             │  │  - TTL 60s → auto-destrucción     │   │
                                             │  └───────────────────────────────────┘   │
                                             │                                           │
                                             │  CERO PUERTOS EXPUESTOS A INTERNET        │
                                             └──────────────────────────────────────────┘
```

### Flujo de Operación

1. SIMPL-EDC (cloud) gestiona la gobernanza: contratos y políticas acordadas con el hospital.
2. Una vez autorizado, SIMPL-EDC publica una orden de ejecución en el topic Kafka `spe-execution-requests`.
3. El SPE-Connector en el hospital (Kafka consumer) recibe la orden de forma asíncrona.
4. El SPE-Connector llama a la API de Kubernetes para crear un namespace aislado y efímero.
5. Se crea un Job K8s con el algoritmo especificado, montando los datos locales como `readOnly`.
6. NetworkPolicy restringe toda comunicación: solo se permite egress hacia DNS y S3.
7. El algoritmo procesa los datos y sube los resultados al bucket S3 del cloud.
8. El namespace se destruye automáticamente tras 60 segundos (TTL).

### Modelo de Seguridad por Capas

```
Capa 1 — Red:      Cero puertos de entrada. NGINX no existe. Firewall sin cambios.
Capa 2 — Datos:    PVC montado readOnly. Los datos nunca salen del hospital.
Capa 3 — Ejecución: Namespace efímero. NetworkPolicy deny-all. No pod-to-pod.
Capa 4 — Container: runAsNonRoot. readOnlyRootFilesystem. Drop ALL capabilities.
Capa 5 — Recursos: ResourceQuota (4 cores, 4GB RAM). Evita DoS interno.
Capa 6 — Auditoría: Labels en namespace. K8s events. Trazabilidad completa.
```

### Tecnologías Principales

| Componente | Tecnología | Local (PoC) | Producción |
|---|---|---|---|
| Framework base | Eclipse EDC 0.10.1 (ServiceExtension) | Igual | Igual |
| Mensajería | Apache Kafka 3.6.1 | Confluent Docker + PLAINTEXT | Kafka gestionado + SASL_SSL |
| Orquestación K8s | Fabric8 Kubernetes Client 6.13.4 | Minikube | K8s hospital real |
| Aislamiento | K8s NetworkPolicy + ResourceQuota + RBAC | Igual | + OPA/Gatekeeper |
| Almacenamiento | MinIO S3 | Docker local | MinIO / AWS S3 |
| Identidad/Auth | `SimplIdentityService` (mock JWT) | Tokens base64 hardcoded | Keycloak + mTLS |
| Secretos | Variables de entorno | Fichero properties / env | K8s Secrets + Vault |
| Testing | JUnit 5 + Mockito + Fabric8 Mock | Igual | Igual |
| UI Kafka | Redpanda Console | Docker local | Opcional |

### Fortalezas

- **Cero puertos expuestos:** El hospital nunca necesita abrir un puerto de entrada. Compatible con cualquier política de firewall hospitalaria.
- **Datos del paciente nunca salen:** Solo los resultados del análisis viajan al exterior, nunca los datos brutos.
- **Aislamiento por diseño:** El aislamiento de red es estructural (NetworkPolicy K8s), no dependiente de configuración manual de NGINX.
- **Modelo de confianza inverso:** El hospital es quien confía en el cloud (conecta a Kafka), no al revés.
- **Aprobación institucional viable:** Tráfico saliente a un broker conocido — patrón aceptado por TI hospitalaria.
- **Destrucción automática:** El entorno de ejecución se destruye tras cada uso, sin rastro.
- **Auditoría nativa:** Kubernetes Events y labels de namespace proporcionan trazabilidad completa.

### Debilidades

- **Fase PoC:** Actualmente usa Kafka PLAINTEXT y credenciales S3 en variables de entorno. Requiere hardening para producción.
- **Requiere Kubernetes:** El hospital debe tener un clúster K8s operativo (o Minikube en desarrollo).
- **Requiere infraestructura Kafka:** Necesita un broker Kafka en la nube, componente adicional a mantener.
- **Asíncrono:** Mayor latencia y complejidad de gestión de estado frente al enfoque síncrono.
- **No es DSP nativo:** La comunicación cloud-hospital usa Kafka, no el Dataspace Protocol estándar.
- **Un consumer por hospital:** El diseño actual escala verticalmente, no horizontalmente.

---

## Comparativa Directa

### Tabla de Criterios Técnicos

| Criterio | Split Architecture (EDC Nativo) | Kafka + K8s (SPE) |
|---|---|---|
| **Puertos expuestos en hospital** | Sí (filtrado por IP) | No (cero) |
| **Quién inicia la conexión** | Cloud → Hospital | Hospital → Cloud |
| **Datos del paciente en tránsito** | Sí, viajan al exterior | No, solo resultados |
| **Protocolo estándar EDC** | Sí, DSP nativo | No, Kafka propio |
| **Interoperabilidad dataspace** | Alta (nativo Gaia-X) | Media (requiere adaptador) |
| **Aislamiento de ejecución** | No aplica | Namespace K8s efímero |
| **Modelo de seguridad** | Políticas ODRL + JWT + Vault | NetworkPolicy + RBAC + TTL |
| **Latencia** | Baja (síncrono) | Alta (asíncrono) |
| **Complejidad operacional** | Media | Alta |
| **Infraestructura requerida** | NGINX + EDC + PostgreSQL | Kafka + K8s + EDC |
| **Aprobación TI hospitalaria** | Difícil | Viable |
| **Cumplimiento RGPD** | Depende de políticas | Por diseño arquitectónico |
| **Fase de madurez** | Producción | PoC |

### Tabla de Criterios Organizacionales y Regulatorios

| Criterio | Split Architecture | Kafka + K8s (SPE) |
|---|---|---|
| **Requiere apertura de firewall** | Sí | No |
| **Datos del paciente en exterior** | Potencialmente | Nunca |
| **Auditoría y trazabilidad** | EDC database logs | K8s Events + labels |
| **Proceso de aprobación CISO** | Largo y complejo | Simplificado |
| **Dependencia de configuración manual** | Alta (NGINX IP allowlist) | Baja (estructural) |
| **Riesgo de misconfiguration** | Alto | Bajo |

---

## Enfoque Elegido: Kafka + Kubernetes (SPE-Federasalud)

### Justificación de la Decisión

La elección del enfoque Kafka no responde únicamente a criterios técnicos, sino a la intersección entre la arquitectura de software y la realidad operacional del entorno hospitalario.

**Razón principal — Política de seguridad perimetral hospitalaria:**

> En un hospital real, el departamento de TI y el CISO (Chief Information Security Officer) no aprobarán la apertura de un puerto de entrada hacia servidores internos que contienen datos de pacientes, independientemente de los mecanismos de filtrado aplicados. Esta es una política institucional, no una preferencia técnica.

El enfoque Split Architecture es arquitectónicamente más elegante y estándar, pero choca con esta realidad. El enfoque Kafka invierte el modelo: el hospital actúa como un cliente que consume mensajes de un broker externo conocido, exactamente igual que una aplicación web consume una API. Este patrón es universalmente aceptado por los departamentos de seguridad hospitalaria porque:

1. No hay nuevas reglas de firewall de entrada que aprobar.
2. El hospital controla a qué se conecta, no quién se conecta a él.
3. El broker Kafka es un endpoint conocido y auditado, no una conexión arbitraria.

**Razón secundaria — Datos del paciente:**

Con el enfoque Split Architecture, los datos del paciente deben transferirse físicamente al exterior (aunque cifrados). Con el enfoque Kafka + K8s, el algoritmo se desplaza al hospital — los datos nunca salen. Esta es la implementación técnica del principio de **minimización de datos** del RGPD.

**Razón terciaria — Aislamiento estructural:**

La seguridad del enfoque Split Architecture depende de que NGINX esté correctamente configurado. La seguridad del enfoque Kafka + K8s es estructural: las NetworkPolicy de Kubernetes deniegan el tráfico por defecto; no hay nada que "olvidar configurar".

### Consideraciones para Producción

El enfoque SPE requiere las siguientes mejoras antes de un despliegue en producción real:

| Mejora | Descripción | Prioridad |
|---|---|---|
| Kafka SASL_SSL | Reemplazar PLAINTEXT por autenticación mTLS | Crítica |
| K8s Secrets / Vault | Mover credenciales S3 fuera de variables de entorno | Crítica |
| Registry privado | Autenticación en Docker registry para imágenes de algoritmos | Alta |
| Network Policy IP | Filtrar S3 egress por IP específica, no por rango | Alta |
| HL7/FHIR | Soporte para estándares de datos clínicos | Media |
| Multi-consumer | Arquitectura de particiones Kafka para escalar por hospital | Media |

---

## Conclusión

Ambos enfoques son válidos desde una perspectiva técnica y representan trade-offs distintos entre estándar/interoperabilidad y seguridad/adoptabilidad:

- **Split Architecture** es el camino correcto si el objetivo es la máxima interoperabilidad con el ecosistema europeo de dataspaces y el entorno hospitalario tiene una postura de seguridad flexible.

- **Kafka + K8s (SPE)** es el camino correcto si el objetivo es la adopción real en hospitales con políticas de seguridad estrictas, donde la protección de datos del paciente y la minimización de la superficie de ataque son no negociables.

En el contexto de este TFM, y dado que el escenario objetivo son hospitales reales con departamentos de TI conservadores, **el enfoque SPE-Federasalud es el elegido como implementación principal**, complementado por SIMPL-EDC para la capa de gobernanza y contratos del dataspace.

---

*Documento generado en el contexto del TFM — Marzo 2026*
