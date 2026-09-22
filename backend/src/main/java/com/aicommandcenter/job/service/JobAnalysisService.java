package com.aicommandcenter.job.service;

import com.aicommandcenter.ai.AiException;
import com.aicommandcenter.ai.AiRequest;
import com.aicommandcenter.ai.AiService;
import com.aicommandcenter.ai.AiTask;
import com.aicommandcenter.ai.activity.AiActivityService;
import com.aicommandcenter.common.JsonLists;
import com.aicommandcenter.exception.ResourceNotFoundException;
import com.aicommandcenter.job.dto.JobAnalysisResponse;
import com.aicommandcenter.job.dto.JobAnalyzeRequest;
import com.aicommandcenter.job.dto.SkillMatch;
import com.aicommandcenter.job.entity.JobAnalysis;
import com.aicommandcenter.job.extract.JobDescriptionParser;
import com.aicommandcenter.job.extract.SkillDictionary;
import com.aicommandcenter.job.repository.JobAnalysisRepository;
import com.aicommandcenter.user.entity.UserSkill;
import com.aicommandcenter.user.repository.UserSkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Job intelligence.
 *
 * <p>The contract that keeps this honest: <strong>the only source of a "known" skill is the
 * user's own skill profile.</strong> Extraction from the description decides what the job wants;
 * it can never decide what the candidate has. Unknown means unknown.</p>
 */
