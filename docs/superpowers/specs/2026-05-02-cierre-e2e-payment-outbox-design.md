# Spec: Cierre del flujo E2E inscripción ↔ pago vía Outbox + RabbitMQ

**Fecha:** 2026-05-02
**Autora:** Tannia Hernández Rojas
**Sub-proyecto:** Código (2 de 3 — pausado el de diagramas, va antes que PPT/cierre)
**Deadline objetivo:** ~2026-05-23

---

## 1. Motivación

El SAD declara Outbox Pattern como decisión arquitectónica clave (ADR documentado, patrón referenciado en sec 4.5 del SAD v2.0). En `inscription-service` está implementado completo. En `payment-service` solo existe el **puerto** (`OutboxEventRepository`) — falta la implementación JPA, el relay con scheduler, la config de RabbitMQ y el script SQL. En runtime, `ProcesarWebhookService` falla silenciosamente o no publica nada → el flujo end-to-end está roto.

Adicionalmente, el WIP en `EventoController.java` inyecta `EventoRepository` directamente, violando el principio de arquitectura hexagonal que el SAD declara como ADR-01.

Cerrar ambos gaps:
1. Hace que el código respalde lo que el SAD afirma (cierra el gap explícito que el profe señaló: *"mecanismos como colas y bloqueo... no siempre queda con la claridad suficiente cómo se sostienen de forma coherente"*).
2. Habilita la evidencia operativa de QA pedida en el feedback (test E2E con TestContainers cubre concurrencia, asincronía, idempotencia, resiliencia con DLQ).

## 2. Alcance

### Dentro del alcance
1. **Outbox completo en `payment-service`:**
   - `OutboxEventEntity` (entidad JPA mapeada a tabla `outbox_events`)
   - `SpringDataOutboxRepository` (interfaz Spring Data)
   - `JpaOutboxEventRepository` (implementación del puerto `OutboxEventRepository`)
   - `OutboxRelayService` (scheduler que polea outbox y publica a RabbitMQ)
   - `RabbitMQConfig` (declara `RabbitTemplate`, exchange, routing)
   - Script SQL `infrastructure/postgres/init/04-payment-outbox.sql` para crear la tabla
2. **Test de integración E2E con TestContainers:** webhook → outbox → RabbitMQ → consumer en inscription → cambio de estado
3. **Fix del `EventoController`:** revertir la inyección de `EventoRepository`; si el endpoint nuevo necesita acceder a eventos, debe hacerlo a través de un caso de uso (`ConsultarEventoPorIdUseCase` o reusar `ConsultarCatalogoUseCase`)
4. **Documentación inline mínima:** comentarios `// Outbox Pattern (ADR-XX)` en los puntos clave para que el código auto-referencie las decisiones del SAD

### Fuera del alcance
- Refactor de `inscription-service` (ya funciona — no tocar)
- Extracción del Outbox a módulo `shared/` (Plan B descartado)
- Pruebas de carga/concurrencia (eso es otro sub-deliverable: evidencias NFR)
- Implementación de `auth-service`, `notification-service`, `certificate-service`
- Cambios en el frontend
- Actualización del SAD para reflejar este sub-proyecto (eso lo cubre el sub-proyecto de cierre de inconsistencias)

## 3. Decisiones de diseño

### 3.1 Espejo, no shared
**Decisión:** Replicar el Outbox de `inscription-service` en `payment-service`, no extraer a un módulo común.

**Razón:** Tannia trabaja sola con 3 semanas. Tocar `inscription-service` (que funciona) para extraer un módulo introduce riesgo de regresión sin beneficio académico claro. La duplicación es defendible en el SAD como "Outbox Pattern aplicado consistentemente en todos los productores de eventos de dominio".

### 3.2 Tabla por servicio (no compartida)
**Decisión:** `payment-service` tendrá su propia tabla `outbox_events` en su propia base de datos lógica.

**Razón:** Es coherente con la decisión arquitectónica de "una BD por servicio" del SAD. Romperla aquí abriría una crítica metodológica.

### 3.3 Polling con `SELECT FOR UPDATE SKIP LOCKED`
**Decisión:** Mismo mecanismo que `inscription-service` (revisar implementación existente para reusar el patrón exacto).

