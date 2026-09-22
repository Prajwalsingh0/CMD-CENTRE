package com.aicommandcenter.notification.repository;

import com.aicommandcenter.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findAllByUserIdAndReadOrderByCreatedAtDesc(Long userId, boolean read);

    List<Notification> findTop10ByUserIdAndReadOrderByCreatedAtDesc(Long userId, boolean read);

    Optional<Notification> findByUserIdAndDedupeKey(Long userId, String dedupeKey);

    boolean existsByUserIdAndDedupeKey(Long userId, String dedupeKey);

    long countByUserIdAndRead(Long userId, boolean read);
}
