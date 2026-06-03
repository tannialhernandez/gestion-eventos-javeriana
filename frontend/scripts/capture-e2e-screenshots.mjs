import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'

const chromePath =
  process.env.CHROME_PATH ??
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const appBaseUrl = process.env.SPA_BASE_URL ?? 'http://127.0.0.1:3000'
const outputDir = resolve(process.env.SPA_SCREENSHOT_DIR ?? 'evidence/screenshots')
const port = Number(process.env.CHROME_DEBUG_PORT ?? 9333 + Math.floor(Math.random() * 500))
const profileDir = `/private/tmp/gea-spa-chrome-${Date.now()}`
const visualEventNumber = 31 + (Date.now() % 60)
const visualEventId = `00000000-0000-0000-0000-${String(visualEventNumber).padStart(12, '0')}`

if (!existsSync(chromePath)) {
  throw new Error(`Chrome executable not found: ${chromePath}`)
}

mkdirSync(outputDir, { recursive: true })
mkdirSync(profileDir, { recursive: true })

function sleep(ms) {
  return new Promise((resolveSleep) => setTimeout(resolveSleep, ms))
}

async function waitForJson(url, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs
  let lastError

  while (Date.now() < deadline) {
    try {
      const response = await fetch(url)
      if (response.ok) return response.json()
    } catch (error) {
      lastError = error
    }
    await sleep(250)
  }

  throw lastError ?? new Error(`Timed out waiting for ${url}`)
}

class CdpClient {
  constructor(ws) {
    this.ws = ws
    this.nextId = 1
    this.pending = new Map()
    this.events = new Map()
    ws.addEventListener('message', (message) => {
      const payload = JSON.parse(message.data)
      if (payload.id && this.pending.has(payload.id)) {
        const { resolve: resolvePending, reject } = this.pending.get(payload.id)
        this.pending.delete(payload.id)
        if (payload.error) reject(new Error(payload.error.message))
        else resolvePending(payload.result)
        return
      }

      if (payload.method && this.events.has(payload.method)) {
        for (const listener of this.events.get(payload.method)) listener(payload.params)
      }
    })
  }

  send(method, params = {}) {
    const id = this.nextId
    this.nextId += 1
    this.ws.send(JSON.stringify({ id, method, params }))
    return new Promise((resolvePending, reject) => {
      this.pending.set(id, { resolve: resolvePending, reject })
    })
  }

  once(method, timeoutMs = 10000) {
    return new Promise((resolvePending, reject) => {
      const timeout = setTimeout(() => {
        listeners.delete(listener)
        reject(new Error(`Timed out waiting for ${method}`))
      }, timeoutMs)
      const listener = (params) => {
        clearTimeout(timeout)
        listeners.delete(listener)
        resolvePending(params)
      }
      const listeners = this.events.get(method) ?? new Set()
      listeners.add(listener)
      this.events.set(method, listeners)
    })
  }

  on(method, listener) {
    const listeners = this.events.get(method) ?? new Set()
    listeners.add(listener)
    this.events.set(method, listeners)
    return () => listeners.delete(listener)
  }

  close() {
    this.ws.close()
  }
}

async function connectPage() {
  const targets = await waitForJson(`http://127.0.0.1:${port}/json`, 15000)
  const page = targets.find((target) => target.type === 'page')
  if (!page) throw new Error('No Chrome page target available')

  const ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((resolveOpen, reject) => {
    ws.addEventListener('open', resolveOpen, { once: true })
    ws.addEventListener('error', reject, { once: true })
  })
  return new CdpClient(ws)
}

async function evaluate(client, expression) {
  const result = await client.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
  })

  if (result.exceptionDetails) {
    const description = result.exceptionDetails.exception?.description ?? result.exceptionDetails.text
    throw new Error(description)
  }

  return result.result?.value
}

async function waitForExpression(client, expression, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs

  while (Date.now() < deadline) {
    if (await evaluate(client, `Boolean(${expression})`)) return
    await sleep(300)
  }

  throw new Error(`Timed out waiting for expression: ${expression}`)
}

async function clickByText(client, selector, text) {
  await evaluate(
    client,
    `(() => {
      const node = Array.from(document.querySelectorAll(${JSON.stringify(selector)}))
        .find((element) => element.textContent && element.textContent.includes(${JSON.stringify(text)}));
      if (!node) throw new Error('No element found for text: ${text}');
      node.click();
      return true;
    })()`,
  )
}

async function setInputValue(client, selector, value) {
  await evaluate(
    client,
    `(() => {
      const node = document.querySelector(${JSON.stringify(selector)});
      if (!node) throw new Error('No input found: ${selector}');
      const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
      node.focus();
      setter.call(node, ${JSON.stringify(value)});
      node.dispatchEvent(new Event('input', { bubbles: true }));
      node.dispatchEvent(new Event('change', { bubbles: true }));
      return true;
    })()`,
  )
}

