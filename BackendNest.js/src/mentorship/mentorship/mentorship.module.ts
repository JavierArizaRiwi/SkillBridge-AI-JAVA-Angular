import { Module } from '@nestjs/common';
import { LiveRoomModule } from '../live-room/live-room.module.js';

@Module({
  imports: [LiveRoomModule],
  exports: [LiveRoomModule],
})
export class MentorshipModule {}
