const SECONDS_PER_UNIT = { s: 1, m: 60, h: 3600, d: 86400 } as const;

/**
 * Convierte JWT_EXPIRES_IN ('3600', '15m', '1h', '7d') a segundos.
 * El formato ya lo valida el esquema zod al arrancar.
 */
export function expirationToSeconds(value: string): number {
  const match = /^(\d+)([smhd]?)$/.exec(value);
  if (!match) {
    throw new Error(`JWT_EXPIRES_IN inválido: "${value}"`);
  }
  const [, amount, unit] = match;
  return (
    Number(amount) *
    SECONDS_PER_UNIT[(unit || 's') as keyof typeof SECONDS_PER_UNIT]
  );
}
