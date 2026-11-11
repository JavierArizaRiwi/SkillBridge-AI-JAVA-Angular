import type { MigrationInterface, QueryRunner } from 'typeorm';

export class CreateTables1790098563238 implements MigrationInterface {
  name = 'CreateTables1790098563238';

  public async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `CREATE TYPE "public"."tables_zone_enum" AS ENUM('INTERIOR', 'TERRACE', 'BAR', 'VIP')`,
    );
    await queryRunner.query(
      `CREATE TYPE "public"."tables_status_enum" AS ENUM('AVAILABLE', 'OCCUPIED', 'OUT_OF_SERVICE')`,
    );
    await queryRunner.query(
      `CREATE TABLE "tables" ("id" uuid NOT NULL DEFAULT uuid_generate_v4(), "number" integer NOT NULL, "capacity" integer NOT NULL, "zone" "public"."tables_zone_enum" NOT NULL, "status" "public"."tables_status_enum" NOT NULL DEFAULT 'AVAILABLE', "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(), CONSTRAINT "UQ_0aa8f1290718849823b581ec144" UNIQUE ("number"), CONSTRAINT "PK_7cf2aca7af9550742f855d4eb69" PRIMARY KEY ("id"))`,
    );
  }

  public async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(`DROP TABLE "tables"`);
    await queryRunner.query(`DROP TYPE "public"."tables_status_enum"`);
    await queryRunner.query(`DROP TYPE "public"."tables_zone_enum"`);
  }
}
