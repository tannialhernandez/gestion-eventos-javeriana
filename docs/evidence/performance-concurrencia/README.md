# Evidencia para presentacion: rendimiento y concurrencia

## 1. Muchos usuarios al tiempo

Usa la prueba K6 de carga sostenida:

```bash
cd load-tests
make load
```

Que se muestra:

- Escenario: `150` usuarios virtuales sostenidos.
- Duracion medida: `326.9s`.
- Requests HTTP: `821`.
- `checks`: `1.0` con `0` fallos.
- `http_req_failed`: `0`.
- Latencia HTTP promedio: `24.17ms`.
- Latencia HTTP `p95`: `52.81ms`.
- Umbrales cumplidos: `p95 < 800ms`, `p99 < 1500ms`, `error rate < 2%`.

Archivos listos para presentar:

- Reporte HTML: `load-tests/reports/02-load-test.html`
- Captura: `load-tests/reports/screenshots/02-load-test.png`
- Grafico resumen: `load-tests/reports/charts/png-full/load-150-vus-resumen.png`
- Grafico p95 vs umbral: `load-tests/reports/charts/png-full/latencia-p95-vs-umbral.png`

Frase para la diapositiva:

> Con 150 usuarios virtuales concurrentes, el sistema mantuvo 0% de errores, checks al 100% y p95 de 52.81ms, por debajo del umbral definido de 800ms.

## 2. Concurrencia sobre cupos

Usa la prueba K6 de cupos concurrentes:

```bash
cd load-tests
make cupos
```

Que se muestra:

- Escenario: `50` usuarios/iteraciones compitiendo por un evento limitado.
- Inscripciones confirmadas: `10`.
- Rechazos por cupo: `40`.
- `checks`: `1.0` con `0` fallos.
- `http_req_failed`: `0`.
- No hubo respuestas `5xx`.
- Umbral de integridad cumplido: confirmadas `10`, rechazadas `40`.

Archivos listos para presentar:

- Reporte HTML: `load-tests/reports/04-cupos-concurrencia.html`
- Captura: `load-tests/reports/screenshots/04-cupos-concurrencia.png`
- Grafico RNF-16: `load-tests/reports/charts/png-full/rnf16-sobrecupo-cero.png`
- Grafico temporal: `load-tests/reports/timeseries-charts/rnf16-cupos-tiempo.svg`

Frase para la diapositiva:

> Bajo concurrencia, el bloqueo pesimista evita el sobrecupo: solo se confirman los cupos disponibles y los intentos restantes reciben rechazo controlado, sin caidas ni errores 5xx.

## 3. Demo exacta de dos usuarios al mismo tiempo en AWS

Ejecuta:

```bash
node scripts/demo-concurrencia-dos-usuarios-aws.mjs
```

La demo hace esto:

- Crea un evento temporal con `1` cupo como ORGANIZADOR.
- Crea una tarifa pagada de prueba.
- Envia el evento a revision y lo aprueba como ADMIN.
- Lanza dos inscripciones en paralelo. Por defecto usa Laura y Sofia; puedes cambiar los participantes con `AWS_PRIMER_EMAIL`, `AWS_PRIMER_LABEL`, `AWS_SEGUNDO_EMAIL` y `AWS_SEGUNDO_LABEL`.
- Valida que exactamente una inscripcion sea exitosa y la otra sea `409` por cupo.
- Aprueba el pago simulado de la inscripcion exitosa.
- Cancela la inscripcion para simular reembolso y liberar el cupo.
- Cancela el evento temporal al final.

Resultado esperado para mostrar:

```text
Laura: HTTP 201 en Xms estado=PENDIENTE_PAGO
Diego: HTTP 409 en Xms estado=sin_cupos_disponibles
cupoDespuesReserva: 0
pagoSimulado: CONFIRMADO
cancelacion: CANCELADA
cupoDespuesCancelacion: 1
```

Si los participantes se invierten, tambien es correcto. Lo importante es que haya `1` exito, `1` rechazo por cupo, cupo en `0` tras reservar y cupo en `1` tras cancelar.

Evidencia AWS validada:

- JSON de corrida final: `docs/evidence/performance-concurrencia/aws-concurrencia-dos-usuarios-2026-06-03.json`
- Resultado observado con Laura/Diego: Laura `201` en `PENDIENTE_PAGO`, Diego `409` por cupo, pago simulado `CONFIRMADO`, cupo `0` tras reserva y cupo `1` tras cancelacion.

## 4. Orden sugerido de diapositivas

1. Arquitectura de resiliencia: API Gateway/CloudFront, servicios separados, DB por servicio, RabbitMQ/outbox, cache.
2. Carga sostenida: captura de K6 con 150 VUs y metricas p95/error rate.
3. Concurrencia de cupos: grafico RNF-16 con confirmadas vs rechazadas.
4. Demo dos usuarios: salida del script Laura/Diego en paralelo.
5. Conclusiones: no sobrecupo, no cupo negativo, pago confirmado, cancelacion libera cupo.
