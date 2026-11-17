import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryColumn,
} from 'typeorm';

@Entity('outbox_events')
export class OutboxEventEntity {
  @PrimaryColumn({ type: 'uuid' })
  id!: string;

  @Column({ type: 'uuid', unique: true })
  eventId!: string;

  @Column({ type: 'varchar', length: 100 })
  eventType!: string;

  @Column({ type: 'varchar', length: 100 })
  aggregateType!: string;

  @Column({ type: 'varchar', length: 100 })
  aggregateId!: string;

  @Column({ type: 'int', default: 1 })
  version!: number;

  @Column({ type: 'varchar', length: 100, nullable: true })
  correlationId!: string | null;

  @Column({ type: 'jsonb' })
  payload!: Record<string, unknown>;

  @Column({ type: 'timestamptz' })
  occurredAt!: Date;

  @Column({ type: 'timestamptz', nullable: true })
  publishedAt!: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt!: Date;
}
