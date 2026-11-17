import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from '../../auth/auth.module.js';
import { SecretCipher } from '../../common/secret-cipher.js';
import { LiveRoomEntity } from '../entities/live-room.entity.js';
import { MentorshipAssignmentEntity } from '../entities/mentorship-assignment.entity.js';
import { OutboxEventEntity } from '../entities/outbox-event.entity.js';
import { DiscordLiveMeetingAdapter } from './adapters/discord-live-meeting.adapter.js';
import { GoogleMeetLiveMeetingAdapter } from './adapters/google-meet-live-meeting.adapter.js';
import { TypeOrmLiveRoomRepository } from './adapters/typeorm-live-room.repository.js';
import { ZoomLiveMeetingAdapter } from './adapters/zoom-live-meeting.adapter.js';
import { LiveProvider } from './domain/live-room.types.js';
import { LiveRoomController } from './live-room.controller.js';
import { LiveRoomMetrics } from './live-room.metrics.js';
import { LiveRoomService } from './live-room.service.js';
import {
  LIVE_MEETING_ADAPTERS,
  type LiveMeetingPort,
} from './ports/live-meeting.port.js';
import { LIVE_ROOM_REPOSITORY } from './ports/live-room.repository.js';

@Module({
  imports: [
    TypeOrmModule.forFeature([
      MentorshipAssignmentEntity,
      LiveRoomEntity,
      OutboxEventEntity,
    ]),
    AuthModule,
  ],
  controllers: [LiveRoomController],
  providers: [
    LiveRoomService,
    LiveRoomMetrics,
    SecretCipher,
    ZoomLiveMeetingAdapter,
    GoogleMeetLiveMeetingAdapter,
    DiscordLiveMeetingAdapter,
    { provide: LIVE_ROOM_REPOSITORY, useClass: TypeOrmLiveRoomRepository },
    {
      provide: LIVE_MEETING_ADAPTERS,
      useFactory: (
        zoom: ZoomLiveMeetingAdapter,
        meet: GoogleMeetLiveMeetingAdapter,
        discord: DiscordLiveMeetingAdapter,
      ): Map<LiveProvider, LiveMeetingPort> =>
        new Map<LiveProvider, LiveMeetingPort>([
          [LiveProvider.ZOOM, zoom],
          [LiveProvider.MEET, meet],
          [LiveProvider.DISCORD, discord],
        ]),
      inject: [
        ZoomLiveMeetingAdapter,
        GoogleMeetLiveMeetingAdapter,
        DiscordLiveMeetingAdapter,
      ],
    },
  ],
  exports: [LiveRoomService],
})
export class LiveRoomModule {}
