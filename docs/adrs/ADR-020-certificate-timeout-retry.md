# ADR-020: Estrategia de timeout y retry en Certificate Service

## Estado
Propuesta

## Fecha
2026-05-18

## Contexto
El Certificate Service generará 5000+ certificados PDF tras la
finalización de eventos masivos (RNF-04). La generación involucra:
- Carga de plantilla y datos del usuario.
- Renderizado HTML/CSS → PDF (Flying Saucer + OpenPDF).
- Generación de QR con código de verificación.
- Persistencia en almacenamiento (filesystem dev / S3 prod).
- Publicación de evento CertificadoGeneradoEvent al outbox.

Cualquier paso puede fallar por causas transitorias (timeout I/O,
contención de recursos, fallo temporal de almacenamiento). Sin
política explícita de timeout y retry:
- Generaciones colgadas indefinidamente saturan worker threads.
- Fallos transitorios se reportan como definitivos.
- El usuario percibe error donde podría haber sido recuperable.

El RNF-07 establece que el certificado debe estar disponible en <30s
desde el registro de asistencia. Esta restricción requiere que los
timeouts sean menores que ese umbral y que los reintentos sean rápidos.

## Decisión
Implementar política de timeout y retry con Resilience4j en cada
paso de la generación de certificado:

1. Timeout por operación: 25 segundos (margen de 5s respecto a RNF-07).
2. Retry con backoff exponencial: 3 intentos, base 2s (2s, 4s, 8s).
3. Circuit breaker en almacenamiento externo (S3): umbral 50% errores
   en ventana de 30s, recuperación tras 60s.
4. Tras agotar retries, certificado se marca `FALLO_DEFINITIVO` en BD
   y se publica al DLQ (`eventos.dlq`, ADR-019) con motivo del fallo.
5. Worker `@Scheduled` cada 5 minutos reintenta certificados en
   `FALLO_DEFINITIVO` con antigüedad menor a 24h.

Configuración Resilience4j (extracto):
```yaml
resilience4j:
  timelimiter.instances.certificate-gen.timeout-duration: 25s
  retry.instances.certificate-gen:
    max-attempts: 3
    wait-duration: 2s
    enable-exponential-backoff: true
    exponential-backoff-multiplier: 2
  circuitbreaker.instances.certificate-storage:
    failure-rate-threshold: 50
    wait-duration-in-open-state: 60s
```

## Alternativas descartadas
- Sin retry: descartada porque convierte fallos transitorios en
  errores permanentes para el usuario; viola RNF-08 (resiliencia).
- Retry infinito: descartada porque puede causar livelock y bloquea
  workers; los usuarios esperarían indefinidamente.
- Retry sin circuit breaker en almacenamiento: descartada porque
  amplifica fallas del S3 bajo presión (efecto avalancha).

## Consecuencias

### Positivas
- (+) Resiliencia ante fallos transitorios sin intervención humana.
- (+) Aislamiento de fallos del almacenamiento externo via circuit breaker.
- (+) Trazabilidad completa de certificados FALLIDOS para reconciliación.
- (+) Compatible con el SLA de <30s (RNF-07) al acotar el timeout.

### Negativas
- (-) Mayor latencia percibida en escenarios degradados (hasta ~14s
  de reintentos antes de fallar definitivamente).
- (-) Complejidad operativa: requiere monitorear backlog de FALLO_DEFINITIVO.

## Trazabilidad
- RF-008 Certificados digitales.
- RNF-04 Procesamiento asíncrono masivo.
- RNF-07 Tiempo de generación de certificado <30s.
- RNF-08 Disponibilidad y resiliencia.
- ADR-009 Resiliencia con Circuit Breaker (decisión hermana).
- ADR-019 Dead Letter Queue (destino de fallos definitivos).
- Patrón aplicado: Retry + Circuit Breaker (Hohpe & Woolf, EIP).

## Referencias
- Resilience4j documentation: https://resilience4j.readme.io/
- srs-casos-uso-pendientes.md §4 (flujo de generación de certificado)
