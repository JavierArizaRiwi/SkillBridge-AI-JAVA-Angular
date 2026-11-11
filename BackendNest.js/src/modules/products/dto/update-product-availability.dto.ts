import { IsEnum } from 'class-validator';
import { ProductAvailability } from '../enums/product-status.enum.js';
import { ApiProperty } from '@nestjs/swagger';

export class UpdateProductAvailabilityDto {
  @ApiProperty({ enum: ProductAvailability })
  @IsEnum(ProductAvailability)
  availability: ProductAvailability;
}
