# ADR-018: Distributed Locking para Outbox Relay con ShedLock

## Estado
Aceptada

## Fecha
2026-05-18

## Contexto
El sistema implementa el patrón Transactional Outbox (ADR-Outbox) en los 
microservicios productores de eventos de dominio (event-service, 
inscription-service, payment-service). Cada servicio expone un 
OutboxRelayService con un @Scheduled que lee periódicamente la tabla 
outbox_events y publica los mensajes pendientes a RabbitMQ.

El RNF-02 (Escalabilidad Horizontal) y la proyección de despliegue en AWS 
ECS/Fargate (ADR-004) contemplan ejecutar múltiples réplicas de cada 
microservicio para soportar los picos de 5,000 usuarios concurrentes 
declarados en RNF-04.

Sin un mecanismo de coordinación distribuida, todas las réplicas 
ejecutarían el scheduler simultáneamente, leerían los mismos registros 
de outbox y publicarían eventos duplicados a RabbitMQ. Esto rompe la 
garantía de idempotencia del sistema (RN-13) y contradice el 
comportamiento esperado documentado en 
docs/comportamiento-runtime-inscripcion-pago.md.

Tres alternativas de coordinación distribuida fueron evaluadas:

1. Bloqueo a nivel de fila con SELECT FOR UPDATE SKIP LOCKED: funcional 
   pero acopla la lógica de coordinación a PostgreSQL y dificulta la 
   evolución a otros brokers.
2. Coordinación con Apache Zookeeper o Consul: sobreingeniería para un 
   caso de uso simple; introduce un componente de infraestructura adicional.
3. ShedLock con provider JDBC: librería madura, mínima intrusión, 
   reutiliza la base de datos existente como mecanismo de bloqueo.

## Decisión
Implementar ShedLock 5.13.0 con provider JDBC 
(shedlock-provider-jdbc-template) en todos los OutboxRelayService del 
sistema. Cada ejecución programada adquiere un lock en una tabla shedlock 
compartida por servicio, garantizando que solo una réplica ejecute la 
tarea en un instante dado.

Configuración aplicada:
- @SchedulerLock(name = "outbox-relay-{servicio}", lockAtMostFor = "30s", 
  lockAtLeastFor = "1s")
- Tabla shedlock por base de datos de servicio (no compartida entre 
  microservicios).
- Migración SQL incluida en cada script de inicialización 
  (infrastructure/postgres/init/04-{servicio}-outbox.sql).

## Alternativas descartadas
- SELECT FOR UPDATE SKIP LOCKED a nivel de query: descartada por acoplar 
  lógica de coordinación al SQL del repositorio, dificultando la 
  testabilidad unitaria del relay y violando SRP.
- Sin coordinación + idempotencia en el consumer: descartada porque 
  desplaza el problema, multiplica tráfico en RabbitMQ y oscurece las 
  métricas de outbox.

## Consecuencias

### Positivas
- (+) Garantía matemática de no-duplicación de eventos a nivel de relay, 
  reforzando la idempotencia end-to-end (RN-13).
- (+) Permite escalado horizontal de los microservicios sin cambios 
  adicionales en código.
- (+) Observabilidad: la tabla shedlock permite auditar cuándo y qué 
  instancia ejecutó cada tarea programada.
- (+) Mínima curva de adopción: anotación declarativa.

### Negativas
- (-) Introduce una dependencia transversal (ShedLock) en todos los 
  servicios productores de eventos.
- (-) Si el lock se adquiere y el nodo cae catastróficamente, el lock 
  expira tras lockAtMostFor (30s); durante ese tiempo no hay procesamiento 
  de outbox. Mitigación: alarma Prometheus si outbox_events sin procesar 
  >100 durante >1 min.
- (-) Requiere mantener la tabla shedlock sincronizada vía script SQL en 
  cada microservicio.

## Trazabilidad
- RNF-02 Escalabilidad horizontal.
- RNF-04 Concurrencia masiva.
- RN-13 Idempotencia transaccional.
- ADR-Outbox Transactional Outbox Pattern.
- Patrón aplicado: Singleton distribuido (variante GoF) + Observer.

## Referencias
- Kuznetsov, L. (2024). ShedLock — Distributed lock for Spring scheduled 
  tasks. https://github.com/lukas-krecan/ShedLock
- Richardson, C. (2018). Microservices Patterns, Capítulo 3.
