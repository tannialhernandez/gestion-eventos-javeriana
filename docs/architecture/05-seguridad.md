# Modelo de Seguridad — Plataforma de Gestión de Eventos Académicos

**PUJ · Versión:** 2.0 · **Actualizado:** 2026-06-03

---

## Autenticación

El sistema usa **JWT RS256** (JSON Web Tokens firmados con RSA de 2048 bits):

1. El usuario envía credenciales a `POST /api/v1/auth/login`.
2. El auth-service emite un JWT firmado con la clave privada RSA.
3. El JWT se almacena en `sessionStorage` del navegador (no en cookies).
4. Cada microservicio valida la firma del JWT usando la **clave pública**, disponible en `GET /api/v1/auth/.well-known/jwks.json`.
5. No se requiere llamada al auth-service en cada solicitud — validación stateless.

**Vida útil del token:** 1 hora. Sin refresh token (Fase 2).

---

## Autorización (RBAC)

| Rol | Capacidades |
|---|---|
| `PARTICIPANTE` | Leer catálogo, inscribirse, pagar, ver propias inscripciones |
| `ORGANIZADOR` | Todo lo de participante + crear/editar eventos propios, enviar a revisión |
| `ADMIN` | Todo lo anterior + aprobar/rechazar eventos, gestionar todo el catálogo |
| `SERVICE` | Operaciones machine-to-machine (e.g., actualizar cupos) |

El rol está contenido en el claim `roles` del JWT y es validado por Spring Security en cada microservicio de forma independiente.

---

## Controles de seguridad aplicados

| Control | Implementación |
|---|---|
| Transporte cifrado | HTTPS en CloudFront + ALB |
| Autenticación fuerte | JWT RS256 — firma criptográfica |
| Autorización granular | RBAC por endpoint en cada microservicio |
| Protección CSRF | No aplica — API stateless sin cookies de sesión |
| Inyección SQL | JPA/Hibernate con queries tipadas — sin SQL concatenado |
| XSS | React escapa HTML automáticamente; `sanitizeText()` en SPA |
| Secretos | AWS Secrets Manager (producción) — variables de entorno en contenedor |
| HMAC | Webhooks de pago validados con HMAC-SHA256 (ADR-021) |
| Logs seguros | No se registran tokens, contraseñas ni datos personales en logs |

---

## Flujo de seguridad extremo a extremo

```
Usuario                SPA (React)         Microservicio
   │                        │                    │
   │── credenciales ────────►│                    │
   │                        │── POST /auth/login ─►│ (auth-service)
   │                        │◄─ JWT firmado RS256 ──│
   │◄──── sesión activa ─────│                    │
   │                        │                    │
   │── acción protegida ─────►│                   │
   │                        │── Authorization: Bearer <JWT> ──►│
   │                        │                    │── valida firma (JWKS)
   │                        │                    │── verifica rol
   │                        │◄─ respuesta ─────────│
   │◄─ resultado ────────────│                    │
```

---

## Pendiente (Fase 2)

- Integración con Azure AD de la Pontificia Universidad Javeriana (OIDC / OAuth 2.0).
- Refresh tokens con revocación centralizada.
- MFA para roles ADMIN y ORGANIZADOR.
- Rotación periódica del par de claves RSA.
