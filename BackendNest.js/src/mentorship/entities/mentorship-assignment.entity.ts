import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryColumn,
  UpdateDateColumn,
} from 'typeorm';
import {
  MentorshipMode,
  MentorshipStatus,
} from '../live-room/domain/live-room.types.js';

@Entity('mentorship_assignments')
export class MentorshipAssignmentEntity {
  @PrimaryColumn({ type: 'uuid' })
  id!: string;

  @Column({ type: 'uuid', unique: true })
  bookingId!: string;

  @Column({ type: 'uuid' })
  customerId!: string;

  @Column({ type: 'uuid' })
  providerId!: string;

  @Column({ type: 'uuid' })
  offeringId!: string;

  @Column({ type: 'enum', enum: MentorshipMode, enumName: 'mentorship_mode' })
  mode!: MentorshipMode;

  @Column({
    type: 'enum',
    enum: MentorshipStatus,
    enumName: 'mentorship_status',
    default: MentorshipStatus.PENDING_PAYMENT,
  })
  status!: MentorshipStatus;

  @Column({ type: 'timestamptz' })
  accessStartsAt!: Date;

  @Column({ type: 'timestamptz' })
  accessEndsAt!: Date;

  @Column({ type: 'int', default: 0 })
  progressPercent!: number;

  @Column({ type: 'timestamptz', nullable: true })
  completedAt!: Date | null;

  @Column({ type: 'int', default: 0 })
  version!: number;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt!: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt!: Date;
}
