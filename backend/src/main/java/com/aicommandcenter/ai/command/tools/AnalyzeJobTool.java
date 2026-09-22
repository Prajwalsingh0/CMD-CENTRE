package com.aicommandcenter.ai.command.tools;

import com.aicommandcenter.ai.command.CommandArgs;
import com.aicommandcenter.ai.command.CommandIntent;
import com.aicommandcenter.ai.command.CommandTool;
import com.aicommandcenter.ai.command.ToolResult;
import com.aicommandcenter.exception.BadRequestException;
import com.aicommandcenter.job.dto.JobAnalysisResponse;
import com.aicommandcenter.job.dto.JobAnalyzeRequest;
import com.aicommandcenter.job.service.JobAnalysisService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AnalyzeJobTool implements CommandTool {

    private static final int MIN_DESCRIPTION_CHARS = 40;

    private final JobAnalysisService jobAnalysisService;

    public AnalyzeJobTool(JobAnalysisService jobAnalysisService) {
        this.jobAnalysisService = jobAnalysisService;
    }

    @Override
    public CommandIntent intent() {
        return CommandIntent.ANALYZE_JOB;
    }

    @Override
    public Set<String> allowedArguments() {
        return CommandIntent.ANALYZE_JOB.arguments();
    }

    @Override
    public ToolResult execute(Long userId, CommandArgs args) {
        args.rejectUnknown(allowedArguments());
        String description = args.optional("description");
        if (description == null || description.length() < MIN_DESCRIPTION_CHARS) {
            throw new BadRequestException("MISSING_JOB_DESCRIPTION",
                    "Paste the job description text with the command (at least " + MIN_DESCRIPTION_CHARS + " characters)");
        }
        JobAnalysisResponse analysis = jobAnalysisService.analyze(userId,
                new JobAnalyzeRequest(description, args.optional("jobTitle"), args.optional("company")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("analysis", analysis);
        return ToolResult.of("Analysed " + (analysis.jobTitle() == null ? "the job description" : analysis.jobTitle())
                        + " — match score " + analysis.matchScore() + "%",
                List.of("Extracted " + analysis.requiredSkills().size() + " required skill(s)",
                        "Compared against your declared skills",
                        "Identified " + analysis.missingSkills().size() + " gap(s)",
                        "Generated " + analysis.interviewQuestions().size() + " interview question(s)"),
                data);
    }
}
