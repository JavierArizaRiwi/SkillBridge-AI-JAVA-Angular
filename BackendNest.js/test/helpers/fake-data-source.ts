import { vi } from 'vitest';

/**
 * DataSource mínimo para pruebas HTTP: evita abrir una conexión real a
 * PostgreSQL cuando se importa `AppModule` (el repositorio se sustituye).
 */
export function createFakeDataSource() {
  const repository = {
    findOne: vi.fn().mockResolvedValue(null),
    find: vi.fn().mockResolvedValue([]),
    create: vi.fn((value: unknown) => value),
    save: vi.fn(async (value: unknown) => value),
    insert: vi.fn().mockResolvedValue({ identifiers: [] }),
  };

  const manager = {
    getRepository: vi.fn(() => repository),
    query: vi.fn().mockResolvedValue([]),
    transaction: vi.fn(),
  };

  return {
    isInitialized: false,
    options: { type: 'postgres' },
    entityMetadatas: [],
    manager,
    getRepository: vi.fn(() => repository),
    getTreeRepository: vi.fn(() => repository),
    getMongoRepository: vi.fn(() => repository),
    query: vi.fn().mockResolvedValue([]),
    transaction: vi.fn(async (work: (m: unknown) => unknown) => work(manager)),
    initialize: vi.fn().mockResolvedValue(undefined),
    destroy: vi.fn().mockResolvedValue(undefined),
  };
}
