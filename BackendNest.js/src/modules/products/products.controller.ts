import {
  Body,
  Controller,
  Get,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiBadRequestResponse,
  ApiConflictResponse,
  ApiCreatedResponse,
  ApiNotFoundResponse,
  ApiOkResponse,
  ApiOperation,
  ApiParam,
  ApiTags,
} from '@nestjs/swagger';
import {
  CreateProductDto,
  FilterProductsDto,
  UpdateProductAvailabilityDto,
  UpdateProductDto,
  UpdateProductStatusDto,
} from './dto/index.js';
import { Product } from './entities/product.entity.js';
import { ProductsService } from './products.service.js';

@ApiTags('Products')
// Requiere access token (RN-094); los permisos por rol llegan con HU-017
@ApiBearerAuth('bearer')
@ApiBadRequestResponse({ description: 'Datos inválidos o campo no permitido' })
@Controller('products')
export class ProductsController {
  constructor(private readonly productsService: ProductsService) {}

  @Post()
  @ApiOperation({ summary: 'Registrar un producto del menú' })
  @ApiCreatedResponse({ type: Product })
  @ApiConflictResponse({
    description:
      'Categoría inexistente (RN-025) o nombre repetido en la categoría',
  })
  create(@Body() dto: CreateProductDto): Promise<Product> {
    return this.productsService.create(dto);
  }

  @Get()
  @ApiOperation({
    summary: 'Listar productos con filtros opcionales',
    description:
      'Incluye la categoría de cada producto. Filtra por `categoryId`, `status` y `availability`.',
  })
  @ApiOkResponse({ type: Product, isArray: true })
  findAll(@Query() filters: FilterProductsDto): Promise<Product[]> {
    return this.productsService.findAll(filters);
  }

  @Get(':id')
  @ApiOperation({ summary: 'Consultar un producto' })
  @ApiParam({ name: 'id', format: 'uuid' })
  @ApiOkResponse({ type: Product })
  @ApiNotFoundResponse({ description: 'Producto inexistente' })
  findOne(@Param('id', ParseUUIDPipe) id: string): Promise<Product> {
    return this.productsService.findOne(id);
  }

  @Patch(':id')
  @ApiOperation({
    summary: 'Actualizar nombre, descripción, precio o categoría',
    description:
      'El estado y la disponibilidad se cambian por sus endpoints específicos.',
  })
  @ApiParam({ name: 'id', format: 'uuid' })
  @ApiOkResponse({ type: Product })
  @ApiNotFoundResponse({ description: 'Producto inexistente' })
  @ApiConflictResponse({
    description:
      'Categoría inexistente (RN-025) o nombre repetido en la categoría',
  })
  update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateProductDto,
  ): Promise<Product> {
    return this.productsService.update(id, dto);
  }

  @Patch(':id/status')
  @ApiOperation({
    summary: 'Activar o desactivar un producto',
    description: 'Un producto INACTIVE no aparece en el menú público (RN-029).',
  })
  @ApiParam({ name: 'id', format: 'uuid' })
  @ApiOkResponse({ type: Product })
  @ApiNotFoundResponse({ description: 'Producto inexistente' })
  updateStatus(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateProductStatusDto,
  ): Promise<Product> {
    return this.productsService.updateStatus(id, dto);
  }

  @Patch(':id/availability')
  @ApiOperation({
    summary: 'Cambiar la disponibilidad de un producto',
    description:
      'Un producto UNAVAILABLE sigue en el menú marcado como no disponible, pero no puede agregarse a nuevos pedidos (RN-030).',
  })
  @ApiParam({ name: 'id', format: 'uuid' })
  @ApiOkResponse({ type: Product })
  @ApiNotFoundResponse({ description: 'Producto inexistente' })
  updateAvailability(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateProductAvailabilityDto,
  ): Promise<Product> {
    return this.productsService.updateAvailability(id, dto);
  }
}
