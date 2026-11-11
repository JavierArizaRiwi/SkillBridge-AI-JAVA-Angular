// Este archivo es el "cerebro" de las mesas: aquí viven las reglas de negocio.
// Los DTOs solo validan que los datos tengan la forma correcta;
// aquí decidimos qué hacer con esos datos (crear, buscar, actualizar, rechazar).

import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import {
  BusinessRuleException,
  EntityNotFoundException,
} from '../../common/exceptions/index.js';
import { Table } from './entities/table.entity.js';
import { TableStatus } from './enums/index.js';
import { CreateTableDto } from './dto/create-table.dto.js';
import { UpdateTableDto } from './dto/update-table.dto.js';
import { UpdateTableStatusDto } from './dto/update-table-status.dto.js';
import { FilterTablesDto } from './dto/filter-tables.dto.js';

@Injectable()
export class TablesService {
  constructor(
    // @InjectRepository le pide a NestJS/TypeORM: "dame el repositorio
    // que sabe hablar con la tabla 'tables' de Postgres". El Repository
    // es el objeto que realmente hace las consultas SQL (find, save, etc.)
    @InjectRepository(Table)
    private readonly tableRepository: Repository<Table>,
  ) {}

  // Crea una mesa nueva.
  async create(dto: CreateTableDto): Promise<Table> {
    // Antes de guardar, revisamos que no exista ya una mesa con ese número.
    // Si existe, esta función lanza el error y create() se detiene aquí.
    await this.ensureNumberIsUnique(dto.number);

    // tableRepository.create() NO guarda nada todavía, solo arma
    // un objeto Table en memoria con los datos del DTO
    const table = this.tableRepository.create({
      ...dto,
      status: TableStatus.AVAILABLE, // RN-018: toda mesa nueva inicia AVAILABLE
    });

    // save() sí escribe el registro en la base de datos y devuelve
    // la mesa ya guardada (con su id generado, createdAt, etc.)
    return this.tableRepository.save(table);
  }

  // Devuelve la lista de mesas, aplicando los filtros que lleguen (o ninguno).
  async findAll(filters: FilterTablesDto): Promise<Table[]> {
    // createQueryBuilder arma la consulta SQL "a mano", pieza por pieza,
    // en vez de usar un find() simple. Es útil cuando los filtros son opcionales.
    const query = this.tableRepository
      .createQueryBuilder('table')
      .orderBy('table.number', 'ASC'); // siempre ordenado por número de mesa

    // Cada "if" agrega una condición SOLO si el filtro fue enviado.
    // Así, si no mandan ningún filtro, la consulta trae todas las mesas.
    if (filters.status) {
      query.andWhere('table.status = :status', { status: filters.status });
    }

    if (filters.zone) {
      query.andWhere('table.zone = :zone', { zone: filters.zone });
    }

    if (filters.capacity) {
      // >= porque el filtro es "capacidad mínima", no capacidad exacta
      query.andWhere('table.capacity >= :capacity', {
        capacity: filters.capacity,
      });
    }

    // getMany() ejecuta finalmente la consulta armada y trae los resultados
    return query.getMany();
  }

  // Busca una mesa por su id. Si no existe, corta el flujo con un 404.
  async findOne(id: string): Promise<Table> {
    const table = await this.tableRepository.findOne({ where: { id } });

    if (!table) {
      // EntityNotFoundException (src/common/exceptions) produce un 404 con el
      // formato uniforme de errores que usa todo el proyecto
      throw new EntityNotFoundException('Mesa', id);
    }

    return table;
  }

  // Actualiza los datos generales de una mesa (número, capacidad, zona).
  async update(id: string, dto: UpdateTableDto): Promise<Table> {
    const table = await this.findOne(id); // si no existe, ya lanza 404 aquí

    // Solo si el DTO trae un 'number' distinto al actual, revisamos
    // de nuevo que no choque con otra mesa. Si no cambia el número,
    // no hace falta revalidar nada.
    if (dto.number !== undefined && dto.number !== table.number) {
      await this.ensureNumberIsUnique(dto.number);
    }

    // Object.assign copia sobre 'table' solo los campos que vengan en el dto,
    // dejando intactos los que no se enviaron (por eso el DTO es "parcial")
    Object.assign(table, dto);

    return this.tableRepository.save(table);
  }

  // Cambia únicamente el estado de una mesa (AVAILABLE/OCCUPIED/OUT_OF_SERVICE).
  async updateStatus(id: string, dto: UpdateTableStatusDto): Promise<Table> {
    const table = await this.findOne(id); // 404 si no existe

    // RN-020 (solo estados válidos) ya quedó garantizada antes de llegar
    // aquí, por el @IsEnum() del DTO — si el status fuera inválido,
    // Nest ya habría respondido 400 antes de entrar a este método.
    //
    // RN-019 dice que una mesa OUT_OF_SERVICE no puede usarse para
    // reservas ni pedidos — esa regla NO se aplica aquí, sino en los
    // módulos de reservas/pedidos (HU-006/HU-020), porque este método
    // solo se encarga de guardar el cambio de estado en sí.
    table.status = dto.status;

    return this.tableRepository.save(table);
  }

  // Método privado (solo se usa dentro de esta clase) que revisa
  // si ya existe una mesa con el número dado, y si es así, corta el flujo.
  private async ensureNumberIsUnique(number: number): Promise<void> {
    const existing = await this.tableRepository.findOne({
      where: { number },
    });

    if (existing) {
      // BusinessRuleException sigue siendo un 409, pero agrega el campo "rule"
      // para dejar trazada la regla de negocio incumplida
      throw new BusinessRuleException(
        `Ya existe una mesa con el número ${number}`,
        'RN-016',
      );
    }
  }
}
