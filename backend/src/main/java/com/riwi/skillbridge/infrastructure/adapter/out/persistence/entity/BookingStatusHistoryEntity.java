package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.model.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_status_history")
public class BookingStatusHistoryEntity {
    @Id
    private UUID id;
    @Column(name = "booking_id")
    private UUID bookingId;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private BookingStatus previousStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status")
    private BookingStatus newStatus;
    @Column(name = "changed_by")
    private UUID changedBy;
    @Column(name = "changed_at")
    private Instant changedAt;

    protected BookingStatusHistoryEntity() {
    }

    public BookingStatusHistoryEntity(
            UUID id,
            UUID bookingId,
            BookingStatus previousStatus,
            BookingStatus newStatus,
            UUID changedBy,
            Instant changedAt) {
        this.id = id;
        this.bookingId = bookingId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public UUID getId() { return id; }
    public UUID getBookingId() { return bookingId; }
    public BookingStatus getPreviousStatus() { return previousStatus; }
    public BookingStatus getNewStatus() { return newStatus; }
    public UUID getChangedBy() { return changedBy; }
    public Instant getChangedAt() { return changedAt; }
}
