import {
  Column,
  CreateDateColumn,
  Entity,
  JoinColumn,
  OneToOne,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';
import { User } from '../../users/entities/user.entity.js';
import { EmployeePosition } from '../enums/index.js';

// Perfil de un empleado (HU-016). El email, el estado y el rol viven en su
// cuenta de `users`; aquí solo el cargo (D-002 en docs/decisiones.md).
@Entity('employees')
export class Employee {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'uuid' })
  userId: string;

  // Una cuenta tiene como máximo un perfil de empleado; si se borra la
  // cuenta, el perfil se borra con ella
  @OneToOne(() => User, { nullable: false, onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  // Todo empleado tiene un cargo (RN-096)
  @Column({ type: 'enum', enum: EmployeePosition })
  position: EmployeePosition;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
