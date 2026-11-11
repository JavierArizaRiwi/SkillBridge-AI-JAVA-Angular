import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  PrimaryGeneratedColumn,
} from 'typeorm';
import { User } from '../../users/entities/user.entity.js';

// Token temporal de un solo uso para restablecer la contraseña (HU-018).
// Solo se guarda su hash SHA-256: quien lea la base de datos no puede usarlo
// (RN-103, RN-104).
@Entity('password_reset_tokens')
export class PasswordResetToken {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index('IDX_password_reset_tokens_user')
  @Column({ type: 'uuid' })
  userId: string;

  // Si se borra el usuario, sus tokens se borran con él
  @ManyToOne(() => User, { nullable: false, onDelete: 'CASCADE' })
  @JoinColumn({ name: 'userId' })
  user: User;

  // SHA-256 en hexadecimal (64 caracteres) del token enviado al usuario
  @Index('UQ_password_reset_tokens_hash', { unique: true })
  @Column({ type: 'varchar', length: 64 })
  tokenHash: string;

  @Column({ type: 'timestamptz' })
  expiresAt: Date;

  // null mientras no se haya usado; con fecha, el token ya no sirve (RN-104)
  @Column({ type: 'timestamptz', nullable: true })
  usedAt: Date | null;

  @CreateDateColumn({ type: 'timestamptz' })
  createdAt: Date;
}
