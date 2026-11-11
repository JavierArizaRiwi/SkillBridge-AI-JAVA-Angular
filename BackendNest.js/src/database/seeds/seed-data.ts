import { CategoryStatus } from '../../modules/categories/enums/index.js';
import {
  ProductAvailability,
  ProductStatus,
} from '../../modules/products/enums/index.js';
import { TableStatus, TableZone } from '../../modules/tables/enums/index.js';

/**
 * Datos de desarrollo. La clave natural de cada fila (número de mesa,
 * nombre de categoría, nombre de producto dentro de su categoría) es lo que
 * hace idempotente al runner: si la fila ya existe, no se vuelve a insertar.
 */

export const tableSeeds = [
  { number: 1, capacity: 2, zone: TableZone.INTERIOR },
  { number: 2, capacity: 2, zone: TableZone.INTERIOR },
  { number: 3, capacity: 4, zone: TableZone.INTERIOR },
  { number: 4, capacity: 4, zone: TableZone.INTERIOR },
  { number: 5, capacity: 6, zone: TableZone.INTERIOR },
  { number: 6, capacity: 4, zone: TableZone.TERRACE },
  { number: 7, capacity: 6, zone: TableZone.TERRACE },
  { number: 8, capacity: 8, zone: TableZone.TERRACE },
  { number: 9, capacity: 2, zone: TableZone.BAR },
  { number: 10, capacity: 2, zone: TableZone.BAR },
  { number: 11, capacity: 8, zone: TableZone.VIP },
  // Una mesa fuera de servicio para poder probar RN-019 en HU-006
  {
    number: 12,
    capacity: 6,
    zone: TableZone.VIP,
    status: TableStatus.OUT_OF_SERVICE,
  },
];

export const categorySeeds = [
  { name: 'Entradas', description: 'Para abrir el apetito' },
  { name: 'Hamburguesas', description: 'Carne 150 g en pan brioche' },
  { name: 'Pastas', description: 'Frescas, hechas en casa' },
  { name: 'Bebidas', description: 'Frías y calientes' },
  // Categoría inactiva: no debe aparecer en el menú público (RN-031)
  {
    name: 'Postres de temporada',
    description: 'Fuera de carta por ahora',
    status: CategoryStatus.INACTIVE,
  },
];

export const productSeeds = [
  { category: 'Entradas', name: 'Nachos con queso', price: 18000 },
  { category: 'Entradas', name: 'Alitas BBQ', price: 24000 },
  { category: 'Entradas', name: 'Bastones de mozzarella', price: 16000 },
  { category: 'Hamburguesas', name: 'Hamburguesa Clásica', price: 25000 },
  { category: 'Hamburguesas', name: 'Hamburguesa BBQ', price: 28000 },
  { category: 'Hamburguesas', name: 'Hamburguesa Doble', price: 34000 },
  // No disponible: sigue en el menú, marcada como no disponible (RN-033)
  {
    category: 'Hamburguesas',
    name: 'Hamburguesa Vegana',
    price: 27000,
    availability: ProductAvailability.UNAVAILABLE,
  },
  { category: 'Pastas', name: 'Pasta Alfredo', price: 26000 },
  { category: 'Pastas', name: 'Lasaña de la casa', price: 30000 },
  { category: 'Pastas', name: 'Ravioles de espinaca', price: 28000 },
  { category: 'Bebidas', name: 'Limonada Natural', price: 8000 },
  { category: 'Bebidas', name: 'Jugo de Maracuyá', price: 9000 },
  { category: 'Bebidas', name: 'Café Americano', price: 6000 },
  // No disponible: segunda muestra de RN-033, en otra categoría
  {
    category: 'Bebidas',
    name: 'Cerveza Artesanal',
    price: 12000,
    availability: ProductAvailability.UNAVAILABLE,
  },
  // Inactivo: no debe aparecer en el menú público (RN-032)
  {
    category: 'Bebidas',
    name: 'Malteada de Oreo',
    price: 14000,
    status: ProductStatus.INACTIVE,
  },
  // Producto de una categoría inactiva: tampoco aparece (RN-031)
  { category: 'Postres de temporada', name: 'Cheesecake', price: 15000 },
];
