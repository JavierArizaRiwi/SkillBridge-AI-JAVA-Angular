import {
  Body,
  Controller,
  Get,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
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
  CreateCategoryDto,
  UpdateCategoryDto,
  UpdateCategoryStatusDto,
} from './dto/index.js';
import { Category } from './entities/category.entity.js';
import { CategoriesService } from './categories.service.js';

@ApiTags('Categories')
// Requiere access token (RN-094); los permisos por rol llegan con HU-017
@ApiBearerAuth('bearer')
@ApiBadRequestResponse({ description: 'Datos inválidos o campo no permitido' })
@Controller('categories')
export class CategoriesController {
  constructor(private readonly categoriesService: CategoriesService) {}

  @Post()
  @ApiOperation({ summary: 'Registrar una categoría del menú' })
  @ApiCreatedResponse({ type: Category })
  @ApiConflictResponse({
    description: 'Ya existe una categoría con ese nombre (RN-021)',
  })
  create(@Body() dto: CreateCategoryDto): Promise<Category> {
    return this.categoriesService.create(dto);
  }

  @Get()
  @ApiOperation({
    summary: 'Listar todas las categorías',
    description:
      'Incluye categorías activas e inactivas. El menú público filtra solo las activas (RN-023).',
  })
  @ApiOkResponse({ type: Category, isArray: true })
  findAll(): Promise<Category[]> {
    return this.categoriesService.findAll();
  }

  @Get(':id')
  @ApiOperation({ summary: 'Consultar una categoría por ID' })
  @ApiParam({ name: 'id', format: 'uuid' })
  @ApiOkResponse({ type: Category })
  @ApiNotFoundResponse({ description: 'Categoría inexistente' })
  findOne(@Param('id', ParseUUIDPipe) id: string): Promise<Category> {
    return this.categoriesService.findOne(id);
  }

  @Patch(':id')
  @ApiOperation({
    summary: 'Actualizar nombre o descripción de una categoría',
    description:
      'El estado se cambia exclusivamente por PATCH /categories/{id}/status.',
  })
  @ApiParam({ name: 'id', format: 'uuid' })
  @ApiOkResponse({ type: Category })
  @ApiNotFoundResponse({ description: 'Categoría inexistente' })
  @ApiConflictResponse({
    description: 'Ya existe una categoría con ese nombre (RN-021)',
  })
  update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateCategoryDto,
  ): Promise<Category> {
    return this.categoriesService.update(id, dto);
  }

  @Patch(':id/status')
  @ApiOperation({
    summary: 'Activar o desactivar una categoría',
    description:
      'Una categoría INACTIVE no se muestra en el menú público (RN-023). Desactivarla no la elimina.',
  })
  @ApiParam({ name: 'id', format: 'uuid' })
  @ApiOkResponse({ type: Category })
  @ApiNotFoundResponse({ description: 'Categoría inexistente' })
  updateStatus(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateCategoryStatusDto,
  ): Promise<Category> {
    return this.categoriesService.updateStatus(id, dto);
  }
}
