package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto;
import com.riwi.skillbridge.domain.model.MentorshipMode;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public record MentorshipAssignmentRequest(@NotNull UUID bookingId, @NotNull MentorshipMode mode, @NotNull Instant accessStartsAt, @NotNull Instant accessEndsAt, String liveRoomUrl) {}
