import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';
import { Table } from '../../tables/entities/table.entity.js';
import { ReservationStatus } from '../enums/reservation-status.enum.js';

@Entity('reservations')
@Index('IDX_reservations_table_date', ['tableId', 'date'])
export class Reservation {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ type: 'varchar', length: 150 })
  customerName: string;

  @Column({ type: 'varchar', length: 30 })
  phone: string;

  @Column({ type: 'varchar', length: 255 })
  email: string;

  @Column({ type: 'date' })
  date: string;

  @Column({ type: 'time' })
  time: string;

  @Column({ type: 'int' })
  guests: number;

  @Column({
    type: 'enum',
    enum: ReservationStatus,
    default: ReservationStatus.PENDING,
  })
  status: ReservationStatus;

  // Columna física de la FK, explícita para usarla en el índice y en los where
  @Column({ type: 'uuid', nullable: true })
  tableId: string | null;

  @ManyToOne(() => Table, { nullable: true, onDelete: 'SET NULL' })
  @JoinColumn({
    name: 'tableId',
    foreignKeyConstraintName: 'FK_reservations_table',
  })
  table: Table | null;

  @Column({ type: 'timestamptz', nullable: true })
  confirmedAt?: Date | null;

  @Column({ type: 'timestamptz', nullable: true })
  checkedInAt?: Date | null;

  @Column({ type: 'timestamptz', nullable: true })
  cancelledAt?: Date | null;

  @Column({ type: 'timestamptz', nullable: true })
  noShowAt?: Date | null;

  @Column({ type: 'timestamptz', nullable: true })
  completedAt?: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt: Date;
}
