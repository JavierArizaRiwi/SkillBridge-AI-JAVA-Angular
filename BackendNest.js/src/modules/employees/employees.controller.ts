// Endpoints de empleados (/api/v1/employees, HU-016). Solo para ADMIN: el
// RolesGuard global responde 401 sin token y 403 con otro rol (RN-100 … RN-102).

import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
} from '@nestjs/common';
import {
  ApiBearerAuth,
  ApiOperation,
  ApiResponse,
  ApiTags,
} from '@nestjs/swagger';
import { Roles } from '../auth/decorators/index.js';
import { UserRole } from '../users/enums/index.js';
import {
  CreateEmployeeDto,
  EmployeeResponseDto,
  UpdateEmployeeDto,
  UpdateEmployeeStatusDto,
} from './dto/index.js';
import { EmployeesService } from './employees.service.js';

@ApiTags('Employees')
@ApiBearerAuth('bearer')
@Roles(UserRole.ADMIN)
@ApiResponse({ status: 401, description: 'Falta el token' })
@ApiResponse({ status: 403, description: 'El usuario no es ADMIN' })
@Controller('employees')
export class EmployeesController {
  constructor(private readonly employeesService: EmployeesService) {}

  // POST /api/v1/employees
  @Post()
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({
    summary: 'Registrar un empleado',
    description:
      'Crea su cuenta ACTIVE (con el rol inicial de su cargo) y su perfil con el cargo. La contraseña nunca se devuelve.',
  })
  @ApiResponse({
    status: 201,
    description: 'Empleado creado',
    type: EmployeeResponseDto,
  })
  @ApiResponse({
    status: 400,
    description:
      'Datos inválidos: nombre, correo, teléfono, cargo o contraseña',
  })
  @ApiResponse({
    status: 409,
    description: 'Ya existe una cuenta con ese correo (RN-095)',
  })
  create(@Body() dto: CreateEmployeeDto): Promise<EmployeeResponseDto> {
    return this.employeesService.create(dto);
  }

  // GET /api/v1/employees
  @Get()
  @ApiOperation({ summary: 'Listar empleados' })
  @ApiResponse({ status: 200, type: [EmployeeResponseDto] })
  findAll(): Promise<EmployeeResponseDto[]> {
    return this.employeesService.findAll();
  }

  // GET /api/v1/employees/{id}
  @Get(':id')
  @ApiOperation({ summary: 'Consultar un empleado' })
  @ApiResponse({ status: 200, type: EmployeeResponseDto })
  @ApiResponse({ status: 400, description: 'El id no es un UUID' })
  @ApiResponse({ status: 404, description: 'El empleado no existe' })
  findOne(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<EmployeeResponseDto> {
    return this.employeesService.findOne(id);
  }

  // PATCH /api/v1/employees/{id}
  @Patch(':id')
  @ApiOperation({
    summary: 'Actualizar los datos de un empleado',
    description:
      'Nombre, apellido, teléfono y cargo. El correo, el estado y el rol no se cambian aquí.',
  })
  @ApiResponse({ status: 200, type: EmployeeResponseDto })
  @ApiResponse({
    status: 400,
    description: 'Datos inválidos o campos no permitidos (email, status, role)',
  })
  @ApiResponse({ status: 404, description: 'El empleado no existe' })
  update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateEmployeeDto,
  ): Promise<EmployeeResponseDto> {
    return this.employeesService.update(id, dto);
  }

  // PATCH /api/v1/employees/{id}/status
  @Patch(':id/status')
  @ApiOperation({
    summary: 'Activar o desactivar la cuenta de un empleado',
    description: 'Un empleado INACTIVE no puede iniciar sesión (RN-098).',
  })
  @ApiResponse({ status: 200, type: EmployeeResponseDto })
  @ApiResponse({ status: 400, description: 'Estado inválido' })
  @ApiResponse({ status: 404, description: 'El empleado no existe' })
  updateStatus(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateEmployeeStatusDto,
  ): Promise<EmployeeResponseDto> {
    return this.employeesService.updateStatus(id, dto);
  }
}
