# ADR-022: API Gateway — rate limiting, routing y políticas transversales

## Estado
Propuesta

## Fecha
2026-05-18

## Contexto
El sistema expone múltiples microservicios (event, inscription, payment,
auth, notification, certificate) cada uno con su propio puerto y endpoints.
Sin un punto de entrada único:
- El frontend conoce demasiados endpoints y se acopla a la topología.
- Las políticas transversales (rate limit, CORS, autenticación,
  correlation-id) se duplican en cada servicio.
- No hay forma simple de aplicar throttling global ante picos de
  tráfico declarados en RNF-04 (5000 usuarios concurrentes).

La resistencia a enumeración de verificación (RNF-08) específicamente
requiere rate limiting en el endpoint público de verificación de QR para
prevenir ataques de fuerza bruta sobre el espacio de tokens.

## Decisión
Introducir un API Gateway basado en Spring Cloud Gateway como único
punto de entrada para clientes externos:

1. **Routing por prefijo de path:**
   - `/api/eventos/*` → event-service:8081
   - `/api/inscripciones/*` → inscription-service:8082
   - `/api/pagos/*` → payment-service:8084
   - `/api/auth/*` → auth-service:8080
   - `/api/certificados/*` → certificate-service:8085

2. **Rate limiting con Redis (token bucket):**
   - Endpoints de lectura autenticados: 100 req/s por usuario.
   - Endpoints de escritura autenticados: 20 req/s por usuario.
   - Endpoints públicos (catálogo, verificación QR): 50 req/s por IP.
   - Respuesta al exceder límite: HTTP 429 con header `Retry-After`.

3. **Validación JWT delegada al gateway:** extrae claims del token,
   propaga como headers internos (`X-User-Id`, `X-User-Roles`, `X-User-Email`).
   Los microservicios confían en estos headers sin re-validar el JWT.

4. **CORS configurado centralmente:** elimina necesidad de configurarlo
   en cada microservicio individualmente.

5. **Correlation-ID:** generado (`UUID.randomUUID()`) o propagado
   (`X-Correlation-Id`) en cada request; incluido en todos los logs.

6. **Métricas Micrometer + Prometheus** para latencia y conteo por ruta.

## Alternativas descartadas
- Sin gateway (frontend con múltiples URLs): descartada por acoplamiento
  y duplicación de cross-cutting concerns en cada servicio.
- Nginx como proxy: descartada porque limita la integración con el
  ecosistema Spring Boot (filtros dinámicos, circuit breakers declarativos,
  métricas via Actuator). Spring Cloud Gateway es nativo al stack elegido.
- Kong o Envoy: descartada por complejidad operativa y costos de
  aprendizaje que no se justifican con el equipo y presupuesto del proyecto.

## Consecuencias

### Positivas
- (+) Punto único de entrada: simplifica cliente frontend y DNS.
- (+) Cross-cutting concerns implementados una sola vez.
- (+) Rate limiting protege el sistema ante picos y ataques (RNF-04, RNF-08).
- (+) Los microservicios internos no necesitan validar JWT ni gestionar CORS.

### Negativas
- (-) El gateway es un punto único de fallo si no se alta disponibiliza.
  Mitigación: múltiples réplicas del gateway con ADR-018 (ShedLock) para
  tareas programadas.
- (-) Latencia adicional (~5-10ms) por el hop extra.
- (-) Requiere Redis para rate limiting distribuido (infraestructura
  adicional, aunque ya está presente para el caché de catálogo ADR-003).

## Trazabilidad
- RNF-04 Concurrencia masiva (5000 usuarios).
- RNF-06 API <500ms p99 (timeout de gateway: 5s).
- RNF-08 Resistencia a enumeración.
- RF-001 Acceso autenticado al sistema.
- ADR-003 Redis ya disponible (reutilizado para rate limiting).
- ADR-007 Autenticación OAuth 2.0 / OIDC (JWT validado en gateway).
- Patrón aplicado: API Gateway (Richardson, Microservices Patterns cap. 8).

## Referencias
- Spring Cloud Gateway documentation.
- Richardson, C. (2018). Microservices Patterns, capítulo 8.
- srs-casos-uso-pendientes.md §10 (trazabilidad RNF-08)
