import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Table } from './entities/table.entity.js';
import { TablesService } from './tables.service.js';
import { TablesController } from './tables.controller.js';

@Module({
  imports: [TypeOrmModule.forFeature([Table])],
  controllers: [TablesController],
  providers: [TablesService],
  exports: [TypeOrmModule, TablesService],
})
export class TablesModule {}
