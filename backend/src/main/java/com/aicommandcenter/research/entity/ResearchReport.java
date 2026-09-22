package com.aicommandcenter.research.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "research_reports")
@Getter
@Setter
@NoArgsConstructor
public class ResearchReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 240)
    private String topic;

    @Column(columnDefinition = "TEXT")
    private String overview;

    @Column(name = "key_findings", columnDefinition = "TEXT")
    private String keyFindings;

    @Column(columnDefinition = "TEXT")
    private String concepts;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Column(columnDefinition = "TEXT")
    private String sources;

    @Column(columnDefinition = "TEXT")
    private String summary;

    /** Whether the report is backed by retrieved sources. Never set speculatively. */
    @Column(nullable = false)
    private boolean grounded = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
