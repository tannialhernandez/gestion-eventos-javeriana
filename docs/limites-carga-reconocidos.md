# Limites de Carga Reconocidos

Proyecto: Plataforma de Gestion de Eventos Academicos, Pontificia Universidad Javeriana.

Este documento declara los limites de la evidencia local de carga para evitar conclusiones infladas. La suite K6 es reproducible y los escenarios defendibles pasan, pero no todas las metas de produccion se pueden certificar en un solo host Docker.

## 1. RNF-04 de 500 VUs no queda certificado localmente

El RNF-04 exige 500 usuarios concurrentes sin degradacion. En esta entrega se valida un escenario local de 150 VUs, porque el stack completo corre en un unico host con Docker Compose: PostgreSQL, Redis, RabbitMQ, Prometheus, K6 y los microservicios comparten CPU, memoria, red y disco.

Resultado defendible actual:

- `make -C load-tests load`
- 150 VUs maximos.
- p95 HTTP 28.15 ms.
- error rate 0%.
- threshold `p(99)<1500` en OK.

Trabajo pendiente:

- Ejecutar 500 VUs en AWS o runner dedicado.
- Separar el generador K6 de los servicios.
- Usar replicas horizontales de servicios stateless.
- Medir CPU, memoria, Hikari, Redis, RabbitMQ y PostgreSQL en paralelo.

## 2. Stress de 1000 VUs no es criterio de aceptacion

El stress test de 1000 VUs sirve para encontrar punto de quiebre, no para demostrar cumplimiento del SRS. En ejecuciones locales previas se observo degradacion severa antes de estabilizar el resultado. Esa observacion no debe extrapolarse a produccion porque el entorno local no representa escalado horizontal.

Trabajo pendiente:

- Caracterizar punto de quiebre exacto en infraestructura dedicada.
- Registrar curva VUs vs p95/p99/errores.
- Identificar si el cuello de botella real queda en PostgreSQL, Redis, RabbitMQ, Feign o Tomcat.

## 3. El load de 150 VUs es lectura/cache por defecto

El script `02-load-test.js` permite escrituras opcionales con `INSCRIPTION_INTERVAL`, pero la evidencia principal deja ese valor en `0`. La razon es tecnica y observable: cada reserva de cupo invalida el catalogo completo en Redis, lo cual mezcla dos preguntas distintas:

- capacidad de lectura/cache bajo carga sostenida;
- contencion de escritura sobre cupos.

La contencion de escritura no se ignora; queda probada de forma aislada y mas fuerte en `04-cupos-concurrencia.js`, donde 50 VUs compiten por 10 cupos y el threshold exige exactamente 10 confirmaciones.

## 4. Cache hit rate medido en escenario focalizado

La efectividad de cache se mide con `06-cache-effectiveness.js`, no como una inferencia del load general. Ese escenario limpia Redis, calienta una consulta y luego mide Prometheus/Lettuce:

- Redis GET observados: 201.
- Redis SETEX observados: 1.
- Hits estimados: 200.
- Hit rate estimado: 99.50%.

Esto evita atribuir mejoras de latencia a cache sin evidencia.

## 5. Aislamiento del host local

Las pruebas se ejecutaron con los recursos locales lo mas aislados posible. Contenedores Docker ajenos al proyecto pueden alterar p95/p99 por competencia de CPU y memoria; por eso los resultados locales deben usarse como evidencia operativa, no como certificacion de capacidad maxima de produccion.

## 6. Evidencia disponible

Archivos generados:

| Evidencia | Ruta |
|---|---|
| Load 150 VUs HTML | `load-tests/reports/02-load-test.html` |
| Load 150 VUs JSON | `load-tests/reports/02-load-test.json` |
| Load 150 VUs captura | `load-tests/reports/screenshots/02-load-test.png` |
| RNF-16 HTML | `load-tests/reports/04-cupos-concurrencia.html` |
| RNF-16 JSON | `load-tests/reports/04-cupos-concurrencia.json` |
| RNF-16 captura | `load-tests/reports/screenshots/04-cupos-concurrencia.png` |
| RNF-14 HTML | `load-tests/reports/05-circuit-breaker.html` |
| RNF-14 JSON | `load-tests/reports/05-circuit-breaker.json` |
| RNF-14 captura | `load-tests/reports/screenshots/05-circuit-breaker.png` |
| Cache HTML | `load-tests/reports/06-cache-effectiveness.html` |
| Cache JSON | `load-tests/reports/06-cache-effectiveness.json` |
| Cache captura | `load-tests/reports/screenshots/06-cache-effectiveness.png` |
| Grafica latencia p95 | `load-tests/reports/charts/latencia-p95-vs-umbral.svg` |
| Grafica RNF-16 | `load-tests/reports/charts/rnf16-sobrecupo-cero.svg` |
| Grafica tasas | `load-tests/reports/charts/tasas-cumplimiento.svg` |
| Grafica load | `load-tests/reports/charts/load-150-vus-resumen.svg` |
| Grafica latencia p95 PNG | `load-tests/reports/charts/png-full/latencia-p95-vs-umbral.png` |
| Grafica RNF-16 PNG | `load-tests/reports/charts/png-full/rnf16-sobrecupo-cero.png` |
| Grafica tasas PNG | `load-tests/reports/charts/png-full/tasas-cumplimiento.png` |
| Grafica load PNG | `load-tests/reports/charts/png-full/load-150-vus-resumen.png` |

Los graficos se regeneran con `make -C load-tests charts`. Son graficos agregados desde los resumenes JSON de K6; no son curvas temporales.

## Declaracion para sustentacion

La posicion honesta es:

> Validamos empiricamente los mecanismos criticos: sobrecupo cero con bloqueo, Circuit Breaker con degradacion controlada y cache Redis bajo lectura repetida. En local sostenemos 150 VUs sin errores. La meta de 500 VUs de RNF-04 queda pendiente de certificacion en infraestructura horizontal porque Docker Compose en un solo host no representa produccion.
