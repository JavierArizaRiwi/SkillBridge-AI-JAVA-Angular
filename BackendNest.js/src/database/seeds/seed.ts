import 'reflect-metadata';
import { Logger } from '@nestjs/common';
import dataSource from '../data-source.js';
import { Category } from '../../modules/categories/entities/category.entity.js';
import { Product } from '../../modules/products/entities/product.entity.js';
import { Table } from '../../modules/tables/entities/table.entity.js';
import { categorySeeds, productSeeds, tableSeeds } from './seed-data.js';

const logger = new Logger('Seed');

/**
 * Carga datos de desarrollo. Es idempotente: se puede ejecutar tantas veces
 * como haga falta porque cada fila se busca por su clave natural antes de
 * insertarla (número de mesa, nombre de categoría, nombre de producto dentro
 * de su categoría). Nunca actualiza ni borra lo que ya existe, así que no
 * pisa los cambios que alguien haya hecho probando.
 *
 * Uso: npm run seed
 */
async function seed(): Promise<void> {
  await dataSource.initialize();

  const tables = dataSource.getRepository(Table);
  const categories = dataSource.getRepository(Category);
  const products = dataSource.getRepository(Product);

  let createdTables = 0;
  for (const seedTable of tableSeeds) {
    const exists = await tables.existsBy({ number: seedTable.number });
    if (!exists) {
      await tables.save(tables.create(seedTable));
      createdTables += 1;
    }
  }

  let createdCategories = 0;
  const categoryIdByName = new Map<string, string>();
  for (const seedCategory of categorySeeds) {
    let category = await categories.findOneBy({ name: seedCategory.name });
    if (!category) {
      category = await categories.save(categories.create(seedCategory));
      createdCategories += 1;
    }
    categoryIdByName.set(category.name, category.id);
  }

  let createdProducts = 0;
  for (const { category, ...seedProduct } of productSeeds) {
    const categoryId = categoryIdByName.get(category);
    if (!categoryId) {
      logger.warn(
        `Se omite "${seedProduct.name}": la categoría "${category}" no existe`,
      );
      continue;
    }

    const exists = await products.existsBy({
      categoryId,
      name: seedProduct.name,
    });
    if (!exists) {
      await products.save(products.create({ ...seedProduct, categoryId }));
      createdProducts += 1;
    }
  }

  logger.log(
    `Mesas: ${createdTables} nuevas de ${tableSeeds.length} · ` +
      `Categorías: ${createdCategories} de ${categorySeeds.length} · ` +
      `Productos: ${createdProducts} de ${productSeeds.length}`,
  );

  await dataSource.destroy();
}

seed().catch((error: unknown) => {
  logger.error('No se pudo cargar la información de desarrollo', error);
  process.exit(1);
});
