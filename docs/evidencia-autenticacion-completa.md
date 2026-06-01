# Evidencia de Autenticacion y Autorizacion Completa

## Resumen

La verificacion cubre autenticacion con los 5 usuarios demo declarados en `auth-service-stub/src/main/resources/application.yml`, proteccion de rutas privadas, manejo de credenciales invalidas, tokens expirados o malformados, logout completo, persistencia de sesion tras refresh, validacion de claims JWT en frontend y exposicion del endpoint JWKS.

Durante esta verificacion se detecto y corrigio un hallazgo real: el frontend restauraba una sesion desde `sessionStorage` si existia `expiresAt`, aunque el JWT estuviera expirado o malformado. La restauracion ahora valida el JWT antes de marcar al usuario como autenticado.

## Usuarios demo validados

| Usuario | Email | Rol | Test | Captura |
|---|---|---|---|---|
| Laura Participante | `laura.participante@javeriana.edu.co` | PARTICIPANTE | `07-auth-multi-usuario.spec.ts` | `frontend/test-results/evidence/auth/07-login-laura-participante.png` |
| Diego Participante | `diego.participante@javeriana.edu.co` | PARTICIPANTE | `07-auth-multi-usuario.spec.ts` | `frontend/test-results/evidence/auth/07-login-diego-participante.png` |
| Carlos Organizador | `carlos.organizador@javeriana.edu.co` | ORGANIZADOR | `07-auth-multi-usuario.spec.ts` | `frontend/test-results/evidence/auth/07-login-carlos-organizador.png` |
| Ana Administradora | `ana.admin@javeriana.edu.co` | ADMIN | `07-auth-multi-usuario.spec.ts` | `frontend/test-results/evidence/auth/07-login-ana-admin.png` |
| Sofia Mesa de Ayuda | `sofia.soporte@javeriana.edu.co` | PARTICIPANTE | `07-auth-multi-usuario.spec.ts` | `frontend/test-results/evidence/auth/07-login-sofia-soporte.png` |

Nota: el stub actual no declara rol `SERVICE`; se valida exactamente lo configurado en `application.yml` para no inventar usuarios ni credenciales.

## Matriz RBAC frontend observada

| Rol | Rutas privadas SPA | Acciones visibles | Estado Entrega 3 |
|---|---|---|---|
| PARTICIPANTE | `/catalogo`, `/eventos/:eventoId`, `/inscripciones/:inscripcionId/pago`, `/confirmacion/:inscripcionId` | Consulta catalogo y crea inscripcion | Validado con `08-rbac-frontend.spec.ts` |
| ORGANIZADOR | `/catalogo`, `/eventos/:eventoId`, `/inscripciones/:inscripcionId/pago`, `/confirmacion/:inscripcionId` | Misma superficie SPA del MVP | Login y acceso privado validados con `07-auth-multi-usuario.spec.ts` |
| ADMIN | `/catalogo`, `/eventos/:eventoId`, `/inscripciones/:inscripcionId/pago`, `/confirmacion/:inscripcionId` | Misma superficie SPA del MVP | Login y acceso privado validados con `07-auth-multi-usuario.spec.ts` |
| Sin sesion | Ninguna ruta privada | Redireccion a `/login` | Validado con `08-rbac-frontend.spec.ts` |

No existen rutas administrativas funcionales en el SPA de Entrega 3. La ruta `/admin` no esta declarada y se resuelve por wildcard hacia `/catalogo`; si no hay sesion, la proteccion vuelve a redirigir a `/login`.

## Matriz de cobertura

