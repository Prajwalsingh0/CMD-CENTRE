package com.aicommandcenter.job.entity;

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

/**
 * A stored job-description analysis. List-shaped fields are persisted as JSON text through
 * {@code JsonLists} so the schema is identical on H2 and PostgreSQL.
 */
@Entity
@Table(name = "job_analyses")
@Getter
@Setter
@NoArgsConstructor
public class JobAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_title", length = 200)
    private String jobTitle;

    @Column(length = 200)
    private String company;

    @Column(name = "raw_description", columnDefinition = "TEXT", nullable = false)
    private String rawDescription;

    @Column(name = "required_skills", columnDefinition = "TEXT")
    private String requiredSkills;

    @Column(name = "preferred_skills", columnDefinition = "TEXT")
    private String preferredSkills;

    @Column(columnDefinition = "TEXT")
    private String technologies;

    @Column(length = 200)
    private String experience;

    @Column(length = 200)
    private String education;

    @Column(columnDefinition = "TEXT")
    private String responsibilities;

    @Column(name = "match_score", nullable = false)
    private int matchScore;

    @Column(name = "gap_analysis", columnDefinition = "TEXT")
    private String gapAnalysis;

    @Column(name = "interview_topics", columnDefinition = "TEXT")
    private String interviewTopics;

    @Column(name = "learning_plan", columnDefinition = "TEXT")
    private String learningPlan;

    @Column(name = "interview_questions", columnDefinition = "TEXT")
    private String interviewQuestions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
