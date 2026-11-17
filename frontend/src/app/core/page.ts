/** Respuesta paginada estándar de la API (PageResponse del backend). */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Tamaño máximo de página aceptado por la API. */
export const MAX_PAGE_SIZE = 100;
