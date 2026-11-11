import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Reservation } from './entities/reservation.entity.js';
import { Table } from '../tables/entities/table.entity.js';
import { ReservationsService } from './reservations.service.js';
import { ReservationsController } from './reservations.controller.js';

@Module({
  imports: [TypeOrmModule.forFeature([Reservation, Table])],
  controllers: [ReservationsController],
  providers: [ReservationsService],
  exports: [ReservationsService],
})
export class ReservationsModule {}
