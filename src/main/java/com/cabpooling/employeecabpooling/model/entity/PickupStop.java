package com.cabpooling.employeecabpooling.model.entity;

import com.cabpooling.employeecabpooling.model.enums.StopType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "pickup_stops",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_cab_assignment_stop_order",
        columnNames = {"cab_assignment_id", "stop_order"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PickupStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cab_assignment_id", nullable = false)
    private CabAssignment cabAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 20)
    private StopType stopType;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "planned_time")
    private OffsetDateTime plannedTime;

    @Column(name = "actual_time")
    private OffsetDateTime actualTime;

    @Column(name = "distance_from_previous_km", nullable = false)
    @Builder.Default
    private Double distanceFromPreviousKm = 0.0;

    @Column(name = "duration_from_previous_minutes", nullable = false)
    @Builder.Default
    private Integer durationFromPreviousMinutes = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = OffsetDateTime.now();
        }
        if (this.distanceFromPreviousKm == null) {
            this.distanceFromPreviousKm = 0.0;
        }
        if (this.durationFromPreviousMinutes == null) {
            this.durationFromPreviousMinutes = 0;
        }
    }
}
