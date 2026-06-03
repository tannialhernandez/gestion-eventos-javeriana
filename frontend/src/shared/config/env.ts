const defaultEventApi = '/event-api/api/v1';
const defaultInscriptionApi = '/inscription-api/api/v1';
const defaultPaymentApi = '/payment-api/api/v1';
const defaultAuthApi = '/auth-api/api/v1';

export const env = {
  authApiUrl: import.meta.env.VITE_AUTH_API_URL ?? defaultAuthApi,
  eventApiUrl: import.meta.env.VITE_EVENT_API_URL ?? defaultEventApi,
  inscriptionApiUrl: import.meta.env.VITE_INSCRIPTION_API_URL ?? defaultInscriptionApi,
  paymentApiUrl: import.meta.env.VITE_PAYMENT_API_URL ?? defaultPaymentApi,
  paymentWebhookSecret: import.meta.env.VITE_PAYMENT_WEBHOOK_SECRET ?? 'change-me-before-production',
};
