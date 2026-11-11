import { describe, it, expect } from 'vitest';
import {
  generateResetToken,
  hashResetToken,
} from './password-reset-token.util.js';

describe('token de recuperación (RN-103)', () => {
  it('genera 32 bytes aleatorios en base64url (43 caracteres, sin símbolos)', () => {
    const token = generateResetToken();

    expect(token).toMatch(/^[A-Za-z0-9_-]{43}$/);
  });

  it('no repite tokens', () => {
    const tokens = new Set(Array.from({ length: 200 }, generateResetToken));

    expect(tokens.size).toBe(200);
  });

  it('el hash es SHA-256 en hexadecimal, estable y distinto del token', () => {
    const token = generateResetToken();
    const hash = hashResetToken(token);

    expect(hash).toMatch(/^[0-9a-f]{64}$/);
    expect(hashResetToken(token)).toBe(hash);
    expect(hash).not.toBe(token);
    expect(hashResetToken(`${token}x`)).not.toBe(hash);
  });
});
