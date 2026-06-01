import { env } from '../shared/config/env';
import { createHttpClient } from '../shared/api';
import { hmacSha256Hex } from '../shared/lib';
import { withRetry } from '../lib/retry';

const paymentHttp = createHttpClient(env.paymentApiUrl, 'payment-service');

type PaymentWebhookPayload = {
  referencia_externa: string;
  inscripcion_id: string;
  estado: 'approved' | 'rejected';
  metadata: {
    origen: 'frontend-spa';
  };
};

export async function approvePayment(inscriptionId: string): Promise<void> {
  const payload: PaymentWebhookPayload = {
    referencia_externa: `SPA-${crypto.randomUUID()}`,
    inscripcion_id: inscriptionId,
    estado: 'approved',
    metadata: { origen: 'frontend-spa' },
  };
  const body = JSON.stringify(payload);
  const signature = await hmacSha256Hex(env.paymentWebhookSecret, body);
  await withRetry(() => {
    return paymentHttp.post('/webhooks/pagos', body, {
      headers: {
        'Content-Type': 'application/json',
        'X-Signature': signature,
      },
    });
  });
}
