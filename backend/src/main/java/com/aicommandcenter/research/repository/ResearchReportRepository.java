package com.aicommandcenter.research.repository;

import com.aicommandcenter.research.entity.ResearchReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResearchReportRepository extends JpaRepository<ResearchReport, Long> {

    List<ResearchReport> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<ResearchReport> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);
}
