# Diagnostico y Resolucion - ENOTFOUND registry.npmjs.org

Fecha: 2026-05-31.

## Diagnostico Ejecutado

### Configuracion npm

`npm config get registry` antes de la solucion:

```text
https://npm.artifacts.furycloud.io/
```

`npm config list` mostro:

```text
; "user" config from /Users/tanhernandez/.npmrc

@playwright:registry = "https://registry.npmjs.org"
//npm.artifacts.furycloud.io/:_auth = (protected)
always-auth = true
registry = "https://npm.artifacts.furycloud.io/"
```

`~/.npmrc` existe. Contenido con secreto redactado:

```text
registry=https://npm.artifacts.furycloud.io/
always-auth=true
@playwright:registry=https://registry.npmjs.org
//npm.artifacts.furycloud.io/:_auth=<REDACTED>
```

`frontend/.npmrc` no existia antes de la solucion.

Variables de entorno:

```text
HTTP_PROXY=null
HTTPS_PROXY=null
NO_PROXY=null
http_proxy=null
https_proxy=null
no_proxy=null
```

Proxy npm:

```text
npm config get proxy       -> null
npm config get https-proxy -> null
```

### DNS y Red

`ping -c 3 registry.npmjs.org`:

```text
ping: cannot resolve registry.npmjs.org: Unknown host
```

`nslookup registry.npmjs.org` fuera del sandbox:

```text
;; connection timed out; no servers could be reached
```

`curl -I --max-time 10 https://registry.npmjs.org/`:

```text
curl: (28) Resolving timed out after 10002 milliseconds
```

`npm ping --registry=https://registry.npmjs.org/`:

```text
npm error code ENOTFOUND
npm error network request to https://registry.npmjs.org/-/ping failed
reason: getaddrinfo ENOTFOUND registry.npmjs.org
```

Mirrors evaluados:

```text
nslookup registry.yarnpkg.com       -> resuelve a Cloudflare
curl -I https://registry.yarnpkg.com/ -> HTTP/2 200
npm ping --registry=https://registry.yarnpkg.com/ -> PONG 331ms

nslookup registry.npmmirror.com -> resuelve
npm ping --registry=https://registry.npmmirror.com/ -> PONG
```

## Causa Clasificada

Causa principal: **A. DNS hacia `registry.npmjs.org`**.

Causa secundaria: **E. Registry alternativo global configurado**.

El equipo tiene `~/.npmrc` apuntando por defecto a `https://npm.artifacts.furycloud.io/`, con `@playwright` forzado a `https://registry.npmjs.org`. Como `registry.npmjs.org` no resuelve por DNS en esta red, la instalacion de Playwright y dependencias publicas falla con `ENOTFOUND`.

No hay evidencia de:

- Certificado SSL no confiable: no se alcanzo la fase TLS.
- Rate limiting: el dominio no resolvio.
- Proxy configurado incorrectamente en npm: `proxy` y `https-proxy` estan en `null`.
- Conexion offline total: `registry.yarnpkg.com`, `registry.npmmirror.com` y `github.com` resolvieron.

## Alternativas Evaluadas

### 1. Mirror publico local en `frontend/.npmrc` - Recomendada

Descripcion: configurar solo el frontend para usar `https://registry.yarnpkg.com/`, incluyendo el scope `@playwright`.

Pasos:

```bash
cd frontend
cat .npmrc
npm ping
npm install --legacy-peer-deps
```

Archivo aplicado:

```text
registry=https://registry.yarnpkg.com/
@playwright:registry=https://registry.yarnpkg.com/
```

Verificacion esperada:

```text
npm notice PING https://registry.yarnpkg.com/
npm notice PONG
```

Resultado obtenido:

```text
added 208 packages, and audited 387 packages
added 2 packages, and audited 389 packages
```

Limitaciones:

- Depende de que `registry.yarnpkg.com` siga permitido en la red.
- Mantiene una advertencia del `~/.npmrc` global por `always-auth=true` en npm 11.

### 2. Mirror publico `registry.npmmirror.com`

Descripcion: usar el mirror publico de npmmirror cuando Yarn registry no este disponible.

Pasos:

