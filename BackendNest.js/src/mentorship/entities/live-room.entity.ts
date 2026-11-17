import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryColumn,
  UpdateDateColumn,
} from 'typeorm';
import {
  LiveProvider,
  LiveRoomSyncState,
} from '../live-room/domain/live-room.types.js';

@Entity('live_rooms')
export class LiveRoomEntity {
  @PrimaryColumn({ type: 'uuid' })
  id!: string;

  /** Restricción única: máximo una sala por asignación (FR-005/FR-006). */
  @Column({ type: 'uuid', unique: true })
  assignmentId!: string;

  @Column({ type: 'enum', enum: LiveProvider, enumName: 'live_provider' })
  provider!: LiveProvider;

  @Column({ type: 'varchar', length: 255 })
  externalRoomId!: string;

  @Column({ type: 'text' })
  joinUrl!: string;

  @Column({ type: 'text', nullable: true })
  hostUrl!: string | null;

  /** Secretos cifrados en reposo (AES-256-GCM, FR-020). */
  @Column({ type: 'text', nullable: true })
  passcodeEnc!: string | null;

  @Column({ type: 'text', nullable: true })
  hostSignatureEnc!: string | null;

  @Column({ type: 'text', nullable: true })
  inviteUrlEnc!: string | null;

  @Column({
    type: 'enum',
    enum: LiveRoomSyncState,
    enumName: 'live_room_sync_state',
    default: LiveRoomSyncState.SYNCED,
  })
  syncState!: LiveRoomSyncState;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt!: Date;

  @UpdateDateColumn({ type: 'timestamptz' })
  updatedAt!: Date;
}
