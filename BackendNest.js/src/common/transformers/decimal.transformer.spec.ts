import { DecimalTransformer } from './decimal.transformer.js';

describe('DecimalTransformer', () => {
  it('convierte el string de PostgreSQL a number al leer', () => {
    expect(DecimalTransformer.from('25000.50')).toBe(25000.5);
    expect(DecimalTransformer.from('0.10')).toBe(0.1);
  });

  it('conserva null al leer', () => {
    expect(DecimalTransformer.from(null)).toBeNull();
  });

  it('no altera el valor al escribir', () => {
    expect(DecimalTransformer.to(19.99)).toBe(19.99);
    expect(DecimalTransformer.to(null)).toBeNull();
  });
});