```bash
cd frontend
npm config set registry https://registry.npmmirror.com/ --location=project
npm config set @playwright:registry https://registry.npmmirror.com/ --location=project
npm ping
npm install --legacy-peer-deps
```

Verificacion esperada:

```text
npm notice PING https://registry.npmmirror.com/
npm notice PONG
```

Limitaciones:

- Mirror externo fuera de npm/Yarn; puede tener latencia mayor o politicas de replica distintas.
- Menos ideal para un equipo institucional en Colombia que el mirror de Yarn/Cloudflare.

### 3. Proxy corporativo explicito

Descripcion: si TI Javeriana requiere proxy para salida HTTPS, configurar npm con ese proxy.

Pasos:

```bash
npm config set proxy http://<proxy>:<port>
npm config set https-proxy http://<proxy>:<port>
npm ping --registry=https://registry.npmjs.org/
cd frontend
npm install --legacy-peer-deps
```

Verificacion esperada:

```text
npm notice PING https://registry.npmjs.org/
npm notice PONG
```

Limitaciones:

- Requiere conocer host/puerto del proxy.
- Si el proxy intercepta TLS, puede requerir certificado corporativo.
- `strict-ssl=false` solo debe usarse como ultimo recurso temporal.

### 4. Dependencias offline desde otro entorno

Descripcion: generar `node_modules` o tarballs en un equipo con internet y copiarlos.

Pasos:

```bash
cd frontend
npm ci --legacy-peer-deps
tar -czf frontend-node-modules.tgz node_modules package-lock.json
```

En el equipo bloqueado:

```bash
cd frontend
tar -xzf frontend-node-modules.tgz
npm rebuild
npm run test
```

Verificacion esperada:

```text
npm run test
# 43 passed
```

Limitaciones:

- Mas pesado y fragil entre macOS/Linux/Windows.
- No es ideal para CI ni reproducibilidad de equipo.

### 5. Codespaces o Dev Container

Descripcion: usar un entorno remoto con salida a internet para instalar y ejecutar tests.

Pasos:

```bash
mkdir -p .devcontainer
# crear devcontainer.json con Node 20/22
```

Verificacion esperada:

```bash
cd frontend
npm install --legacy-peer-deps
npm run test
npm run test:e2e
```

Limitaciones:

- Puede requerir cuenta GitHub con acceso a Codespaces.
- No resuelve la red local del equipo.

### 6. Verdaccio local como cache

Descripcion: montar un registry cache en una maquina con internet y usarlo desde la red local.

Pasos:

```bash
npm install -g verdaccio
verdaccio
cd frontend
npm config set registry http://<host-verdaccio>:4873 --location=project
npm install --legacy-peer-deps
```

Limitaciones:

- Requiere mantener otro servicio.
- Tiene mas costo operativo que un `.npmrc` local con mirror publico.

## Recomendacion

La opcion mas viable es **mirror publico local en `frontend/.npmrc` usando `registry.yarnpkg.com`**.

Razon:

- No requiere credenciales nuevas.
- No modifica runtime ni arquitectura.
- Es reproducible para el equipo.
- Evita tocar el `~/.npmrc` global con token privado.
- Corrige tambien el scope `@playwright`, que estaba forzado a `registry.npmjs.org`.

## Verificacion Final

Comandos ejecutados:

```bash
cd frontend
npm install --legacy-peer-deps
npm run test
npm run test:coverage
npm run test:e2e
npm run lint
npm run build
npm run smoke:e2e
```

Resultados:

| Comando | Resultado |
|---|---|
| `npm install --legacy-peer-deps` | OK, dependencias instaladas |
| `npm run test` | OK, 10 archivos, 43 tests |
| `npm run test:coverage` | OK, statements 87.76%, branches 80.35%, functions 73.33%, lines 87.76% |
| `npm run test:e2e` | OK, 7 tests Playwright Chromium |
| `npm run lint` | OK, 68 archivos TypeScript parseados |
| `npm run build` | OK |
| `npm run smoke:e2e` | OK, pago `CONFIRMADO` |

Notas:

- `npm install` reporta 8 vulnerabilidades transitivas. No bloquean la suite, pero deben revisarse con `npm audit`.
- npm 11 sigue mostrando una advertencia por `always-auth=true` en `~/.npmrc`; no afecta la instalacion desde el mirror local.