| Escenario | Test | Evidencia | Resultado |
|---|---|---|---|
| Login con 5 usuarios demo | `frontend/e2e/07-auth-multi-usuario.spec.ts` | `frontend/test-results/evidence/auth/07-login-*.png` | OK |
| RBAC: PARTICIPANTE accede a catalogo | `frontend/e2e/08-rbac-frontend.spec.ts` | Playwright report | OK |
| RBAC: PARTICIPANTE crea inscripcion | `frontend/e2e/08-rbac-frontend.spec.ts` | Playwright report | OK |
| Ruta administrativa inexistente | `frontend/e2e/08-rbac-frontend.spec.ts` | Redireccion segura a catalogo | OK |
| Acceso sin login a `/catalogo` | `frontend/e2e/08-rbac-frontend.spec.ts` | `frontend/test-results/evidence/auth/08-sin-login-redirige.png` | OK |
| Credenciales invalidas | `frontend/e2e/01-login-flow.spec.ts` | `frontend/test-results/evidence/auth/08-credenciales-invalidas.png` | OK |
| Email malformado | `frontend/e2e/01-login-flow.spec.ts` | Validacion nativa HTML antes del backend | OK |
| Token expirado | `frontend/e2e/09-token-expirado.spec.ts` | `frontend/test-results/evidence/auth/09-token-expirado.png` | OK |
| Token malformado | `frontend/e2e/09-token-expirado.spec.ts` | Limpieza de `sessionStorage` | OK |
| Logout limpia storage | `frontend/e2e/10-logout-completo.spec.ts` | `frontend/test-results/evidence/auth/10-logout-completo.png` | OK |
| Back tras logout | `frontend/e2e/10-logout-completo.spec.ts` | No recupera sesion protegida | OK |
| Refresh mantiene sesion activa | `frontend/e2e/11-refresh-session.spec.ts` | `frontend/test-results/evidence/auth/11-refresh-session.png` | OK |
| Claims JWT en frontend | `frontend/src/features/auth/jwt-claims.test.ts` | `npm run test` | OK |
| JWKS endpoint | `frontend/e2e/12-jwks-endpoint.spec.ts` | HTTP 200 via `/auth-api/api/v1/auth/.well-known/jwks.json` | OK |
| Login emite JWT compatible | `frontend/e2e/12-jwks-endpoint.spec.ts` | `accessToken` de 3 segmentos, `tokenType=Bearer` | OK |

## Validacion de claims JWT

Se creo `frontend/src/features/auth/jwt-utils.ts` como modulo puro para validar:

- Parseo de payload JWT base64url.
- Extraccion del claim `roles`.
- Deteccion de `exp` en el pasado.
- Rechazo de token malformado.

El `AuthContext` usa esta validacion al restaurar sesion desde `sessionStorage`, de forma que un token expirado o malformado limpia la sesion y redirige a `/login`.

## Resultado de ejecucion

| Comando | Resultado |
|---|---|
| `npm run lint` | OK, 71 archivos TypeScript validados |
| `npm run build` | OK |
| `npm run test` | OK, 52/52 tests Vitest |
| `npm run test:coverage` | OK, statements 87.82%, branches 80.52% |
| `npm run test:e2e` | OK, 28/28 tests Playwright |
| `npm run smoke:e2e` | OK, pago `CONFIRMADO` |

## Evidencia visual

Las capturas de autenticacion quedan en:

- `frontend/test-results/evidence/auth/07-login-laura-participante.png`
- `frontend/test-results/evidence/auth/07-login-diego-participante.png`
- `frontend/test-results/evidence/auth/07-login-carlos-organizador.png`
- `frontend/test-results/evidence/auth/07-login-ana-admin.png`
- `frontend/test-results/evidence/auth/07-login-sofia-soporte.png`
- `frontend/test-results/evidence/auth/08-credenciales-invalidas.png`
- `frontend/test-results/evidence/auth/08-sin-login-redirige.png`
- `frontend/test-results/evidence/auth/09-token-expirado.png`
- `frontend/test-results/evidence/auth/10-logout-completo.png`
- `frontend/test-results/evidence/auth/11-refresh-session.png`

## Conclusiones

La autenticacion queda validada de extremo a extremo para los usuarios demo reales, con JWT RS256 emitido por `auth-service-stub`, persistencia de sesion en `sessionStorage`, proteccion de rutas privadas y limpieza ante credenciales, token expirado, token malformado o logout. La autorizacion frontend actual es coherente con el alcance implementado: las rutas privadas requieren sesion activa y no existen rutas administrativas funcionales en Entrega 3.
