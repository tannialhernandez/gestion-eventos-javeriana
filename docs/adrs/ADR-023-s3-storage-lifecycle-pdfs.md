# ADR-023: Política de almacenamiento S3 y ciclo de vida de PDFs

## Estado
Propuesta

## Fecha
2026-05-18

## Contexto
El sistema genera certificados PDF que deben estar disponibles para
descarga durante un período extendido (RNF-09: retención mínima 5 años).
Además, los certificados tienen diferentes patrones de acceso a lo largo
del tiempo:
- Alta frecuencia de acceso en los primeros 30 días (descarga inmediata
  por los participantes).
- Frecuencia decreciente entre 30 días y 1 año (verificación occasional
  por terceros como empleadores o instituciones).
- Acceso muy esporádico después de 1 año (verificación histórica).

Sin una política de ciclo de vida, todos los PDFs permanecen en la clase
de almacenamiento S3 Standard indefinidamente, generando un costo
creciente que eventualmente viola la restricción de $200/mes (RNF-06).

## Decisión
Configurar el bucket S3 `eventos-certificados-javeriana` con las
siguientes políticas de ciclo de vida:

1. **Días 0-30:** S3 Standard. Acceso rápido para descarga inmediata.

2. **Días 31-365:** Transición automática a S3 Standard-IA
   (Infrequent Access). ~60% más económico, latencia de primer byte
   aceptable para verificación occasional.

3. **Día 366 - Año 5:** Transición automática a S3 Glacier Instant
   Retrieval. ~90% más económico que Standard. Latencia milisegundos
   (aceptable para verificación histórica, no para descarga masiva).

4. **Tras 5 años (1826 días):** Expiración automática (eliminación).
   Cumple con la retención mínima de RNF-09. Si se requiere mayor
   retención, cambiar el rule a transición a S3 Glacier Deep Archive
   en lugar de expiración.

5. **Naming convention del objeto S3:**
   `{evento_id}/{usuario_id}/{certificado_id}_{año_evento}.pdf`

   El año del evento en el nombre permite aplicar reglas de lifecycle
   diferenciadas por antigüedad del evento si fuera necesario en el
   futuro.

6. **Backups:** Bucket destino con versionado habilitado y replicación
   cross-region (us-east-1 → us-west-2) para certificados de más de 1
   año. Costo adicional mínimo dado el volumen esperado (<100 MB/año).

Configuración AWS lifecycle rule (extracto JSON):
```json
{
  "Rules": [{
    "ID": "certificados-lifecycle",
    "Filter": {"Prefix": ""},
    "Status": "Enabled",
    "Transitions": [
      {"Days": 30,  "StorageClass": "STANDARD_IA"},
      {"Days": 365, "StorageClass": "GLACIER_IR"}
    ],
    "Expiration": {"Days": 1826}
  }]
}
```

## Alternativas descartadas
- S3 Standard permanente: descartada por costo creciente; viola restricción
  presupuestal ($200/mes) a escala de cientos de eventos/año.
- Almacenamiento propio (servidor de archivos): descartada por costo
  operativo y ausencia de durabilidad (S3 tiene 99.999999999% durabilidad).
- S3 Glacier desde el inicio: descartada porque la descarga en los
  primeros días es frecuente; latencia de Glacier degradaría UX de descarga
  inmediata.
- Expiración en 1 año: descartada porque RNF-09 exige retención de 5 años
  como mínimo para trazabilidad académica.

## Consecuencias

### Positivas
- (+) Costo optimizado automáticamente a lo largo del tiempo.
- (+) Cumplimiento de RNF-09 (retención 5 años) sin intervención manual.
- (+) Durabilidad y disponibilidad garantizadas por SLA de AWS S3.
- (+) Transparente para los microservicios: la URL de descarga funciona
  igual independientemente de la clase de almacenamiento.

### Negativas
- (-) Complejidad de configuración inicial (reglas de lifecycle en Terraform
  o CloudFormation — fuera del alcance del prototipo con Docker Compose).
- (-) Latencia de Glacier Instant Retrieval puede ser perceptible en
  accesos a certificados de >1 año (aunque milisegundos, no segundos).
- (-) Expiración automática tras 5 años puede ser problemática si los
  requerimientos de retención cambian. Mitigación: revisar la política
  anualmente.

## Trazabilidad
- RNF-09 Retención de certificados 5 años.
- RF-008 Certificados digitales.
- RF-031 Verificación pública por QR.
- ADR-020 Timeout y retry en Certificate Service (gestor del upload a S3).
- Patrón aplicado: Object Storage + Lifecycle Management.

## Referencias
- AWS S3 Lifecycle documentation.
- srs-casos-uso-pendientes.md §10 (trazabilidad RNF-09)
