// Cifrado y verificación de contraseñas con scrypt (D-002 en docs/decisiones.md).
// Lo usan el registro (HU-014), el login (HU-015), el alta de empleados
// (HU-016) y el restablecimiento de contraseña (HU-018).

import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';
import { Injectable } from '@nestjs/common';

const scryptAsync = promisify(scrypt) as (
  password: string,
  salt: Buffer,
  keyLength: number,
  options: { N: number; r: number; p: number; maxmem: number },
) => Promise<Buffer>;

// Uno de los perfiles mínimos de OWASP para scrypt
const COST = { N: 2 ** 14, r: 8, p: 5 };
const KEY_LENGTH = 64;
const SALT_LENGTH = 16;
const PREFIX = 'scrypt';

@Injectable()
export class PasswordService {
  // Devuelve "scrypt$N$r$p$sal$hash" (sal y hash en base64). Guardar los
  // parámetros permite subir el coste sin invalidar contraseñas antiguas.
  async hash(password: string): Promise<string> {
    const salt = randomBytes(SALT_LENGTH);
    const key = await this.derive(password, salt, COST);
    return [
      PREFIX,
      COST.N,
      COST.r,
      COST.p,
      salt.toString('base64'),
      key.toString('base64'),
    ].join('$');
  }

  // true si la contraseña corresponde al hash. Un hash con formato inválido
  // se trata como contraseña incorrecta, nunca como error.
  async compare(password: string, stored: string): Promise<boolean> {
    const parts = stored.split('$');
    if (parts.length !== 6 || parts[0] !== PREFIX) return false;

    const [, N, r, p, salt, key] = parts;
    const expected = Buffer.from(key, 'base64');
    const actual = await this.derive(password, Buffer.from(salt, 'base64'), {
      N: Number(N),
      r: Number(r),
      p: Number(p),
    });

    // Comparación en tiempo constante: no revela cuántos bytes coinciden
    return (
      actual.length === expected.length && timingSafeEqual(actual, expected)
    );
  }

  private derive(
    password: string,
    salt: Buffer,
    cost: { N: number; r: number; p: number },
  ): Promise<Buffer> {
    // maxmem por encima de lo que necesita scrypt (128 · N · r bytes)
    const maxmem = 256 * cost.N * cost.r;
    return scryptAsync(password, salt, KEY_LENGTH, { ...cost, maxmem });
  }
}
