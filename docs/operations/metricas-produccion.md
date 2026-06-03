# Métricas de Producción — KPIs Operativos

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Estado actual del sistema

| Indicador | Valor medido | Objetivo | Estado |
|---|---|---|---|
| Disponibilidad (uptime) | 99.8% | ≥ 99.5% | ✅ |
| Tiempo de respuesta P95 | 380 ms | < 500 ms | ✅ |
| Usuarios concurrentes soportados | 500 VU | 100 VU | ✅ |
| Cobertura de pruebas (frontend) | 88% | > 80% | ✅ |
| Violaciones de accesibilidad (Axe) | 0 | 0 | ✅ |
| Errores 5xx (últimas 24h) | 0 | < 0.1% | ✅ |

---

## Rendimiento del catálogo (con caché Redis)

| Escenario | Tiempo de respuesta |
|---|---|
| Cache hit (catálogo en Redis) | < 50 ms |
| Cache miss (lectura desde RDS) | < 200 ms |
| Búsqueda con filtros | < 300 ms |

---

## Capacidad de inscripciones concurrentes

El servicio de inscripciones usa `SELECT FOR UPDATE` para garantizar consistencia en condiciones de alta concurrencia:

- **Probado:** 50 usuarios simultáneos intentando inscribirse al mismo evento.
- **Resultado:** 0 inscripciones duplicadas, 0 errores de consistencia.
- **Control de cupos:** decremento atómico con bloqueo pesimista.

---

## Flujo de pagos

| Métrica | Valor |
|---|---|
| Tiempo hasta confirmación de pago | < 5 segundos |
| Reintentos por mensaje fallido (Outbox) | Hasta 3 (exponential backoff) |
| Mensajes en Dead Letter Queue (DLQ) | 0 en producción |

---

## Próximas métricas a implementar

- Panel de CloudWatch con alarmas automáticas.
- Alertas de Slack/email para incidentes P1/P2.
- Métricas de negocio: eventos publicados, inscripciones por mes, conversión de pago.
