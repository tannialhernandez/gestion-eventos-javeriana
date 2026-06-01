import { type AxiosInstance, isAxiosError } from 'axios';
import { parseHttpError, ServiceUnavailableError } from '../errors';
import { recordServiceDegraded, recordServiceRecovered } from '../serviceHealth';

export function attachAxiosErrorInterceptor(client: AxiosInstance, serviceName: string): void {
  client.interceptors.response.use(
    (response) => {
      recordServiceRecovered(serviceName);
      return response;
    },
    (error: unknown) => {
      if (!isAxiosError(error)) return Promise.reject(error);

      const parsed = parseHttpError(error, serviceName);
      if (parsed instanceof ServiceUnavailableError) {
        recordServiceDegraded(parsed);
      }

      return Promise.reject(parsed);
    },
  );
}
