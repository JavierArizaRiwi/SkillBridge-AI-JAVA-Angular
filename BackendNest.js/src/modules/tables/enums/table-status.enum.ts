// Enum con los estados válidos de una mesa (RN-020: solo estos estados existen)
export enum TableStatus {
  AVAILABLE = 'AVAILABLE', // mesa libre, disponible para reservas/pedidos
  OCCUPIED = 'OCCUPIED', // mesa en uso
  OUT_OF_SERVICE = 'OUT_OF_SERVICE', // mesa fuera de servicio (RN-019: no usable)
}