@Service
public class JobAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(JobAnalysisService.class);
    private static final Pattern NUMBERED = Pattern.compile("(?m)^\\s*(\\d{1,2})[.)]\\s*(.+?)\\s*$");
    private static final int MAX_PROMPT_CHARS = 8_000;

    private final JobAnalysisRepository jobRepository;
    private final UserSkillRepository skillRepository;
    private final AiService aiService;
    private final AiActivityService activityService;

    public JobAnalysisService(JobAnalysisRepository jobRepository,
                              UserSkillRepository skillRepository,
                              AiService aiService,
                              AiActivityService activityService) {
        this.jobRepository = jobRepository;
        this.skillRepository = skillRepository;
        this.aiService = aiService;
        this.activityService = activityService;
    }

    @Transactional
    public JobAnalysisResponse analyze(Long userId, JobAnalyzeRequest request) {
        JobDescriptionParser.ParsedJobDescription parsed = JobDescriptionParser.parse(request.description());

        String title = firstNonBlank(request.jobTitle(), parsed.jobTitle());
        String company = firstNonBlank(request.company(), parsed.company());

        List<String> required = parsed.requiredSkills();
        List<String> preferred = parsed.preferredSkills();
        List<SkillMatch> matches = match(userId, required, preferred);
        int score = score(matches);
        List<String> missing = matches.stream()
                .filter(match -> SkillMatch.MISSING.equals(match.status()))
                .map(SkillMatch::skill)
                .toList();

        JobAnalysis entity = new JobAnalysis();
        entity.setUserId(userId);
        entity.setJobTitle(title);
        entity.setCompany(company);
        entity.setRawDescription(request.description());
        entity.setRequiredSkills(JsonLists.write(required));
        entity.setPreferredSkills(JsonLists.write(preferred));
        entity.setTechnologies(JsonLists.write(parsed.technologies()));
        entity.setExperience(parsed.experience());
        entity.setEducation(parsed.education());
        entity.setResponsibilities(JsonLists.write(parsed.responsibilities()));
        entity.setMatchScore(score);
        entity.setGapAnalysis(gapAnalysis(matches, required, score));
        entity.setInterviewTopics(JsonLists.write(interviewTopics(matches, parsed)));
        entity.setLearningPlan(JsonLists.write(learningPlan(missing)));
        entity.setInterviewQuestions(JsonLists.write(interviewQuestions(request.description(), matches)));
        JobAnalysis saved = jobRepository.save(entity);

        activityService.record(userId, "Analyse job description: " + (title == null ? "untitled role" : title),
                "ANALYZE_JOB", AiActivityService.ActivityStatus.SUCCESS,
                "Match score " + score + "% — " + missing.size() + " missing skill(s), "
                        + generatedPrepCount(saved) + " interview question(s) generated.");

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<JobAnalysisResponse> list(Long userId) {
        return jobRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobAnalysisResponse get(Long userId, Long id) {
        return toResponse(require(userId, id));
    }

    /** Regenerates only the preparation material, keeping the original extraction and score. */
    @Transactional
    public JobAnalysisResponse regeneratePrep(Long userId, Long id) {
        JobAnalysis entity = require(userId, id);
        List<String> required = JsonLists.read(entity.getRequiredSkills());
        List<String> preferred = JsonLists.read(entity.getPreferredSkills());
        List<SkillMatch> matches = match(userId, required, preferred);
        List<String> missing = matches.stream()
                .filter(match -> SkillMatch.MISSING.equals(match.status()))
                .map(SkillMatch::skill)
                .toList();
        entity.setInterviewTopics(JsonLists.write(interviewTopics(matches,
                new JobDescriptionParser.ParsedJobDescription(entity.getJobTitle(), entity.getCompany(),
                        required, preferred, JsonLists.read(entity.getTechnologies()),
                        entity.getExperience(), entity.getEducation(),
                        JsonLists.read(entity.getResponsibilities())))));
        entity.setLearningPlan(JsonLists.write(learningPlan(missing)));
        entity.setInterviewQuestions(JsonLists.write(interviewQuestions(entity.getRawDescription(), matches)));
        JobAnalysis saved = jobRepository.save(entity);
        activityService.record(userId, "Regenerate interview prep", "ANALYZE_JOB",
                AiActivityService.ActivityStatus.SUCCESS,
                "Rebuilt interview preparation for '" + describe(saved) + "'.");
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        jobRepository.delete(require(userId, id));
    }

    @Transactional(readOnly = true)
    public List<JobAnalysisResponse> recent(Long userId, int limit) {
        return jobRepository.findTop5ByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(Math.max(1, limit))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobAnalysis require(Long userId, Long id) {
        return jobRepository.findById(id)
                .filter(analysis -> analysis.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Job analysis", id));
    }

    // ---------------------------------------------------------------- matching

    private List<SkillMatch> match(Long userId, List<String> required, List<String> preferred) {
        Map<String, UserSkill> owned = new LinkedHashMap<>();
        for (UserSkill skill : skillRepository.findAllByUserIdOrderBySkillAsc(userId)) {
            owned.put(SkillDictionary.canonicalise(skill.getSkill()).toLowerCase(Locale.ROOT), skill);
        }
        List<SkillMatch> matches = new ArrayList<>();
        for (String skill : required) {
            matches.add(toMatch(skill, true, owned));
        }
        for (String skill : preferred) {
            matches.add(toMatch(skill, false, owned));
        }
        return matches;
    }

    private SkillMatch toMatch(String skill, boolean required, Map<String, UserSkill> owned) {
        UserSkill userSkill = owned.get(SkillDictionary.canonicalise(skill).toLowerCase(Locale.ROOT));
        boolean has = userSkill != null;
        return SkillMatch.of(skill, required, has, has && userSkill.isVerified(), has ? userSkill.getLevel() : null);
    }

    private int score(List<SkillMatch> matches) {
        if (matches.isEmpty()) {
            return 0;
        }
        double denominator = 0;
        double numerator = 0;
        for (SkillMatch match : matches) {
            double weight = match.required() ? 1.0 : 0.5;
            denominator += weight;
            if (SkillMatch.KNOWN.equals(match.status())) {
                numerator += weight;
            } else if (SkillMatch.UNVERIFIED.equals(match.status())) {
                numerator += weight * 0.5;
            }
        }
        return denominator == 0 ? 0 : (int) Math.round(numerator * 100.0 / denominator);
    }

    private String gapAnalysis(List<SkillMatch> matches, List<String> required, int score) {
        long known = matches.stream().filter(match -> match.required() && SkillMatch.KNOWN.equals(match.status())).count();
        long unverified = matches.stream()
                .filter(match -> match.required() && SkillMatch.UNVERIFIED.equals(match.status())).count();
        List<String> missing = matches.stream()
                .filter(match -> SkillMatch.MISSING.equals(match.status()))
                .map(SkillMatch::skill)
                .toList();
        StringBuilder builder = new StringBuilder();
        builder.append("Overall match: ").append(score).append("%.\n");
        builder.append("Required skills matched outright: ").append(known).append(" of ").append(required.size()).append(".\n");
        if (unverified > 0) {
            builder.append("Declared but unverified (you should prepare evidence for these): ")
                    .append(join(matches.stream()
                            .filter(match -> SkillMatch.UNVERIFIED.equals(match.status()))
                            .map(SkillMatch::skill).toList()))
                    .append(".\n");
        }
        if (missing.isEmpty()) {
            builder.append("No gaps found against the required skill list. Focus on depth and system design.");
        } else {
            builder.append("Gaps to close: ").append(join(missing)).append(".\n");
            builder.append("This report only uses skills you have recorded in your profile — it never assumes "
                    + "experience you have not declared. Add skills in your profile if any of the gaps above "
                    + "are out of date.");
        }
        return builder.toString();
    }

    private List<String> interviewTopics(List<SkillMatch> matches, JobDescriptionParser.ParsedJobDescription parsed) {
        Set<String> topics = new LinkedHashSet<>();
        for (SkillMatch match : matches) {
            if (!SkillMatch.KNOWN.equals(match.status())) {
                topics.add("Deep dive: " + match.skill() + " (" + match.status().toLowerCase(Locale.ROOT) + ")");
            }
        }
        for (String skill : parsed.technologies()) {
            if (topics.size() >= 12) {
                break;
            }
            topics.add("Applied fundamentals: " + skill);
        }
        topics.add("System design: scaling a service that uses the primary stack of this role");
        topics.add("Behavioural: a production incident you diagnosed end to end");
        topics.add("Project walkthrough: the strongest item on your profile for this role");
        return topics.stream().limit(14).toList();
    }

    private List<String> learningPlan(List<String> missing) {
        if (missing.isEmpty()) {
            return List.of(
                    "No skill gaps detected. Spend the preparation time on depth: system design, failure modes "
                            + "and measurable impact stories.",
                    "Re-read your own strongest project and be ready to defend every design decision in it.");
        }
        List<String> plan = new ArrayList<>();
        int day = 1;
        for (String skill : missing.stream().limit(8).toList()) {
            plan.add("Day " + day + "-" + (day + 2) + ": " + skill
                    + " — one focused tutorial, then build a small feature that forces you to use it.");
            day += 3;
        }
        plan.add("Day " + day + "-" + (day + 1)
                + ": build one end-to-end mini project that combines the gaps above; this becomes your talking point.");
        plan.add("Final day: rehearse the interview questions on this page out loud, with a timer.");
        return plan;
    }

    private List<String> interviewQuestions(String description, List<SkillMatch> matches) {
        List<String> focus = matches.stream()
                .filter(match -> !SkillMatch.KNOWN.equals(match.status()))
                .map(SkillMatch::skill)
                .limit(10)
                .toList();
        List<String> fallback = focus.stream()
                .map(skill -> "Explain how you would apply " + skill + " in a production service, "
                        + "including one trade-off you accepted.")
                .toList();
        try {
            String raw = aiService.complete(AiRequest.of(AiTask.QUESTIONS,
                    "Generate concise, technical interview questions for this role.",
                    truncate(description, MAX_PROMPT_CHARS))).text();
            List<String> parsed = parseNumbered(raw);
            if (!parsed.isEmpty()) {
                return parsed.size() > 15 ? parsed.subList(0, 15) : parsed;
            }
        } catch (AiException ex) {
            log.info("Falling back to deterministic interview questions: {}", ex.getCode());
        }
        return fallback.isEmpty() ? List.of("Walk through the most complex system you have built.") : fallback;
    }

    private static List<String> parseNumbered(String text) {
        List<String> questions = new ArrayList<>();
        if (text == null) {
            return questions;
        }
        Matcher matcher = NUMBERED.matcher(text);
        while (matcher.find()) {
            String question = matcher.group(2).strip();
            if (question.length() >= 12) {
                questions.add(question);
            }
            if (questions.size() >= 15) {
                break;
            }
        }
        return questions;
    }

    // ---------------------------------------------------------------- mapping

    public JobAnalysisResponse toResponse(JobAnalysis entity) {
        List<String> required = JsonLists.read(entity.getRequiredSkills());
        List<String> preferred = JsonLists.read(entity.getPreferredSkills());
        List<SkillMatch> matches = new ArrayList<>();
        // Recompute display matches from the stored extraction plus the CURRENT profile, so the
        // report never shows stale "known" claims after the user edits their skills.
        Map<String, UserSkill> owned = new LinkedHashMap<>();
        for (UserSkill skill : skillRepository.findAllByUserIdOrderBySkillAsc(entity.getUserId())) {
            owned.put(SkillDictionary.canonicalise(skill.getSkill()).toLowerCase(Locale.ROOT), skill);
        }
        for (String skill : required) {
            matches.add(toMatch(skill, true, owned));
        }
        for (String skill : preferred) {
            matches.add(toMatch(skill, false, owned));
        }
        List<String> missing = matches.stream()
                .filter(match -> SkillMatch.MISSING.equals(match.status()))
                .map(SkillMatch::skill)
                .toList();
        return new JobAnalysisResponse(
                entity.getId(),
                entity.getJobTitle(),
                entity.getCompany(),
                entity.getExperience(),
                entity.getEducation(),
                required,
                preferred,
                JsonLists.read(entity.getTechnologies()),
                JsonLists.read(entity.getResponsibilities()),
                matches,
                entity.getMatchScore(),
                missing,
                entity.getGapAnalysis(),
                JsonLists.read(entity.getInterviewTopics()),
                JsonLists.read(entity.getLearningPlan()),
                JsonLists.read(entity.getInterviewQuestions()),
                entity.getCreatedAt());
    }

    private int generatedPrepCount(JobAnalysis entity) {
        return JsonLists.read(entity.getInterviewQuestions()).size();
    }

    private static String describe(JobAnalysis entity) {
        return entity.getJobTitle() == null ? "untitled role" : entity.getJobTitle();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback;
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
