import type { ValueTransformer } from 'typeorm';

/**
 * PostgreSQL devuelve las columnas `decimal`/`numeric` como string para no
 * perder precisión. Para importes con 2 decimales (precios, totales) es
 * seguro convertirlos a number al leer.
 *
 * Uso: @Column({ type: 'decimal', precision: 10, scale: 2, transformer: DecimalTransformer })
 */
export const DecimalTransformer: ValueTransformer = {
  to: (value: number | null | undefined) => value,
  from: (value: string | null) => (value === null ? null : Number(value)),
};
