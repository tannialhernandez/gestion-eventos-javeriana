# Diagnostico y Resolucion - ENOTFOUND registry.npmjs.org

Fecha: 2026-05-31.

## Resumen

Durante la instalacion de dependencias del frontend se observo el error
`ENOTFOUND registry.npmjs.org`. El bloqueo se clasifica como un problema de
resolucion DNS hacia el registry publico principal de npm. Para mantener la
reproducibilidad sin credenciales ni servicios pagos, el frontend quedo
configurado con el mirror publico `https://registry.yarnpkg.com/`.

## Diagnostico Ejecutado

### Configuracion npm del proyecto

`frontend/.npmrc`:

```text
registry=https://registry.yarnpkg.com/
@playwright:registry=https://registry.yarnpkg.com/
```

El archivo `frontend/package-lock.json` fue normalizado para que los campos
`resolved` usen el mismo registry publico del proyecto. Esto evita que `npm ci`
intente descargar paquetes desde registries no declarados por el repositorio.

### DNS y Red

`ping -c 3 registry.npmjs.org`:

```text
ping: cannot resolve registry.npmjs.org: Unknown host
```

`curl -I --max-time 10 https://registry.npmjs.org/`:

```text
curl: (28) Resolving timed out after 10002 milliseconds
```

Verificacion del mirror seleccionado:

```text
npm ping --registry=https://registry.yarnpkg.com/
npm notice PONG
```

## Causa Clasificada

Causa principal: **A. DNS hacia `registry.npmjs.org`**.

No hay evidencia suficiente para atribuir el problema a credenciales, proxy,
certificados TLS, rate limiting o indisponibilidad general de internet. El
repositorio queda autosuficiente al declarar un registry publico alternativo en
`frontend/.npmrc`.

## Solucion Aplicada

### 1. Mirror publico local en `frontend/.npmrc`

El frontend usa `https://registry.yarnpkg.com/` para dependencias npm y para el
scope de Playwright:

```text
registry=https://registry.yarnpkg.com/
@playwright:registry=https://registry.yarnpkg.com/
```

### 2. Lockfile consistente con CI

El lockfile se dejo sin referencias a registries externos no declarados. En CI
se usa instalacion deterministica:

```bash
cd frontend
npm ci --legacy-peer-deps --registry=https://registry.yarnpkg.com/
```

### 3. Workflows GitHub Actions

Los workflows `ci-frontend.yml` y `ci-e2e.yml` escriben el `.npmrc` del proyecto
antes de instalar dependencias y ejecutan `npm ci`, no `npm install`, para que
el resultado sea reproducible.

## Alternativas Evaluadas

### A. Mirror publico Yarn - Recomendada

Pasos:

```bash
cd frontend
npm ping --registry=https://registry.yarnpkg.com/
npm ci --legacy-peer-deps --registry=https://registry.yarnpkg.com/
```

Verificacion esperada:

```text
npm notice PONG
added ... packages
```

Limitacion: depende de disponibilidad del mirror publico de Yarn.

### B. Mirror publico npmmirror

Pasos:

```bash
cd frontend
npm config set registry https://registry.npmmirror.com/ --location=project
npm config set @playwright:registry https://registry.npmmirror.com/ --location=project
npm ping
npm ci --legacy-peer-deps
```

Limitacion: mirror externo adicional, con politicas de replica distintas.

### C. Dependencias offline desde otro entorno

Pasos:

```bash
cd frontend
npm ci --legacy-peer-deps
tar -czf frontend-node-modules.tgz node_modules package-lock.json
```

En el equipo sin resolucion DNS:

```bash
cd frontend
tar -xzf frontend-node-modules.tgz
npm rebuild
npm run test
```

Limitacion: menos portable entre sistemas operativos y arquitecturas.

### D. Dev Container o Codespaces

Pasos:

```bash
cd frontend
npm ci --legacy-peer-deps
npm run lint
npm run test
npm run build
```

Limitacion: requiere usar un entorno remoto o contenedor de desarrollo.

## Recomendacion Final

Mantener la solucion A: `frontend/.npmrc` + `npm ci` + lockfile normalizado al
mirror publico `registry.yarnpkg.com`. Es la alternativa de menor costo, no
requiere credenciales, no cambia el stack React/Vite/TypeScript y funciona tanto
en local como en GitHub Actions.

## Verificacion Final Esperada

```bash
cd frontend
npm ci --legacy-peer-deps --registry=https://registry.yarnpkg.com/
npm run lint
npm run build
npm run test
npm run test:coverage
```
