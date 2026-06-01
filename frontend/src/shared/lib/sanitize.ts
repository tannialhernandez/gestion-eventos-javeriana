export function sanitizeText(value: string | null | undefined): string {
  if (!value) return '';
  return value.replace(/<[^>]*>/g, '').replace(/[\u0000-\u001f\u007f]/g, '').trim();
}
