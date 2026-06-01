# Alcance y Decisiones de Mocks

Proyecto: Plataforma de Gestion de Eventos Academicos, Pontificia Universidad Javeriana.

Fecha: 2026-05-31.

## Resumen Ejecutivo

La Entrega 3 valida el flujo critico completo con backend real y SPA real:

```text
login -> catalogo -> inscripcion -> pago -> confirmacion
```

El login ya no firma JWT desde el navegador. El SPA consume un endpoint HTTP real en `auth-service-stub`, que emite JWT RS256 con la misma clave publica configurada en `event-service` e `inscription-service`.

`auth-service-stub` no se declara como microservicio productivo del SAD. Es un adaptador minimo para demo E2E, reemplazable por Azure AD, Keycloak u otro IdP.

## Auth: Stub vs Servicio Conceptual del SAD

El SAD mantiene `auth-service` como componente conceptual de identidad. En Entrega 3 se implementa `auth-service-stub` para cerrar el flujo E2E sin desplegar un IdP completo.

Alcance exacto del stub:

- Endpoint real `POST /api/v1/auth/login`.
- Endpoint real `GET /api/v1/auth/.well-known/jwks.json`.
- 5 usuarios demo en `application.yml`.
- Emision de JWT RS256 con la misma clave publica configurada en los microservicios.
- Sin base de datos propia, sin refresh token, sin revocacion centralizada.

Decision consciente sobre OAuth 2.0:

`auth-service-stub` se comporta como un flujo equivalente a Resource Owner Password Credentials Grant, descrito en OAuth 2.0 RFC 6749 seccion 4.3. Esta decision se acepta solo para demo local y sustentacion porque:

- Los usuarios son cuentas demo, no identidades reales.
- No hay almacenamiento persistente de credenciales.
- El objetivo es validar consumo de JWT por el SPA y validacion stateless por los microservicios.
- El flujo no se promueve para produccion.

Roadmap Fase 2:

1. Reemplazar password grant por Authorization Code + PKCE.
2. Registrar el SPA como cliente publico OIDC.
3. Migrar usuarios demo a Azure AD tenant Javeriana o Keycloak federado.
4. Consumir JWKS del IdP real y retirar la clave privada demo del repositorio.
5. Implementar refresh token con rotacion o silent renewal segun politica del IdP.
6. Agregar revocacion, logout federado y auditoria de sesiones.

## Real vs Decision Documentada

| Componente | Estado en Entrega 3 | Evidencia | Reemplazo productivo |
|---|---|---|---|
| `event-service` | Real | Catalogo y tarifas leidos desde PostgreSQL real via API | Se mantiene |
| `inscription-service` | Real | Crea inscripciones con JWT validado, cupos y outbox | Se mantiene |
| `payment-service` | Real con pasarela simulada | Webhook HMAC confirma pago | Integracion real Mercado Pago/PSP |
| `auth-service-stub` | Stub HTTP minimo | `POST /api/v1/auth/login` emite JWT RS256 | Azure AD, Keycloak u OIDC institucional |
| JWT frontend | Real como consumo, no emision | Token llega desde `auth-service-stub` y vive en `sessionStorage` | OAuth/OIDC con PKCE |
| Usuarios | Seed demo en YAML | 5 usuarios demo sin BD propia | Directorio institucional |
| Refresh token | No implementado | Declarado en `TECH_DEBT.md` | Refresh token/rotacion/revocacion |
| Pasarela de pago | Simulador con HMAC | Webhook firmado hacia `payment-service` | PSP real + validacion de firma real |
| Observabilidad frontend | Basica | Errores tipados y UX de degradacion | RUM/tracing frontend |

## Mocks Intencionales

| Mock / Stub | Por que existe | Riesgo si se malinterpreta | Control aplicado |
|---|---|---|---|
| `auth-service-stub` | Entrega 3 no incluye IdP institucional; se necesitaba login HTTP defendible | Confundirlo con auth productivo | Nombre explicito `stub`, documento de alcance y TECH_DEBT |
| Usuarios demo en `application.yml` | Evita una BD adicional para un componente no productivo | Credenciales fijas en repo | Solo entorno local/e2e; no usar en prod |
| Clave privada RSA de prueba en stub | Necesaria para emitir tokens que validan los servicios locales | Exposicion de secreto de prueba | Marcada como clave demo; prod usa Secrets Manager/IdP |
| Pasarela simulada | Permite validar flujo de pago y webhook sin PSP externo | Creer que hay cobro real | Webhook HMAC real, proveedor marcado `simulador` |
| Captura de `503 + Retry-After` via CDP | Permite evidencia visual reproducible del Circuit Breaker frontend | No sustituye prueba backend de resiliencia | Complementa K6/Prompt 24.1, no la reemplaza |

## Decisiones Tecnicas

| Decision | Justificacion |
|---|---|
| `auth-service-stub` corre en `8081` | `payment-service` ya ocupa `8084`; mover pagos romperia el stack E2E existente. El puerto `8081` queda reservado para auth/gateway local. |
| Password grant solo en stub | RFC 6749 seccion 4.3 se usa como atajo consciente para usuarios demo | Prohibido para produccion; reemplazo PKCE en Fase 2 |
| JWT en `sessionStorage` | Reduce persistencia ante XSS frente a `localStorage`; suficiente para demo sin refresh token. |
| Sin refresh token | Entrega 3 valida flujo critico, no ciclo completo de identidad. Queda en deuda tecnica. |
| Sin BD propia de auth | El stub no es microservicio productivo; una BD nueva daria falsa sensacion de alcance mayor. |
| JWKS publicado por el stub | Deja claro el contrato OIDC/JWKS que usaria un IdP real. |

## Roadmap Post-MVP

1. Sustituir `auth-service-stub` por Azure AD tenant Javeriana, Keycloak o IdP OIDC institucional.
2. Migrar el SPA a Authorization Code + PKCE.
3. Agregar refresh token con rotacion, revocacion y expiracion por politica.
4. Mover claves a AWS Secrets Manager o configuracion gestionada del IdP.
5. Reemplazar usuarios YAML por directorio institucional o tabla de usuarios gobernada.
6. Integrar PSP real para pagos, manteniendo verificacion HMAC/firma.
7. Agregar pruebas de seguridad para expiracion, revocacion, roles y CORS.

## Como Defenderlo

La frase corta para sustentacion:

> "No presentamos un IdP productivo. Presentamos un stub HTTP minimo y documentado que emite JWT RS256 reales, consumidos por el SPA y validados por los microservicios. La decision evita que el navegador firme tokens y deja un contrato reemplazable por OIDC/JWKS en produccion."
