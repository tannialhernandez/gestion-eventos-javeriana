# ADR-021: Firma HMAC-SHA256 para tokens QR de asistencia

## Estado
Propuesta

## Fecha
2026-05-18

## Contexto
El registro de asistencia a eventos académicos (RF-008, RF-032) usa
códigos QR escaneados en la entrada física o virtual. Cada QR codifica
un token con: ID del evento, ID del asistente, timestamp de emisión y
ventana de validez.

Sin firma criptográfica, los tokens son falsificables: cualquiera puede
generar un QR válido conociendo el formato. Esto compromete la integridad
del registro de asistencia y la posterior emisión de certificados. La
consecuencia académica es que un participante podría registrar asistencia
sin haber estado presente, obteniendo un certificado inválido.

El RNF-08 (resistencia a enumeración y falsificación) requiere que el
mecanismo de verificación no sea bypasseable sin el secreto criptográfico.

## Decisión
Firmar todos los tokens QR de asistencia con HMAC-SHA256:

1. Secret compartido entre el servicio emisor (Event Service) y el
   validador (endpoint público de verificación). En producción, gestionado
   via AWS Secrets Manager o equivalente; en desarrollo, `application-dev.yml`.
2. Payload firmado: `{evento_id, asistente_id, emitido_en, valido_hasta}`.
3. Token format: `base64url(payload) + "." + base64url(hmac_signature)`.
4. Validación en endpoint público (sin auth requerida):
   - Decodificar payload.
   - Recalcular HMAC-SHA256 con el secret actual.
   - Comparar en tiempo-constante (evitar timing attacks).
   - Verificar que `now()` está dentro de `[emitido_en, valido_hasta]`.
5. Rotación de secret cada 90 días (proceso documentado en runbook
   operacional; fuera del alcance del prototipo académico).

## Alternativas descartadas
- Token sin firma: descartada por permitir falsificación trivial con
  solo conocer el formato del QR.
- JWT con RS256 (clave asimétrica): descartada por sobreingeniería.
  El QR solo se valida dentro del sistema propio; HMAC simétrico es
  suficiente y más eficiente.
- Token aleatorio + lookup en BD: descartada por requerir consulta a
  BD en cada escaneo; en un evento masivo con 500 asistentes simultáneos
  esto generaría carga innecesaria y latencia no determinista.

## Consecuencias

### Positivas
- (+) Integridad criptográfica del token sin consulta a BD en validación.
- (+) Validación O(1) sin red ni storage.
- (+) Compatible con verificación offline (conectividad degradada en
  el lugar del evento).
- (+) Falsificación requiere conocer el secret; imposible por enumeración.

### Negativas
- (-) Compromiso del secret invalida la confianza en todos los tokens
  emitidos (requiere rotación y reemisión).
- (-) Sin revocación granular: un token firmado válido lo es hasta
  que expira por tiempo. No es posible invalidar un token individual.
- (-) Requiere proceso de rotación de secret y comunicación entre servicios.

## Trazabilidad
- RF-008 Certificados digitales.
- RF-032 Registro de asistencia por QR.
- RNF-05 Seguridad.
- RNF-08 Resistencia a enumeración de verificación.
- Patrón aplicado: Message Authentication Code (criptografía simétrica, RFC 2104).

## Referencias
- RFC 2104: HMAC: Keyed-Hashing for Message Authentication.
- srs-casos-uso-pendientes.md §3 y §6 (flujo de registro de asistencia y
  verificación pública de certificados)
