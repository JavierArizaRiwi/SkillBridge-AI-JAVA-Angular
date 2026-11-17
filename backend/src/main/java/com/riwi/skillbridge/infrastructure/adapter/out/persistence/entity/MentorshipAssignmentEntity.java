package com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity;

import com.riwi.skillbridge.domain.model.MentorshipMode;
import com.riwi.skillbridge.domain.model.MentorshipStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mentorship_assignments", uniqueConstraints = @UniqueConstraint(name = "uk_assignment_booking", columnNames = "booking_id"))
public class MentorshipAssignmentEntity {
    @Id private UUID id;
    @Column(name = "booking_id", nullable = false, updatable = false) private UUID bookingId;
    @Column(name = "customer_id", nullable = false, updatable = false) private UUID customerId;
    @Column(name = "offering_id", nullable = false, updatable = false) private UUID offeringId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MentorshipMode mode;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private MentorshipStatus status;
    @Column(name = "access_starts_at", nullable = false) private Instant accessStartsAt;
    @Column(name = "access_ends_at", nullable = false) private Instant accessEndsAt;
    @Column(name = "progress_percent", nullable = false) private int progressPercent;
    @Column(name = "live_room_url") private String liveRoomUrl;
    @Column(name = "zoom_meeting_id") private String zoomMeetingId;
    @Column(name = "zoom_passcode") private String zoomPasscode;
    @Column(name = "completed_at") private Instant completedAt;
    @Version private long version;

    protected MentorshipAssignmentEntity() {}
    public MentorshipAssignmentEntity(UUID id, UUID bookingId, UUID customerId, UUID offeringId, MentorshipMode mode, MentorshipStatus status, Instant accessStartsAt, Instant accessEndsAt, int progressPercent, String liveRoomUrl) {
        this.id=id; this.bookingId=bookingId; this.customerId=customerId; this.offeringId=offeringId; this.mode=mode; this.status=status; this.accessStartsAt=accessStartsAt; this.accessEndsAt=accessEndsAt; this.progressPercent=progressPercent; this.liveRoomUrl=liveRoomUrl;
    }
    public UUID getId(){return id;} public UUID getBookingId(){return bookingId;} public UUID getCustomerId(){return customerId;} public UUID getOfferingId(){return offeringId;}
    public MentorshipMode getMode(){return mode;} public MentorshipStatus getStatus(){return status;} public Instant getAccessStartsAt(){return accessStartsAt;} public Instant getAccessEndsAt(){return accessEndsAt;} public int getProgressPercent(){return progressPercent;} public String getLiveRoomUrl(){return liveRoomUrl;} public Instant getCompletedAt(){return completedAt;}
    public String getZoomMeetingId(){return zoomMeetingId;} public String getZoomPasscode(){return zoomPasscode;}
    public void setZoomMeeting(String meetingId, String passcode, String joinUrl){this.zoomMeetingId=meetingId; this.zoomPasscode=passcode; this.liveRoomUrl=joinUrl;}
    public void update(MentorshipStatus status, int progressPercent, Instant completedAt) { this.status=status; this.progressPercent=progressPercent; this.completedAt=completedAt; }
}
