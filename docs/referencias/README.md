# Documentos de Referencia Normativa

Este directorio contiene los documentos normativos del proyecto que se 
referencian en el código, los ADRs y los documentos de arquitectura.

## Documentos requeridos (colocar manualmente)

Los siguientes archivos deben colocarse en este directorio por el usuario.
**No se commitean al repositorio** (están en `.gitignore` si son PDFs).

| Archivo esperado | Descripción | Referenciado en |
|---|---|---|
| `sad-eventos-academicos-v3.pdf` | Software Architecture Document v3.0 | ADRs, README.md |
| `srs-eventos-academicos-v3.pdf` | Software Requirements Specification v3.0 | ADRs, matriz-trazabilidad.md |

## Cómo añadir los documentos

```bash
# Copiar los PDFs a este directorio
cp /ruta/a/sad-eventos-academicos-v3.pdf docs/referencias/
cp /ruta/a/srs-eventos-academicos-v3.pdf docs/referencias/
```

## Documentos en Markdown (versionados en el repo)

Los siguientes documentos Markdown son la fuente de verdad versionada:

| Archivo | Descripción |
|---|---|
| [../comportamiento-runtime-inscripcion-pago.md](../comportamiento-runtime-inscripcion-pago.md) | Flujos dinámicos de inscripción y pago |
| [../matriz-trazabilidad.md](../matriz-trazabilidad.md) | Trazabilidad RF → RN → CU → CA → ADR |
| [../modelo-datos-conceptual.md](../modelo-datos-conceptual.md) | Entidades, relaciones y estados de dominio |
| [../srs-casos-uso-pendientes.md](../srs-casos-uso-pendientes.md) | Casos de uso del SRS v2.0 y reglas propuestas |
| [../build-conventions.md](../build-conventions.md) | Convenciones de build y calidad |
