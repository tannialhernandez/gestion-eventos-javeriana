import { expect, test } from '@playwright/test';

test.describe('JWKS auth-service-stub', () => {
  test('JWKS endpoint expone clave publica RSA', async ({ request }) => {
    const response = await request.get('/auth-api/api/v1/auth/.well-known/jwks.json');
    expect(response.status()).toBe(200);

    const jwks = await response.json() as {
      keys?: Array<Record<string, unknown>>;
    };

    expect(jwks).toHaveProperty('keys');
    expect(jwks.keys).toHaveLength(1);
    expect(jwks.keys?.[0]).toMatchObject({
      kty: 'RSA',
      alg: 'RS256',
      use: 'sig',
      kid: 'local-demo-rsa',
    });
    expect(jwks.keys?.[0]).toHaveProperty('n');
    expect(jwks.keys?.[0]).toHaveProperty('e');
  });

  test('login emite JWT compatible con JWKS', async ({ request }) => {
    const login = await request.post('/auth-api/api/v1/auth/login', {
      data: {
        email: 'diego.participante@javeriana.edu.co',
        password: 'demo123',
      },
    });

    expect(login.status()).toBe(200);
    const body = await login.json() as { accessToken?: string; tokenType?: string };
    expect(body.tokenType).toBe('Bearer');
    expect(body.accessToken?.split('.')).toHaveLength(3);
  });
});
