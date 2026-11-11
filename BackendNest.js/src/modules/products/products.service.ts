import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { Category } from '../categories/entities/category.entity.js';
import {
  CreateProductDto,
  FilterProductsDto,
  UpdateProductAvailabilityDto,
  UpdateProductDto,
  UpdateProductStatusDto,
} from './dto/index.js';
import { Product } from './entities/product.entity.js';

@Injectable()
export class ProductsService {
  constructor(
    @InjectRepository(Product)
    private readonly products: Repository<Product>,
    @InjectRepository(Category)
    private readonly categories: Repository<Category>,
  ) {}

  /** RN-025, RN-027, RN-028: categoría existente; estado y disponibilidad iniciales por defecto de la entidad. */
  async create(dto: CreateProductDto): Promise<Product> {
    await this.assertCategoryExists(dto.categoryId);
    await this.assertNameAvailable(dto.categoryId, dto.name);

    const product = this.products.create(dto);
    return this.products.save(product);
  }

  findAll(filters: FilterProductsDto): Promise<Product[]> {
    return this.products.find({
      where: {
        ...(filters.categoryId && { categoryId: filters.categoryId }),
        ...(filters.status && { status: filters.status }),
        ...(filters.availability && { availability: filters.availability }),
      },
      relations: { category: true },
      order: { name: 'ASC' },
    });
  }

  async findOne(id: string): Promise<Product> {
    const product = await this.products.findOne({
      where: { id },
      relations: { category: true },
    });
    if (!product) {
      throw new EntityNotFoundException('Producto', id);
    }
    return product;
  }

  async update(id: string, dto: UpdateProductDto): Promise<Product> {
    const product = await this.findOne(id);

    const categoryId = dto.categoryId ?? product.categoryId;
    const name = dto.name ?? product.name;

    if (dto.categoryId && dto.categoryId !== product.categoryId) {
      await this.assertCategoryExists(dto.categoryId);
    }
    if (categoryId !== product.categoryId || name !== product.name) {
      await this.assertNameAvailable(categoryId, name, id);
    }

    this.products.merge(product, dto);
    await this.products.save(product);
    return this.findOne(id);
  }

  /** RN-029: un producto INACTIVE deja de mostrarse en el menú público (HU-005). */
  async updateStatus(
    id: string,
    dto: UpdateProductStatusDto,
  ): Promise<Product> {
    const product = await this.findOne(id);
    product.status = dto.status;
    return this.products.save(product);
  }

  /**
   * RN-030: un producto UNAVAILABLE sigue registrado y visible como "no disponible",
   * pero no puede agregarse a nuevos pedidos. Esa validación la aplica el módulo de
   * pedidos (Sprint 4) al agregar ítems: debe rechazar `availability !== AVAILABLE`.
   */
  async updateAvailability(
    id: string,
    dto: UpdateProductAvailabilityDto,
  ): Promise<Product> {
    const product = await this.findOne(id);
    product.availability = dto.availability;
    return this.products.save(product);
  }

  private async assertCategoryExists(categoryId: string): Promise<void> {
    const exists = await this.categories.existsBy({ id: categoryId });
    if (!exists) {
      throw new BusinessRuleException(
        `La categoría con id ${categoryId} no existe`,
        'RN-025',
      );
    }
  }

  /** Decisión de #45: el nombre es único dentro de cada categoría (UQ_products_category_name). */
  private async assertNameAvailable(
    categoryId: string,
    name: string,
    excludeId?: string,
  ): Promise<void> {
    const existing = await this.products.findOneBy({ categoryId, name });
    if (existing && existing.id !== excludeId) {
      throw new BusinessRuleException(
        `Ya existe un producto llamado "${name}" en esa categoría`,
      );
    }
  }
}