**Razón:** Coherencia y evidencia de bloqueo pesimista en el productor también, no solo en el consumidor.

### 3.4 Test E2E con TestContainers (PostgreSQL + RabbitMQ)
**Decisión:** El test arranca contenedores reales de Postgres y RabbitMQ, dispara un webhook simulado, y verifica que la inscripción cambie de estado.

**Razón:** Es la pieza de evidencia operativa que el feedback pide: *"mostrar evidencia más clara de cómo se comprueba lo que prometen en rendimiento, disponibilidad, concurrencia y resiliencia"*. Un solo test demuestra que el patrón Outbox + RabbitMQ + idempotencia funciona realmente.

### 3.5 Fix del controller — usar caso de uso, no repositorio
**Decisión:** Si el WIP del `EventoController` necesita un endpoint que consulta un evento por ID, se crea un nuevo caso de uso (`ConsultarEventoPorIdUseCase` con su `ConsultarEventoPorIdService`) o se extiende `ConsultarCatalogoUseCase`. El controller NO inyecta `EventoRepository`.

**Razón:** ADR-01 del SAD declara arquitectura hexagonal pura. El profe ya señaló inconsistencias documentales — agregar inconsistencias código↔documento sería peor.

## 4. Criterios de aceptación

El sub-proyecto se considera cerrado cuando:

- [ ] `payment-service/src/main/java/.../infrastructure/outbox/` existe con: `OutboxEventEntity`, `SpringDataOutboxRepository`, `JpaOutboxEventRepository`, `OutboxRelayService`
- [ ] `payment-service/src/main/java/.../infrastructure/messaging/RabbitMQConfig.java` declara `RabbitTemplate` apuntando a `eventos.topic` exchange
- [ ] `infrastructure/postgres/init/04-payment-outbox.sql` crea la tabla `outbox_events` con índice en `(published, created_at)`
- [ ] El `OutboxRelayService` corre cada 1-2 segundos vía `@Scheduled`, publica eventos no publicados, marca como publicados
- [ ] Existe `payment-service/src/test/java/.../PagoFlowEndToEndIT.java` que: arranca PostgreSQL + RabbitMQ vía TestContainers, simula un webhook approved, verifica que un mensaje aparece en queue `pago.confirmado` con el payload correcto
- [ ] El test pasa en CI local (`mvn -pl payment-service verify`)
- [ ] `EventoController.java` ya no inyecta `EventoRepository`; cualquier acceso a datos pasa por un `UseCase`
- [ ] Todos los archivos nuevos están commiteados con mensajes descriptivos (no en un solo "WIP")

## 5. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| El test E2E con TestContainers tarda >30s y desincentiva correrlo | Marcar la clase con `@Tag("integration")` y separarla del build rápido; correrla en CI o explícito con `mvn verify` |
| Diferencias entre el Outbox de inscription y el de payment causan inconsistencias | Documentar las diferencias en el comentario de cabecera de cada clase nueva (idealmente cero diferencias en la lógica del relay) |
| El fix del controller rompe el WIP que la usuaria estaba haciendo | Antes de tocar el archivo, leer el diff completo y entender qué endpoint estaba agregando, luego refactorizar respetando la intención |
| `RabbitMQConfig` colisiona con la config implícita de Spring Boot | Usar `@ConfigurationProperties` o config explícita en `application.yml`, validar que la app arranca antes del test E2E |
| El polling cada 1-2s genera carga innecesaria en BD | Aceptable para escala académica y demostración; queda documentado como decisión consciente con nota "en producción se ajustaría según latencia tolerable" |

## 6. Lo que habilita este sub-proyecto

- **Diagrama C4 nivel 4 del flujo de inscripción** (sub-proyecto de diagramas, ya tiene spec): podrá referenciar clases reales y existentes
- **Vista de Procesos de Kruchten** (mismo sub-proyecto): la secuencia "webhook → outbox → consumer → confirmación" será diagramada con base en código que funciona
- **Sub-proyecto de PPT**: tendrá un demo concreto y reproducible para mostrar en vivo
- **Sub-proyecto de QA con evidencia**: el test E2E es el primer artefacto de evidencia y se puede extender con pruebas de carga sobre el mismo flujo
