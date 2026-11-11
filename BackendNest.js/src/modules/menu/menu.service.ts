import { Injectable } from '@nestjs/common';

import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';

import { Category } from '../categories/entities/category.entity.js';
import { Product } from '../products/entities/product.entity.js';

import { CategoryStatus } from '../categories/enums/index.js';
import { ProductStatus } from '../products/enums/index.js';
import { EntityNotFoundException } from '../../common/exceptions/index.js';
import {
  MenuCategoryResponseDto,
  MenuCategorySummaryDto,
  MenuProductResponseDto,
  MenuResponseDto,
} from './dto/menu-response.dto.js';

@Injectable()
export class MenuService {
  constructor(
    @InjectRepository(Category)
    private readonly categoryRepository: Repository<Category>,

    @InjectRepository(Product)
    private readonly productRepository: Repository<Product>,
  ) {}

  /**
   * GET /menu
   *
   * Consulta el menú completo.
   *
   * RN-031: solo categorías ACTIVE.
   * RN-032: solo productos ACTIVE.
   * RN-033: UNAVAILABLE se muestra como no disponible.
   */
  async getMenu(): Promise<MenuResponseDto> {
    const categories = await this.categoryRepository.find({
      where: {
        status: CategoryStatus.ACTIVE,
      },
      order: {
        name: 'ASC',
      },
    });

    const products = await this.productRepository.find({
      where: {
        status: ProductStatus.ACTIVE,
      },
      order: {
        name: 'ASC',
      },
    });

    const categoriesWithProducts = categories.map((category) => {
      const categoryProducts = products.filter(
        (product) => product.categoryId === category.id,
      );

      return this.mapCategory(category, categoryProducts);
    });

    return {
      categories: categoriesWithProducts,
    };
  }

  /**
   * GET /menu/categories
   *
   * Devuelve solamente las categorías activas.
   */
  async getActiveCategories(): Promise<MenuCategorySummaryDto[]> {
    const categories = await this.categoryRepository.find({
      where: {
        status: CategoryStatus.ACTIVE,
      },
      order: {
        name: 'ASC',
      },
    });

    return categories.map((category) => ({
      id: category.id,
      name: category.name,
      description: category.description,
    }));
  }

  /**
   * GET /menu/categories/:categoryId/products
   *
   * Devuelve los productos activos de una categoría activa.
   */
  async getProductsByCategory(
    categoryId: string,
  ): Promise<MenuProductResponseDto[]> {
    const category = await this.categoryRepository.findOne({
      where: {
        id: categoryId,
        status: CategoryStatus.ACTIVE,
      },
    });

    if (!category) {
      throw new EntityNotFoundException('Categoría', categoryId);
    }

    const products = await this.productRepository.find({
      where: {
        categoryId,
        status: ProductStatus.ACTIVE,
      },
      order: {
        name: 'ASC',
      },
    });

    return products.map((product) => this.mapProduct(product));
  }

  /**
   * GET /menu/products/:id
   *
   * Devuelve el detalle de un producto activo
   * perteneciente a una categoría activa.
   */
  async getProductDetail(productId: string): Promise<MenuProductResponseDto> {
    const product = await this.productRepository.findOne({
      where: {
        id: productId,
        status: ProductStatus.ACTIVE,
      },
      relations: {
        category: true,
      },
    });

    if (!product) {
      throw new EntityNotFoundException('Producto', productId);
    }

    if (product.category.status !== CategoryStatus.ACTIVE) {
      throw new EntityNotFoundException('Categoría', product.category.id);
    }
    return this.mapProduct(product);
  }

  private mapCategory(
    category: Category,
    products: Product[],
  ): MenuCategoryResponseDto {
    return {
      id: category.id,
      name: category.name,
      description: category.description,
      products: products.map((product) => this.mapProduct(product)),
    };
  }

  private mapProduct(product: Product): MenuProductResponseDto {
    return {
      id: product.id,
      name: product.name,
      description: product.description,
      price: product.price,
      categoryId: product.categoryId,
      availability: product.availability,
    };
  }
}
