import type { MigrationInterface, QueryRunner } from 'typeorm';

/**
 * Esquema mínimo para HU-MNT-07: asignaciones, salas en vivo y outbox.
 *
 * Se usa SQL explícito (en lugar de `Table`) para fijar los tipos ENUM y los
 * nombres exactos de columnas de forma determinista entre versiones de TypeORM.
 */
export class InitialSchema1700000000000 implements MigrationInterface {
  name = 'InitialSchema1700000000000';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `CREATE TYPE "mentorship_mode" AS ENUM ('ASYNCHRONOUS', 'LIVE')`,
    );
    await queryRunner.query(
      `CREATE TYPE "mentorship_status" AS ENUM ('PENDING_PAYMENT', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'NO_SHOW')`,
    );
    await queryRunner.query(
      `CREATE TYPE "live_provider" AS ENUM ('ZOOM', 'MEET', 'DISCORD', 'CUSTOM_URL')`,
    );
    await queryRunner.query(
      `CREATE TYPE "live_room_sync_state" AS ENUM ('PENDING', 'SYNCED', 'FAILED')`,
    );

    await queryRunner.query(`
      CREATE TABLE "mentorship_assignments" (
        "id" uuid PRIMARY KEY,
        "bookingId" uuid NOT NULL UNIQUE,
        "customerId" uuid NOT NULL,
        "providerId" uuid NOT NULL,
        "offeringId" uuid NOT NULL,
        "mode" "mentorship_mode" NOT NULL,
        "status" "mentorship_status" NOT NULL DEFAULT 'PENDING_PAYMENT',
        "accessStartsAt" timestamptz NOT NULL,
        "accessEndsAt" timestamptz NOT NULL,
        "progressPercent" int NOT NULL DEFAULT 0,
        "completedAt" timestamptz,
        "version" int NOT NULL DEFAULT 0,
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "updatedAt" timestamptz NOT NULL DEFAULT now()
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "live_rooms" (
        "id" uuid PRIMARY KEY,
        "assignmentId" uuid NOT NULL UNIQUE,
        "provider" "live_provider" NOT NULL,
        "externalRoomId" varchar(255) NOT NULL,
        "joinUrl" text NOT NULL,
        "hostUrl" text,
        "passcodeEnc" text,
        "hostSignatureEnc" text,
        "inviteUrlEnc" text,
        "syncState" "live_room_sync_state" NOT NULL DEFAULT 'SYNCED',
        "createdAt" timestamptz NOT NULL DEFAULT now(),
        "updatedAt" timestamptz NOT NULL DEFAULT now(),
        CONSTRAINT "fk_live_rooms_assignment" FOREIGN KEY ("assignmentId")
          REFERENCES "mentorship_assignments" ("id") ON DELETE CASCADE
      )
    `);

    await queryRunner.query(`
      CREATE TABLE "outbox_events" (
        "id" uuid PRIMARY KEY,
        "eventId" uuid NOT NULL UNIQUE,
        "eventType" varchar(100) NOT NULL,
        "aggregateType" varchar(100) NOT NULL,
        "aggregateId" varchar(100) NOT NULL,
        "version" int NOT NULL DEFAULT 1,
        "correlationId" varchar(100),
        "payload" jsonb NOT NULL,
        "occurredAt" timestamptz NOT NULL,
        "publishedAt" timestamptz,
        "createdAt" timestamptz NOT NULL DEFAULT now()
      )
    `);

    await queryRunner.query(
      `CREATE INDEX "idx_outbox_events_unpublished" ON "outbox_events" ("publishedAt")`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE "outbox_events"`);
    await queryRunner.query(`DROP TABLE "live_rooms"`);
    await queryRunner.query(`DROP TABLE "mentorship_assignments"`);
    await queryRunner.query(`DROP TYPE "live_room_sync_state"`);
    await queryRunner.query(`DROP TYPE "live_provider"`);
    await queryRunner.query(`DROP TYPE "mentorship_status"`);
    await queryRunner.query(`DROP TYPE "mentorship_mode"`);
  }
}
