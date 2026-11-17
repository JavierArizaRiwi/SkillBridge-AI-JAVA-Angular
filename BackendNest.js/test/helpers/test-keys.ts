import { generateKeyPairSync } from 'node:crypto';

export interface TestKeys {
  privateKey: string;
  publicKey: string;
}

let cached: TestKeys | undefined;

/**
 * Genera (una vez) un par RSA para firmar JWT RS256 en las pruebas y publica
 * la clave pública en el entorno para que el guard la valide.
 */
export function getTestKeys(): TestKeys {
  if (cached) {
    return cached;
  }
  const { privateKey, publicKey } = generateKeyPairSync('rsa', {
    modulusLength: 2048,
  });
  cached = {
    privateKey: privateKey.export({ type: 'pkcs8', format: 'pem' }).toString(),
    publicKey: publicKey.export({ type: 'spki', format: 'pem' }).toString(),
  };
  process.env.JWT_PUBLIC_KEY = cached.publicKey;
  process.env.JWT_ISSUER = 'skillbridge';
  process.env.LIVE_ROOM_ENCRYPTION_KEY = 'test-encryption-key';
  process.env.NODE_ENV = 'test';
  return cached;
}
