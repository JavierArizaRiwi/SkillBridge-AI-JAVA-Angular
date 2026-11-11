import { describe, it, expect } from 'vitest';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { RegisterDto } from './register.dto.js';

const valid = {
  firstName: 'Carlos',
  lastName: 'Pérez',
  email: 'carlos@example.com',
  phone: '+57 300 123 4567',
  password: 'Password123',
  passwordConfirmation: 'Password123',
};

// Valida como lo hace el ValidationPipe global y devuelve los campos con error
async function invalidFields(body: Record<string, unknown>) {
  const errors = await validate(plainToInstance(RegisterDto, body));
  return errors.map((e) => e.property);
}

describe('RegisterDto', () => {
  it('acepta un registro válido', async () => {
    await expect(invalidFields(valid)).resolves.toEqual([]);
  });

  it('normaliza el email a minúsculas y recorta espacios', () => {
    const dto = plainToInstance(RegisterDto, {
      ...valid,
      email: '  Carlos@Example.COM ',
      firstName: '  Carlos ',
    });

    expect(dto.email).toBe('carlos@example.com');
    expect(dto.firstName).toBe('Carlos');
  });

  describe('política de contraseñas (RN-083)', () => {
    it.each([
      ['menos de 8 caracteres', 'Pass12'],
      ['sin números', 'Passwordsolo'],
      ['sin letras', '12345678'],
      ['más de 128 caracteres', `a1${'x'.repeat(127)}`],
    ])('rechaza una contraseña %s', async (_caso, password) => {
      await expect(
        invalidFields({ ...valid, password, passwordConfirmation: password }),
      ).resolves.toEqual(['password']);
    });

    it('acepta letras con tilde o ñ como letras', async () => {
      const password = 'contraseña1';
      await expect(
        invalidFields({ ...valid, password, passwordConfirmation: password }),
      ).resolves.toEqual([]);
    });
  });

  it('rechaza una confirmación distinta de la contraseña (RN-084, @Match)', async () => {
    await expect(
      invalidFields({ ...valid, passwordConfirmation: 'Password124' }),
    ).resolves.toEqual(['passwordConfirmation']);
  });

  it.each([
    ['email', 'no-es-un-email'],
    ['phone', 'abc'],
    ['phone', '123'],
    ['firstName', '   '],
    ['lastName', ''],
  ])('rechaza %s = %j', async (field, value) => {
    await expect(invalidFields({ ...valid, [field]: value })).resolves.toEqual([
      field,
    ]);
  });
});
