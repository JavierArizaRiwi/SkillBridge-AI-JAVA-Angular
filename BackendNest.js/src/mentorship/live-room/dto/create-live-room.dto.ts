import { IsEnum } from 'class-validator';
import { LiveProvider } from '../domain/live-room.types.js';

export class CreateLiveRoomDto {
  @IsEnum(LiveProvider, {
    message: `provider debe ser uno de: ${Object.values(LiveProvider).join(', ')}`,
  })
  provider!: LiveProvider;
}
