import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import {
  Check,
  Column,
  CreateDateColumn,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';
import { Category } from '../../categories/entities/category.entity.js';
import { DecimalTransformer } from '../../../common/transformers/index.js';
import { ProductAvailability, ProductStatus } from '../enums/index.js';

@Entity('products')
// RN-026: el precio es mayor que cero también a nivel de base de datos.
@Check('CHK_products_price_positive', '"price" > 0')
// Un mismo nombre puede repetirse en categorías distintas, no dentro de la misma.
@Index('UQ_products_category_name', ['categoryId', 'name'], { unique: true })
export class Product {
  @ApiProperty({ format: 'uuid' })
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @ApiProperty({ example: 'Hamburguesa Clásica' })
  @Column({ type: 'varchar', length: 100 })
  name: string;

  @ApiPropertyOptional({ nullable: true })
  @Column({ type: 'text', nullable: true })
  description: string | null;

  @ApiProperty({ example: 25000 })
  @Column({
    type: 'decimal',
    precision: 10,
    scale: 2,
    transformer: DecimalTransformer,
  })
  price: number;

  // RN-025: todo producto pertenece a una categoría existente.
  // RESTRICT: no se puede borrar una categoría con productos.
  @ApiProperty({ format: 'uuid' })
  @Column({ type: 'uuid' })
  categoryId: string;

  @ApiPropertyOptional({
    type: () => Category,
    description: 'Presente en las consultas (GET)',
  })
  @ManyToOne(() => Category, { onDelete: 'RESTRICT', nullable: false })
  @JoinColumn({ name: 'categoryId' })
  category: Category;

  // RN-027
  @ApiProperty({ enum: ProductStatus })
  @Column({ type: 'enum', enum: ProductStatus, default: ProductStatus.ACTIVE })
  status: ProductStatus;

  // RN-028
  @ApiProperty({ enum: ProductAvailability })
  @Column({
    type: 'enum',
    enum: ProductAvailability,
    default: ProductAvailability.AVAILABLE,
  })
  availability: ProductAvailability;

  @ApiProperty()
  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @ApiProperty()
  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
