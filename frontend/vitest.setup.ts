import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll, expect, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import axios from 'axios';
import * as axeMatchers from 'vitest-axe/matchers';

expect.extend(axeMatchers);

function createMemoryStorage(): Storage {
  const values = new Map<string, string>();
  return {
    get length() {
      return values.size;
    },
    clear: () => values.clear(),
    getItem: (key: string) => values.get(key) ?? null,
    key: (index: number) => Array.from(values.keys())[index] ?? null,
    removeItem: (key: string) => values.delete(key),
    setItem: (key: string, value: string) => values.set(key, String(value)),
  };
}

const sessionStorageMock = createMemoryStorage();
const localStorageMock = createMemoryStorage();

Object.defineProperty(globalThis, 'sessionStorage', {
  value: sessionStorageMock,
  configurable: true,
});

Object.defineProperty(globalThis, 'localStorage', {
  value: localStorageMock,
  configurable: true,
});

Object.defineProperty(window, 'sessionStorage', {
  value: sessionStorageMock,
  configurable: true,
});

Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
  configurable: true,
});

if (globalThis.Node && !('isConnected' in globalThis.Node.prototype)) {
  Object.defineProperty(globalThis.Node.prototype, 'isConnected', {
    value: false,
    writable: true,
    configurable: true,
  });
}

class TestMessageEvent {
  readonly type: string;
  readonly data: unknown;
  readonly origin: string;
  defaultPrevented = false;

  constructor(type: string, eventInitDict: MessageEventInit = {}) {
    this.type = type;
    this.data = eventInitDict.data;
    this.origin = eventInitDict.origin ?? '';
  }

  preventDefault() {
    this.defaultPrevented = true;
  }
}

Object.defineProperty(globalThis, 'MessageEvent', {
  value: TestMessageEvent,
  configurable: true,
});

Object.defineProperty(window, 'MessageEvent', {
  value: TestMessageEvent,
  configurable: true,
});

axios.defaults.adapter = 'fetch';

const { server } = await import('./src/test/mocks/server');

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  sessionStorage.clear();
  localStorage.clear();
  vi.useRealTimers();
});

afterAll(() => {
  server.close();
});
