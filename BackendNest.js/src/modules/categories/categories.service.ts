import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import {
  CreateCategoryDto,
  UpdateCategoryDto,
  UpdateCategoryStatusDto,
} from './dto/index.js';
import { Category } from './entities/category.entity.js';
import { CategoryStatus } from './enums/index.js';

@Injectable()
export class CategoriesService {
  constructor(
    @InjectRepository(Category)
    private readonly categories: Repository<Category>,
  ) {}

  /** RN-021, RN-022: nombre único y estado inicial ACTIVE por defecto. */
  async create(dto: CreateCategoryDto): Promise<Category> {
    await this.assertNameAvailable(dto.name);

    const category = this.categories.create({
      ...dto,
      status: CategoryStatus.ACTIVE,
    });
    return this.categories.save(category);
  }

  /**
   * RN-023: lista todas las categorías (activas e inactivas).
   * Desactivar una categoría no la elimina; el menú público filtra las activas (HU-005).
   */
  findAll(): Promise<Category[]> {
    return this.categories.find({
      order: { name: 'ASC' },
    });
  }

  async findOne(id: string): Promise<Category> {
    const category = await this.categories.findOneBy({ id });
    if (!category) {
      throw new EntityNotFoundException('Categoría', id);
    }
    return category;
  }

  /** RN-021: revalida unicidad del nombre si se modifica. */
  async update(id: string, dto: UpdateCategoryDto): Promise<Category> {
    const category = await this.findOne(id);

    if (dto.name && dto.name !== category.name) {
      await this.assertNameAvailable(dto.name, id);
    }

    this.categories.merge(category, dto);
    return this.categories.save(category);
  }

  /** RN-023, RN-024: cambiar estado entre los definidos por el sistema. */
  async updateStatus(
    id: string,
    dto: UpdateCategoryStatusDto,
  ): Promise<Category> {
    const category = await this.findOne(id);
    category.status = dto.status;
    return this.categories.save(category);
  }

  /** RN-021: el nombre de la categoría debe ser único en el sistema. */
  private async assertNameAvailable(
    name: string,
    excludeId?: string,
  ): Promise<void> {
    const existing = await this.categories.findOneBy({ name });
    if (existing && existing.id !== excludeId) {
      throw new BusinessRuleException(
        `Ya existe una categoría llamada "${name}"`,
        'RN-021',
      );
    }
  }
}
