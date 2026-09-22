package com.aicommandcenter.user.repository;

import com.aicommandcenter.user.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findAllByUserIdOrderBySkillAsc(Long userId);

    Optional<UserSkill> findByUserIdAndSkillIgnoreCase(Long userId, String skill);

    boolean existsByUserIdAndSkillIgnoreCase(Long userId, String skill);

    List<UserSkill> deleteByUserIdAndSkillIgnoreCase(Long userId, String skill);
}
