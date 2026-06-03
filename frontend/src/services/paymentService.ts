import { env } from '../shared/config/env';
import { createHttpClient } from '../shared/api';
import { withRetry } from '../lib/retry';
import { BusinessRuleError, isAppError } from '../lib/errors';

const paymentHttp = createHttpClient(env.paymentApiUrl, 'payment-service');

export async function approvePayment(inscriptionId: string): Promise<void> {
  try {
    await withRetry(() => {
      return paymentHttp.post(`/pagos/simulador/${inscriptionId}/aprobar`);
    });
  } catch (error) {
    if (isAppError(error) && (error.status === 401 || error.status === 403)) {
      throw new BusinessRuleError(
        'El simulador de pago productivo rechazó la confirmación. Tu sesión sigue activa; revisa la firma HMAC o configuración del payment-service.',
        { status: error.status, code: 'payment_confirmation_rejected', serviceName: error.serviceName, cause: error },
      );
    }
    throw error;
  }
}
