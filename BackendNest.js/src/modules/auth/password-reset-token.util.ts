import { createHash, randomBytes } from 'node:crypto';

// Token de recuperación: 32 bytes aleatorios en base64url (43 caracteres).
export const generateResetToken = (): string =>
  randomBytes(32).toString('base64url');

// Lo que se guarda en la base de datos. SHA-256 basta (no hace falta scrypt)
// porque el token ya tiene 256 bits de entropía: no se puede adivinar por
// fuerza bruta aunque se conozca el hash.
export const hashResetToken = (token: string): string =>
  createHash('sha256').update(token).digest('hex');
