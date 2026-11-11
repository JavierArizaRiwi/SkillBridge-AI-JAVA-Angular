import { IsEnum } from 'class-validator';
import { ProductStatus } from '../enums/product-status.enum.js';
import { ApiProperty } from '@nestjs/swagger';

export class UpdateProductStatusDto {
  @ApiProperty({ enum: ProductStatus })
  @IsEnum(ProductStatus)
  status: ProductStatus;
}
