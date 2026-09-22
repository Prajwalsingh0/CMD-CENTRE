package com.aicommandcenter.user.entity;

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
 * A skill the user claims. Only skills present here can ever be marked "known"
 * by the job-intelligence module, which is how the product avoids inventing
 * qualifications the user never provided.
 */
@Entity
@Table(name = "user_skills")
@Getter
@Setter
@NoArgsConstructor
public class UserSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String skill;

    @Column(nullable = false, length = 20)
    private String level = "INTERMEDIATE";

    @Column(nullable = false)
    private boolean verified = false;

    @Column(nullable = false, length = 40)
    private String source = "SELF_REPORTED";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