async function screenshot(client, filename) {
  await sleep(500)
  const result = await client.send('Page.captureScreenshot', {
    format: 'png',
    captureBeyondViewport: true,
    fromSurface: true,
  })
  writeFileSync(join(outputDir, filename), Buffer.from(result.data, 'base64'))
}

async function interceptInscriptionUnavailable(client) {
  client.on('Fetch.requestPaused', (params) => {
    const body = JSON.stringify({
      error: 'servicio_no_disponible',
      message: 'El servicio está temporalmente no disponible. Intente en unos momentos.',
    })
    void client.send('Fetch.fulfillRequest', {
      requestId: params.requestId,
      responseCode: 503,
      responsePhrase: 'Service Unavailable',
      responseHeaders: [
        { name: 'Content-Type', value: 'application/json' },
        { name: 'Retry-After', value: '2' },
      ],
      body: Buffer.from(body).toString('base64'),
    })
  })

  await client.send('Fetch.enable', {
    patterns: [
      {
        urlPattern: '*inscription-api/api/v1/inscripciones*',
        requestStage: 'Request',
      },
    ],
  })
}

const chrome = spawn(chromePath, [
  '--headless=new',
  '--disable-gpu',
  '--disable-dev-shm-usage',
  '--no-first-run',
  '--no-default-browser-check',
  `--remote-debugging-port=${port}`,
  `--user-data-dir=${profileDir}`,
  '--window-size=1440,1000',
  'about:blank',
], {
  stdio: 'ignore',
})

let client
const browserErrors = []

function describeRemoteValue(value) {
  return value?.value ?? value?.description ?? value?.unserializableValue ?? ''
}

try {
  client = await connectPage()
  await client.send('Page.enable')
  await client.send('Runtime.enable')
  await client.send('Log.enable')
  client.on('Runtime.exceptionThrown', (params) => {
    const details = params.exceptionDetails
    browserErrors.push(details.exception?.description ?? details.text ?? 'Unhandled browser exception')
  })
  client.on('Runtime.consoleAPICalled', (params) => {
    if (params.type !== 'error') return
    browserErrors.push(params.args.map(describeRemoteValue).filter(Boolean).join(' '))
  })
  client.on('Log.entryAdded', (params) => {
    if (params.entry.level !== 'error') return
    if (params.entry.text.includes('status of 503 (Service Unavailable)')) return
    browserErrors.push(params.entry.text)
  })
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false,
  })

  await client.send('Page.navigate', { url: `${appBaseUrl}/login` })
  await waitForExpression(client, "document.querySelector('#login-title')?.textContent.includes('Gestion') || document.querySelector('#login-title')?.textContent.includes('Gestión')")
  await screenshot(client, '01-login.png')

  await setInputValue(client, 'input[name="email"]', 'sofia.soporte@javeriana.edu.co')
  await setInputValue(client, 'input[name="password"]', 'demo123')
  await clickByText(client, 'button', 'Iniciar sesión')
  await waitForExpression(client, "location.pathname === '/catalogo' && document.querySelectorAll('.event-card').length > 0", 30000)
  await screenshot(client, '02-catalogo.png')

  await client.send('Page.navigate', { url: `${appBaseUrl}/eventos/${visualEventId}` })
  await waitForExpression(client, "location.pathname.startsWith('/eventos/') && document.querySelector('.checkout-panel')", 20000)
  await screenshot(client, '03-detalle-evento.png')

  await clickByText(client, 'button', 'Inscribirme')
  await waitForExpression(client, "location.pathname.includes('/pago') && document.querySelector('.payment-layout')", 30000)
  await screenshot(client, '04-pago.png')

  await clickByText(client, 'button', 'Confirmar pago aprobado')
  await waitForExpression(client, "location.pathname.startsWith('/confirmacion/') && document.querySelector('.confirmation')", 30000)
  await screenshot(client, '05-confirmacion.png')

  await client.send('Page.navigate', { url: `${appBaseUrl}/catalogo` })
  await waitForExpression(client, "location.pathname === '/catalogo' && document.querySelectorAll('.event-card').length > 0", 30000)
  await client.send('Page.navigate', { url: `${appBaseUrl}/eventos/${visualEventId}` })
  await waitForExpression(client, "location.pathname.startsWith('/eventos/') && document.querySelector('.checkout-panel')", 20000)
  await interceptInscriptionUnavailable(client)
  await clickByText(client, 'button', 'Inscribirme')
  await waitForExpression(
    client,
    "document.querySelector('.contextual-error') && document.querySelector('.resilience-banner--degraded')",
    15000,
  )
  await screenshot(client, '06-degradacion-controlada.png')

  if (browserErrors.length > 0) {
    throw new Error(`Browser runtime errors detected:\n${browserErrors.join('\n')}`)
  }

  console.log(`Captured SPA E2E screenshots in ${outputDir}`)
} finally {
  if (client) client.close()
  chrome.kill('SIGTERM')
  try {
    rmSync(profileDir, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
  } catch {
    // Chrome can keep a lock briefly after SIGTERM; the temp profile is disposable.
  }
}
