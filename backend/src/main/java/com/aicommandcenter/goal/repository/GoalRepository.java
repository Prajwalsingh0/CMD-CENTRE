package com.aicommandcenter.goal.repository;

import com.aicommandcenter.goal.entity.Goal;
import com.aicommandcenter.goal.entity.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Goal> findAllByUserIdAndStatusOrderByCreatedAtDesc(Long userId, GoalStatus status);

    /** Direct children of a goal, oldest first so a tree renders in creation order. */
    List<Goal> findAllByUserIdAndParentGoalIdOrderByCreatedAtAsc(Long userId, Long parentGoalId);

    long countByUserIdAndParentGoalId(Long userId, Long parentGoalId);

    long countByUserIdAndStatus(Long userId, GoalStatus status);

    List<Goal> findTop8ByUserIdAndStatusOrderByDeadlineAsc(Long userId, GoalStatus status);
}
