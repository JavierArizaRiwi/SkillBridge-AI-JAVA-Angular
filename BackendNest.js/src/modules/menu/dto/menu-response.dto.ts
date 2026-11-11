import { ApiProperty, ApiPropertyOptional } from '@nestjs/swagger';
import { ProductAvailability } from '../../products/enums/index.js';

export class MenuProductResponseDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty({ example: 'Hamburguesa Clásica' })
  name: string;

  @ApiPropertyOptional({
    example: 'Hamburguesa con carne, queso y vegetales',
    nullable: true,
  })
  description: string | null;

  @ApiProperty({ example: 25000 })
  price: number;

  @ApiProperty({ format: 'uuid' })
  categoryId: string;

  @ApiProperty({
    enum: ProductAvailability,
    example: ProductAvailability.AVAILABLE,
  })
  availability: ProductAvailability;
}

/** Categoría sin productos: respuesta de GET /menu/categories. */
export class MenuCategorySummaryDto {
  @ApiProperty({ format: 'uuid' })
  id: string;

  @ApiProperty({ example: 'Hamburguesas' })
  name: string;

  @ApiPropertyOptional({
    example: 'Hamburguesas de la casa',
    nullable: true,
  })
  description: string | null;
}

/** Categoría con sus productos: se usa dentro de GET /menu. */
export class MenuCategoryResponseDto extends MenuCategorySummaryDto {
  @ApiProperty({ type: [MenuProductResponseDto] })
  products: MenuProductResponseDto[];
}

export class MenuResponseDto {
  @ApiProperty({ type: [MenuCategoryResponseDto] })
  categories: MenuCategoryResponseDto[];
}
