import { ApiProperty } from '@nestjs/swagger';
import { IsEnum } from 'class-validator';
import { CategoryStatus } from '../enums/category-status.enum.js';

export class UpdateCategoryStatusDto {
  @ApiProperty({ enum: CategoryStatus, example: CategoryStatus.INACTIVE })
  @IsEnum(CategoryStatus)
  status: CategoryStatus;
}
