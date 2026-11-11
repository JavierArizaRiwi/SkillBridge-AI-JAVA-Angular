import { describe, it, expect } from 'vitest';
import { validateEnv } from './env.validation.schema.js';

// Variables obligatorias mínimas para que el esquema acepte la configuración.
const base = {
  DATABASE_HOST: 'localhost',
  DATABASE_USER: 'zerobug',
  DATABASE_PASSWORD: 'secreto',
  DATABASE_NAME: 'zerobug',
  JWT_SECRET: 'x'.repeat(32),
};

describe('validateEnv — recuperación de contraseña (HU-018)', () => {
  it('usa 30 minutos y una URL local por defecto', () => {
    const env = validateEnv(base);

    expect(env.PASSWORD_RESET_TTL_MINUTES).toBe(30);
    expect(env.PASSWORD_RESET_URL).toBe('http://localhost:3000/reset-password');
  });

  it.each([
    ['PASSWORD_RESET_TTL_MINUTES', '2'],
    ['PASSWORD_RESET_TTL_MINUTES', '1441'],
    ['PASSWORD_RESET_URL', 'no-es-una-url'],
  ])('rechaza %s=%s y detiene el arranque', (name, value) => {
    expect(() => validateEnv({ ...base, [name]: value })).toThrow(name);
  });
});

describe('validateEnv — parámetros de reservas (D-001)', () => {
  it('usa 120 min de duración y 15 min de tolerancia por defecto', () => {
    const env = validateEnv(base);

    expect(env.RESERVATION_DURATION_MINUTES).toBe(120);
    expect(env.RESERVATION_NO_SHOW_TOLERANCE_MINUTES).toBe(15);
  });

  it('convierte a número los valores que llegan como texto desde .env', () => {
    const env = validateEnv({
      ...base,
      RESERVATION_DURATION_MINUTES: '90',
      RESERVATION_NO_SHOW_TOLERANCE_MINUTES: '0',
    });

    expect(env.RESERVATION_DURATION_MINUTES).toBe(90);
    expect(env.RESERVATION_NO_SHOW_TOLERANCE_MINUTES).toBe(0);
  });

  it.each([
    ['RESERVATION_DURATION_MINUTES', '10'],
    ['RESERVATION_DURATION_MINUTES', '721'],
    ['RESERVATION_DURATION_MINUTES', '90.5'],
    ['RESERVATION_NO_SHOW_TOLERANCE_MINUTES', '-1'],
    ['RESERVATION_NO_SHOW_TOLERANCE_MINUTES', 'quince'],
  ])('rechaza %s=%s y detiene el arranque', (name, value) => {
    expect(() => validateEnv({ ...base, [name]: value })).toThrow(name);
  });
});
