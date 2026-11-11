import { describe, it, expect } from 'vitest';
import { expirationToSeconds } from './jwt-expiration.js';

describe('expirationToSeconds (RN-092)', () => {
  it.each([
    ['3600', 3600],
    ['45s', 45],
    ['15m', 900],
    ['1h', 3600],
    ['7d', 604800],
  ])('%s → %i segundos', (value, seconds) => {
    expect(expirationToSeconds(value)).toBe(seconds);
  });

  it.each(['', '1w', '1.5h', 'una hora'])('rechaza %j', (value) => {
    expect(() => expirationToSeconds(value)).toThrow('JWT_EXPIRES_IN');
  });
});
