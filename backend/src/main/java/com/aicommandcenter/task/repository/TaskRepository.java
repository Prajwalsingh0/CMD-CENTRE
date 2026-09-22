package com.aicommandcenter.task.repository;

import com.aicommandcenter.task.entity.Task;
import com.aicommandcenter.task.entity.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    List<Task> findAllByUserId(Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, TaskStatus status);

    long countByUserIdAndStatusNot(Long userId, TaskStatus status);

    long countByUserIdAndGoalId(Long userId, Long goalId);

    long countByUserIdAndGoalIdAndStatus(Long userId, Long goalId, TaskStatus status);

    long countByUserIdAndDueDateBeforeAndStatusIn(Long userId, LocalDate date, List<TaskStatus> statuses);

    long countByUserIdAndDueDateBetweenAndStatusIn(Long userId, LocalDate from, LocalDate to, List<TaskStatus> statuses);

    List<Task> findTop8ByUserIdAndStatusInOrderByDueDateAsc(Long userId, List<TaskStatus> statuses);

    List<Task> findAllByUserIdAndGoalId(Long userId, Long goalId);

    List<Task> findAllByUserIdAndStatusAndCompletedAtAfter(Long userId, TaskStatus status, java.time.Instant after);

    @Query("select t from Task t where t.userId = :userId and t.dueDate < :today and t.status in :statuses order by t.dueDate asc")
    List<Task> findOverdue(@Param("userId") Long userId,
                           @Param("today") LocalDate today,
                           @Param("statuses") List<TaskStatus> statuses);

    @Query("select t.dueDate, count(t) from Task t where t.userId = :userId and t.status = com.aicommandcenter.task.entity.TaskStatus.COMPLETED and t.completedAt >= :since group by t.dueDate")
    List<Object[]> countCompletedGroupedByDueDate(@Param("userId") Long userId, @Param("since") java.time.Instant since);
}
