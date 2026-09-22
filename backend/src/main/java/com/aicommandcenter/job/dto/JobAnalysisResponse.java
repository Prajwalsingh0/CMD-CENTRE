package com.aicommandcenter.job.dto;

import java.time.Instant;
import java.util.List;

public record JobAnalysisResponse(
        Long id,
        String jobTitle,
        String company,
        String experience,
        String education,
        List<String> requiredSkills,
        List<String> preferredSkills,
        List<String> technologies,
        List<String> responsibilities,
        List<SkillMatch> skillMatches,
        int matchScore,
        List<String> missingSkills,
        String gapAnalysis,
        List<String> interviewTopics,
        List<String> learningPlan,
        List<String> interviewQuestions,
        Instant createdAt) {
}
