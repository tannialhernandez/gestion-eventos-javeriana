import type { CheckoutSnapshot } from '../../entities/inscription';

const prefix = 'gea.checkout.';

export function saveCheckoutSnapshot(inscriptionId: string, snapshot: CheckoutSnapshot): void {
  sessionStorage.setItem(`${prefix}${inscriptionId}`, JSON.stringify(snapshot));
}

export function readCheckoutSnapshot(inscriptionId: string | undefined): CheckoutSnapshot | null {
  if (!inscriptionId) return null;
  const raw = sessionStorage.getItem(`${prefix}${inscriptionId}`);
  if (!raw) return null;

  try {
    return JSON.parse(raw) as CheckoutSnapshot;
  } catch {
    return null;
  }
}
