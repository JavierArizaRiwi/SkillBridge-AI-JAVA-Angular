import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';

import { MenuController } from './menu.controller.js';
import { MenuService } from './menu.service.js';

import { Category } from '../categories/entities/category.entity.js';
import { Product } from '../products/entities/product.entity.js';

@Module({
  imports: [TypeOrmModule.forFeature([Category, Product])],
  controllers: [MenuController],
  providers: [MenuService],
  exports: [MenuService],
})
export class MenuModule {}
