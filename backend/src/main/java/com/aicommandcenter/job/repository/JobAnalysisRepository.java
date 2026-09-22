package com.aicommandcenter.job.repository;

import com.aicommandcenter.job.entity.JobAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobAnalysisRepository extends JpaRepository<JobAnalysis, Long> {

    List<JobAnalysis> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<JobAnalysis> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);
}
