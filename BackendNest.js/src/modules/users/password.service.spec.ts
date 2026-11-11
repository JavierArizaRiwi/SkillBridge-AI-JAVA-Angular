import { describe, it, expect } from 'vitest';
import { PasswordService } from './password.service.js';

describe('PasswordService (RN-085, RN-089)', () => {
  const service = new PasswordService();

  it('no guarda la contraseña en claro y usa el formato scrypt$N$r$p$sal$hash', async () => {
    const hash = await service.hash('Password123');

    expect(hash).not.toContain('Password123');
    expect(hash.split('$')).toHaveLength(6);
    expect(hash.startsWith('scrypt$16384$8$5$')).toBe(true);
  });

  it('genera un hash distinto cada vez para la misma contraseña (sal aleatoria)', async () => {
    const [a, b] = await Promise.all([
      service.hash('Password123'),
      service.hash('Password123'),
    ]);

    expect(a).not.toBe(b);
  });

  it('acepta la contraseña correcta', async () => {
    const hash = await service.hash('Password123');

    await expect(service.compare('Password123', hash)).resolves.toBe(true);
  });

  it('rechaza una contraseña distinta, aunque solo cambie una letra', async () => {
    const hash = await service.hash('Password123');

    await expect(service.compare('password123', hash)).resolves.toBe(false);
    await expect(service.compare('', hash)).resolves.toBe(false);
  });

  it.each(['', 'texto-plano', 'bcrypt$10$abc', 'scrypt$1$2$3$sal'])(
    'trata un hash con formato inválido (%j) como contraseña incorrecta',
    async (stored) => {
      await expect(service.compare('Password123', stored)).resolves.toBe(false);
    },
  );
});
