import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  createCipheriv,
  createDecipheriv,
  createHash,
  randomBytes,
} from 'node:crypto';

const ALGORITHM = 'aes-256-gcm';
const VERSION = 'v1';
const IV_BYTES = 12;

/**
 * Cifrado de secretos recuperables de una sala (passcode, URL de anfitrión,
 * invitación) con AES-256-GCM (FR-020). La clave se deriva de
 * `LIVE_ROOM_ENCRYPTION_KEY` y nunca se registra.
 */
@Injectable()
export class SecretCipher {
  private readonly key?: Buffer;

  constructor(config: ConfigService) {
    const raw = config.get<string>('liveRoom.encryptionKey');
    this.key = raw ? createHash('sha256').update(raw).digest() : undefined;
  }

  encrypt(plain: string): string {
    const key = this.requireKey();
    const iv = randomBytes(IV_BYTES);
    const cipher = createCipheriv(ALGORITHM, key, iv);
    const encrypted = Buffer.concat([
      cipher.update(plain, 'utf8'),
      cipher.final(),
    ]);
    const tag = cipher.getAuthTag();
    return [
      VERSION,
      iv.toString('base64'),
      tag.toString('base64'),
      encrypted.toString('base64'),
    ].join(':');
  }

  decrypt(payload: string): string {
    const key = this.requireKey();
    const [version, ivB64, tagB64, dataB64] = payload.split(':');
    if (version !== VERSION || !ivB64 || !tagB64 || !dataB64) {
      throw new Error('Formato de secreto desconocido');
    }
    const decipher = createDecipheriv(
      ALGORITHM,
      key,
      Buffer.from(ivB64, 'base64'),
    );
    decipher.setAuthTag(Buffer.from(tagB64, 'base64'));
    return Buffer.concat([
      decipher.update(Buffer.from(dataB64, 'base64')),
      decipher.final(),
    ]).toString('utf8');
  }

  private requireKey(): Buffer {
    if (!this.key) {
      throw new Error('LIVE_ROOM_ENCRYPTION_KEY no configurada');
    }
    return this.key;
  }
}
