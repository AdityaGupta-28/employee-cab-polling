package com.cabpooling.employeecabpooling.model.entity;

import com.cabpooling.employeecabpooling.model.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cab_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cab_id", nullable = false)
    private Cab cab;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    @Column(name = "assignment_date", nullable = false)
    private LocalDate assignmentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.PLANNED;

    @Column(name = "total_distance_km", nullable = false)
    @Builder.Default
    private Double totalDistanceKm = 0.0;

    @Column(name = "total_duration_minutes", nullable = false)
    @Builder.Default
    private Integer totalDurationMinutes = 0;

    @Column(name = "has_escort", nullable = false)
    @Builder.Default
    private Boolean hasEscort = false;

    @OneToMany(mappedBy = "cabAssignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopOrder ASC")
    @Builder.Default
    private List<PickupStop> stops = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.status == null) {
            this.status = AssignmentStatus.PLANNED;
        }
        if (this.totalDistanceKm == null) {
            this.totalDistanceKm = 0.0;
        }
        if (this.totalDurationMinutes == null) {
            this.totalDurationMinutes = 0;
        }
        if (this.hasEscort == null) {
            this.hasEscort = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
