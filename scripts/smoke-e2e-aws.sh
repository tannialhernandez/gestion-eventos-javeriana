#!/bin/bash
set -e

ALB="eventos-javeriana-alb-1966078085.us-east-1.elb.amazonaws.com"
EMAIL="laura.participante@javeriana.edu.co"
PASSWORD="demo123"

echo "========================================"
echo " SMOKE E2E — AWS Productivo"
echo " ALB: $ALB"
echo "========================================"
echo ""

echo "=== TEST 1: JWKS endpoint ==="
KEY_TYPE=$(curl -sf http://$ALB/api/v1/auth/.well-known/jwks.json | python3 -c "import sys,json; print(json.load(sys.stdin)['keys'][0]['kty'])")
echo "Key type: $KEY_TYPE"
[[ "$KEY_TYPE" == "RSA" ]] || { echo "FAIL: expected RSA key"; exit 1; }
echo "PASS ✓"
echo ""

echo "=== TEST 2: Login ==="
LOGIN_RESP=$(curl -sf -X POST http://$ALB/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}")
TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")
USER_NAME=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['user']['nombre'])")
[[ -n "$TOKEN" && "$TOKEN" != "null" ]] || { echo "FAIL: no token received"; exit 1; }
echo "Usuario: $USER_NAME"
echo "Token: ${TOKEN:0:60}..."
echo "PASS ✓"
echo ""

echo "=== TEST 3: Catálogo de eventos ==="
EVENTOS=$(curl -sf -H "Authorization: Bearer $TOKEN" http://$ALB/api/v1/eventos)
EVENTO_ID=$(echo "$EVENTOS" | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0]['id'])")
EVENTO_TITULO=$(echo "$EVENTOS" | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0]['titulo'])")
[[ -n "$EVENTO_ID" ]] || { echo "FAIL: no events found"; exit 1; }
echo "Evento: $EVENTO_TITULO"
echo "ID: $EVENTO_ID"
echo "PASS ✓"
echo ""

echo "=== TEST 3b: Tarifas del evento ==="
TARIFAS=$(curl -sf http://$ALB/api/v1/tarifas?eventoId=$EVENTO_ID)
TARIFA_ID=$(echo "$TARIFAS" | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0]['id'])")
TARIFA_MONTO=$(echo "$TARIFAS" | python3 -c "import sys,json; data=json.load(sys.stdin); print(data[0]['monto'])")
[[ -n "$TARIFA_ID" ]] || { echo "FAIL: no tarifas found"; exit 1; }
echo "Tarifa ID: $TARIFA_ID  Monto: $TARIFA_MONTO COP"
echo "PASS ✓"
echo ""

echo "=== TEST 4: Crear inscripción ==="
IDEMPOTENCY_KEY=$(python3 -c "import uuid; print(uuid.uuid4())")
INSC_RESP=$(curl -sf -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"eventoId\":\"$EVENTO_ID\",\"tarifaId\":\"$TARIFA_ID\",\"idempotencyKey\":\"$IDEMPOTENCY_KEY\"}" \
  http://$ALB/api/v1/inscripciones)
INSC_ID=$(echo "$INSC_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin)['inscripcionId'])")
INSC_ESTADO=$(echo "$INSC_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('estado','N/A'))")
CHECKOUT_URL=$(echo "$INSC_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('checkoutUrl','N/A'))")
[[ -n "$INSC_ID" ]] || { echo "FAIL: no inscripcionId. Response: $INSC_RESP"; exit 1; }
echo "Inscripción ID: $INSC_ID"
echo "Estado: $INSC_ESTADO"
echo "Checkout URL: ${CHECKOUT_URL:0:60}..."
echo "PASS ✓"
echo ""

echo "=== TEST 5: Crear preferencia de pago ==="
PAGO_RESP=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"inscripcionId\":\"$INSC_ID\",\"monto\":$TARIFA_MONTO,\"moneda\":\"COP\"}" \
  http://$ALB/api/v1/pagos/preferencias)
PAGO_STATUS=$(echo "$PAGO_RESP" | grep "HTTP_STATUS:" | cut -d: -f2)
PAGO_BODY=$(echo "$PAGO_RESP" | grep -v "HTTP_STATUS:")
echo "HTTP Status: $PAGO_STATUS"
echo "Pago response: ${PAGO_BODY:0:100}"
echo "PASS ✓"
echo ""

echo "========================================"
echo " ✅ SMOKE E2E AWS COMPLETO"
echo "========================================"
