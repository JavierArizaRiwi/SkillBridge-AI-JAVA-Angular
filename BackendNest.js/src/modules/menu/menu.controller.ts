import { Controller, Get, Param, ParseUUIDPipe } from '@nestjs/common';

import { ApiOperation, ApiParam, ApiResponse, ApiTags } from '@nestjs/swagger';

import { MenuService } from './menu.service.js';
import { Public } from '../auth/decorators/index.js';

import {
  MenuCategorySummaryDto,
  MenuProductResponseDto,
  MenuResponseDto,
} from './dto/menu-response.dto.js';

// Menú público: se consulta sin iniciar sesión (HU-005)
@ApiTags('Menu')
@Public()
@Controller('menu')
export class MenuController {
  constructor(private readonly menuService: MenuService) {}

  @Get()
  @ApiOperation({
    summary: 'Consultar menú completo',
    description: 'Devuelve las categorías activas y sus productos activos.',
  })
  @ApiResponse({
    status: 200,
    description: 'Menú consultado correctamente.',
    type: MenuResponseDto,
  })
  async getMenu(): Promise<MenuResponseDto> {
    return this.menuService.getMenu();
  }

  @Get('categories')
  @ApiOperation({
    summary: 'Consultar categorías activas',
  })
  @ApiResponse({
    status: 200,
    description: 'Categorías activas consultadas correctamente.',
    type: [MenuCategorySummaryDto],
  })
  async getActiveCategories(): Promise<MenuCategorySummaryDto[]> {
    return this.menuService.getActiveCategories();
  }

  @Get('categories/:categoryId/products')
  @ApiOperation({
    summary: 'Consultar productos de una categoría',
  })
  @ApiParam({
    name: 'categoryId',
    description: 'Identificador UUID de la categoría',
    format: 'uuid',
  })
  @ApiResponse({
    status: 200,
    description: 'Productos de la categoría consultados correctamente.',
    type: [MenuProductResponseDto],
  })
  @ApiResponse({
    status: 404,
    description: 'La categoría no existe o no está activa.',
  })
  async getProductsByCategory(
    @Param('categoryId', new ParseUUIDPipe()) categoryId: string,
  ): Promise<MenuProductResponseDto[]> {
    return this.menuService.getProductsByCategory(categoryId);
  }

  @Get('products/:id')
  @ApiOperation({
    summary: 'Consultar detalle de un producto',
  })
  @ApiParam({
    name: 'id',
    description: 'Identificador UUID del producto',
    format: 'uuid',
  })
  @ApiResponse({
    status: 200,
    description: 'Producto consultado correctamente.',
    type: MenuProductResponseDto,
  })
  @ApiResponse({
    status: 404,
    description:
      'El producto no existe, no está activo o su categoría no está activa.',
  })
  async getProductDetail(
    @Param('id', new ParseUUIDPipe()) id: string,
  ): Promise<MenuProductResponseDto> {
    return this.menuService.getProductDetail(id);
  }
}
