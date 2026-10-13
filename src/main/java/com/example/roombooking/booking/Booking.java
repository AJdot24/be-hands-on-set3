package com.example.roombooking.booking;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import com.example.roombooking.room.Room;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String organiserEmail;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "booking_attendees", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "email", nullable = false)
    private Set<String> attendeeEmails = new LinkedHashSet<>();

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status = BookingStatus.ACTIVE;

    @Column
    private LocalDateTime cancelledAt;

    protected Booking() {
    }

    public Booking(Room room, String title, String organiserEmail, Set<String> attendeeEmails,
                   LocalDateTime startAt, LocalDateTime endAt, LocalDateTime createdAt) {
        this.room = room;
        this.title = title;
        this.organiserEmail = organiserEmail;
        this.attendeeEmails = new LinkedHashSet<>(attendeeEmails);
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = createdAt;
        this.status = BookingStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public String getTitle() {
        return title;
    }

    public String getOrganiserEmail() {
        return organiserEmail;
    }

    public Set<String> getAttendeeEmails() {
        return attendeeEmails;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void cancel(LocalDateTime cancelledAt) {
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }
}
