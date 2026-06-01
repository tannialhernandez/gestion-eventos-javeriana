import crypto from 'node:crypto';
import fs from 'node:fs';

const [privateKeyFile, outputFile, rawCount] = process.argv.slice(2);
const count = Number.parseInt(rawCount ?? '1500', 10);

if (!privateKeyFile || !outputFile || Number.isNaN(count) || count < 1) {
  console.error('Usage: node generate-tokens.mjs <private-key-b64> <output-json> <count>');
  process.exit(1);
}

const der = Buffer.from(fs.readFileSync(privateKeyFile, 'utf8').trim(), 'base64');
const privateKey = crypto.createPrivateKey({ key: der, format: 'der', type: 'pkcs8' });

function base64url(input) {
  return Buffer.from(input)
    .toString('base64')
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replace(/=+$/u, '');
}

function uuidFromNumber(namespace, value) {
  return `${namespace}-0000-4000-8000-${String(value).padStart(12, '0')}`;
}

function signToken(userId, roles) {
  const now = Math.floor(Date.now() / 1000);
  const header = { alg: 'RS256', typ: 'JWT' };
  const payload = {
    sub: userId,
    roles,
    email: `load-${userId.slice(-12)}@test.javeriana.edu`,
    iat: now,
    exp: now + 24 * 60 * 60,
  };
  const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(payload))}`;
  const signature = crypto.sign('RSA-SHA256', Buffer.from(signingInput), privateKey);
  return `${signingInput}.${base64url(signature)}`;
}

const participantes = Array.from({ length: count }, (_, index) => {
  const userId = uuidFromNumber('10000000', index + 1);
  return { userId, token: signToken(userId, ['PARTICIPANTE']) };
});

const organizadorId = uuidFromNumber('20000000', 1);

const payload = {
  generatedAt: new Date().toISOString(),
  participantes,
  organizador: {
    userId: organizadorId,
    token: signToken(organizadorId, ['ORGANIZADOR']),
  },
};

fs.writeFileSync(outputFile, `${JSON.stringify(payload)}\n`);
console.log(`Generated ${participantes.length} participant tokens at ${outputFile}`);
