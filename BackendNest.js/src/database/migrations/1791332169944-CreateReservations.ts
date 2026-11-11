import type { MigrationInterface, QueryRunner } from 'typeorm';

export class CreateReservations1791332169944 implements MigrationInterface {
  name = 'CreateReservations1791332169944';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `CREATE TYPE "public"."reservations_status_enum" AS ENUM('PENDING', 'CONFIRMED', 'CHECKED_IN', 'CANCELLED', 'NO_SHOW', 'COMPLETED')`,
    );
    await queryRunner.query(`
      CREATE TABLE "reservations" (
        "id" uuid NOT NULL DEFAULT uuid_generate_v4(),
        "customerName" character varying(150) NOT NULL,
        "phone" character varying(30) NOT NULL,
        "email" character varying(255) NOT NULL,
        "date" date NOT NULL,
        "time" TIME NOT NULL,
        "guests" integer NOT NULL,
        "status" "public"."reservations_status_enum" NOT NULL DEFAULT 'PENDING',
        "tableId" uuid,
        "confirmedAt" TIMESTAMP WITH TIME ZONE,
        "checkedInAt" TIMESTAMP WITH TIME ZONE,
        "cancelledAt" TIMESTAMP WITH TIME ZONE,
        "noShowAt" TIMESTAMP WITH TIME ZONE,
        "completedAt" TIMESTAMP WITH TIME ZONE,
        "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
        "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
        CONSTRAINT "PK_reservations_id" PRIMARY KEY ("id")
      )
    `);
    await queryRunner.query(
      `CREATE INDEX "IDX_reservations_table_date" ON "reservations" ("tableId", "date")`,
    );
    await queryRunner.query(
      `ALTER TABLE "reservations" ADD CONSTRAINT "FK_reservations_table" FOREIGN KEY ("tableId") REFERENCES "tables"("id") ON DELETE SET NULL ON UPDATE NO ACTION`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE "reservations" DROP CONSTRAINT "FK_reservations_table"`,
    );
    await queryRunner.query(
      `DROP INDEX "public"."IDX_reservations_table_date"`,
    );
    await queryRunner.query(`DROP TABLE "reservations"`);
    await queryRunner.query(`DROP TYPE "public"."reservations_status_enum"`);
  }
}
