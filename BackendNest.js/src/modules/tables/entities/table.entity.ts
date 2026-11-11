import {
  Entity,
  PrimaryGeneratedColumn,
  Column,
  CreateDateColumn,
  UpdateDateColumn,
} from 'typeorm';
import { TableStatus, TableZone } from '../enums/index.js';

// @Entity('tables') le dice a TypeORM el nombre real de la tabla en Postgres.
// Sin el nombre explícito, TypeORM usaría "table", que es palabra reservada en Postgres
// y causaría errores al crear/consultar la tabla.
@Entity('tables')
export class Table {
  // Clave primaria autogenerada como UUID (convención del equipo)
  @PrimaryGeneratedColumn('uuid')
  id: string;

  // Columna int, con restricción unique a nivel de BD → refuerza RN-016
  // (número de mesa único) directamente en el esquema, no solo en la validación de la app
  @Column({ type: 'int', unique: true })
  number: number;

  // Capacidad como int. La regla "mayor que cero" (RN-017) no se pone aquí,
  // porque la entidad solo describe la tabla; esa validación va en el DTO.
  @Column({ type: 'int' })
  capacity: number;

  // Columna tipada con el enum TableZone: Postgres crea un tipo ENUM real,
  // así que a nivel de BD solo se pueden guardar esos 4 valores
  @Column({ type: 'enum', enum: TableZone })
  zone: TableZone;

  // Igual que zone, pero con default AVAILABLE → cumple RN-018
  // (toda mesa nueva inicia en AVAILABLE) sin que el código de negocio tenga que asignarlo
  @Column({
    type: 'enum',
    enum: TableStatus,
    default: TableStatus.AVAILABLE,
  })
  status: TableStatus;

  // Timestamps automáticos que TypeORM llena solo al crear/actualizar el registro
  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
