# Validacion de Diagramas C4 vs Sistema Real

## Alcance

Validacion liviana sobre los diagramas C4 existentes. No se encontro la ruta `docs/diagramas-c4/`; los diagramas C4 actuales estan distribuidos en:

- `docs/vista-fisica-deployment.md`: vista C4 de despliegue/contenedores con bloque Mermaid principal.
- `docs/c4-nivel3-componentes.md`: vistas C3/componentes por microservicio.
- `docs/diagramas/*.puml`: fuentes PlantUML ya exportadas a PNG para varias vistas.

Esta revision solo verifica los tres ajustes solicitados: `auth-service-stub`, WireMock como mock de Mercado Pago y frontend etiquetado como TypeScript.

## Resultado

| Diagrama | Elemento esperado | Presente antes | Accion |
|---|---|---|---|
| C2 / Deployment Mermaid (`docs/vista-fisica-deployment.md`) | `auth-service-stub` (`8081`) | No | Agregado como container local con JWT RS256, JWKS y 5 usuarios demo. |
| C2 / C3 | WireMock para Mercado Pago | Si | Mantener. Ya aparecia en C2 como `wiremock :8089 mock` y en C3 payment-service como adapter de pasarela. |
| C2 / Deployment Mermaid (`docs/vista-fisica-deployment.md`) | Frontend etiquetado TypeScript | No | Actualizado de `React 18 + Vite` a `React 18 + Vite + TypeScript`. |

## Cambios aplicados

Archivo modificado:

- `docs/vista-fisica-deployment.md`

Ajustes puntuales:

- Se agrego `auth-service-stub :8081` al bloque Docker del Mermaid principal.
- Se actualizo el routing Mermaid a `/auth/* -> 8081`, `/events/* -> 8082`, `/inscriptions/* -> 8083`, `/payments/* -> 8084`.
- Se actualizo la etiqueta del SPA a `React 18 + Vite + TypeScript`.
- Se mantuvo WireMock porque ya estaba representado como mock de pasarela.

## Componentes C3 revisados

| Diagrama C3 | Elemento revisado | Resultado |
|---|---|---|
| `docs/c4-nivel3-componentes.md` | WireMock / pasarela de pago | Presente en payment-service como adapter de pasarela. |
| `docs/diagramas/c4-3-payment-service.puml` | WireMock / pasarela de pago | Presente como driven adapter. |
| `docs/diagramas/c4-3-auth-service.puml` | auth-service conceptual | Presente como vista C3; Entrega 3 lo implementa como `auth-service-stub`. |

## Hallazgos

| ID | Hallazgo | Estado | Accion |
|---|---|---|---|
| C4-L-001 | El Mermaid C2 no mostraba `auth-service-stub` ni los puertos reales de Entrega 3. | Cerrado | Actualizado en `docs/vista-fisica-deployment.md`. |
| C4-L-002 | El Mermaid C2 etiquetaba el frontend como React + Vite, sin TypeScript. | Cerrado | Actualizado en `docs/vista-fisica-deployment.md`. |
| C4-L-003 | WireMock ya estaba representado como mock de pasarela. | Sin deuda | Sin cambios. |

## Conclusion

Los tres puntos de ajuste liviano quedaron cubiertos. No se reescribieron los diagramas C4 ni se agregaron componentes fuera del alcance. La deuda resultante es cero para los tres elementos solicitados.
