const tokenFile = __ENV.K6_TOKENS_FILE || '/tmp/k6/tokens.json';
const tokenPool = JSON.parse(open(tokenFile));

function participanteIndex() {
  return (((__VU - 1) * 1009) + __ITER) % tokenPool.participantes.length;
}

export function participanteActual() {
  return tokenPool.participantes[participanteIndex()];
}

export function generarJwt(_userHint, roles = ['PARTICIPANTE']) {
  if (roles.includes('ORGANIZADOR')) {
    return tokenPool.organizador.token;
  }
  return participanteActual().token;
}

export function authHeaders(roles = ['PARTICIPANTE']) {
  return {
    Authorization: `Bearer ${generarJwt(null, roles)}`,
    'Content-Type': 'application/json',
    'X-Correlation-Id': `k6-${__VU}-${__ITER}-${Date.now()}`,
  };
}

export function authHeadersForIndex(index = 0) {
  const participante = tokenPool.participantes[index % tokenPool.participantes.length];
  return {
    Authorization: `Bearer ${participante.token}`,
    'Content-Type': 'application/json',
    'X-Correlation-Id': `k6-setup-${index}-${Date.now()}`,
  };
}
