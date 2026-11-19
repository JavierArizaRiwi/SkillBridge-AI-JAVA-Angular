import { Test, TestingModule } from '@nestjs/testing';
import { MentorshipService } from './mentorship.service.js';

describe('MentorshipService', () => {
  let service: MentorshipService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [MentorshipService],
    }).compile();

    service = module.get<MentorshipService>(MentorshipService);
  });

  it('should be defined', () => {
    expect(service).toBeDefined();
  });
});
