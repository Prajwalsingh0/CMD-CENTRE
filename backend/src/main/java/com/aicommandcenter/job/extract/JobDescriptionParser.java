package com.aicommandcenter.job.extract;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic job-description parser.
 *
 * <p>This is the path the product always uses for structure. When a real LLM provider is
 * configured the same fields are additionally requested as JSON and merged, but the deterministic
 * result is the baseline — so the feature works, and is testable, with no credentials.</p>
 */
public final class JobDescriptionParser {

    private static final Pattern YEARS = Pattern.compile(
            "(\\d{1,2})\\s*(?:-\\s*(\\d{1,2})\\s*)?\\+?\\s*(?:to\\s+\\d{1,2}\\s*)?(?:years?|yrs?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EDUCATION = Pattern.compile(
            "(bachelor[^,.;\\n]{0,40}|master[^,.;\\n]{0,40}|b\\.?tech[^,.;\\n]{0,20}|b\\.?e\\b[^,.;\\n]{0,20}|"
                    + "mca\\b|bsc[^,.;\\n]{0,20}|msc[^,.;\\n]{0,20}|phd[^,.;\\n]{0,20}|degree in [^,.;\\n]{0,40})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_LABEL = Pattern.compile(
            "(?:job title|position|role|title)\\s*[:\\-]\\s*([^\\n]{3,80})", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPANY_LABEL = Pattern.compile(
            "(?:company|organisation|organization|employer)\\s*[:\\-]\\s*([^\\n]{2,80})", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPANY_AT = Pattern.compile(
            "\\bat\\s+([A-Z][A-Za-z0-9&.'\\-]*(?:\\s+[A-Z][A-Za-z0-9&.'\\-]*){0,3})");
    private static final Pattern TITLE_ROLE = Pattern.compile(
            "\\b((?:senior|junior|lead|principal|staff)?\\s*"
                    + "(?:java|python|javascript|typescript|golang|go|rust|ruby|php|kotlin|scala|c\\+\\+|c#|csharp|"
                    + "android|ios|mobile|web|node|react|angular|vue|dotnet|\\.net|salesforce|sap|"
                    + "backend|back-end|frontend|front-end|full[- ]stack|software|data|devops|cloud|platform|"
                    + "security|network|database|qa|test|ai|ml|machine learning|site reliability)?\\s*"
                    + "(?:engineer|developer|programmer|architect|analyst|scientist|consultant|manager|administrator|"
                    + "specialist|intern|lead))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> REQUIRED_HEADINGS = List.of(
            "requirements", "required", "must have", "must-have", "qualifications", "what you'll need",
            "what we're looking for", "who you are", "skills required", "essential");
    private static final List<String> PREFERRED_HEADINGS = List.of(
            "preferred", "nice to have", "nice-to-have", "bonus", "plus", "good to have",
            "desirable", "additional");
    private static final List<String> RESPONSIBILITY_HEADINGS = List.of(
            "responsibilities", "what you'll do", "what you will do", "duties", "the role", "key responsibilities",
            "day to day", "your role");
    private static final List<String> OTHER_HEADINGS = List.of(
            "benefits", "perks", "about us", "about the company", "compensation", "salary", "how to apply",
            "equal opportunity", "interview process");

    private JobDescriptionParser() {
    }

    public static ParsedJobDescription parse(String description) {
        String text = description == null ? "" : description.replace("\r\n", "\n").replace('\r', '\n');
        Map<Section, StringBuilder> sections = splitSections(text);

        String requiredBlock = sections.getOrDefault(Section.REQUIRED, new StringBuilder(text)).toString();
        String preferredBlock = sections.getOrDefault(Section.PREFERRED, new StringBuilder()).toString();
        String responsibilityBlock = sections.getOrDefault(Section.RESPONSIBILITY, new StringBuilder()).toString();

        // "Requirements" and "Key responsibilities" both describe capability the role needs, so both
        // contribute to the required set. "Preferred" is separate by definition and never overlaps it.
        List<String> requiredBlockSkills = SkillDictionary.findSkills(requiredBlock);
        List<String> responsibilitySkills = SkillDictionary.findSkills(responsibilityBlock);
        List<String> required = new ArrayList<>(requiredBlockSkills);
        for (String skill : responsibilitySkills) {
            if (!required.contains(skill)) {
                required.add(skill);
            }
        }
        List<String> requiredScope = List.copyOf(required);
        List<String> preferred = SkillDictionary.findSkills(preferredBlock).stream()
                .filter(skill -> !requiredScope.contains(skill))
                .toList();
        if (required.isEmpty() && preferred.isEmpty()) {
            // No section headings at all: fall back to the whole description.
            required = new ArrayList<>(SkillDictionary.findSkills(text));
            preferred = List.of();
        }
        Set<String> technologies = new LinkedHashSet<>(required);
        technologies.addAll(preferred);

        return new ParsedJobDescription(
                extractTitle(text),
                extractCompany(text),
                required,
                preferred,
                new ArrayList<>(technologies),
                extractExperience(text),
                extractEducation(text),
                extractResponsibilities(responsibilityBlock, text));
    }

    private static Map<Section, StringBuilder> splitSections(String text) {
        Map<Section, StringBuilder> sections = new EnumMap<>(Section.class);
        Section cursor = Section.REQUIRED;
        sections.put(cursor, new StringBuilder());
        for (String rawLine : text.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            Section heading = headingOf(line);
            if (heading != null) {
                cursor = heading;
                continue;
            }
            StringBuilder buffer = sections.get(cursor);
            if (buffer == null) {
                buffer = new StringBuilder();
                sections.put(cursor, buffer);
            }
            buffer.append(line).append('\n');
        }
        return sections;
    }

    private static Section headingOf(String line) {
        if (line.length() > 80) {
            return null;
        }
        String normalised = line.toLowerCase(Locale.ROOT).replaceAll("[^a-z '\\-]", " ").strip();
        if (normalised.isEmpty()) {
            return null;
        }
        if (matchesAny(normalised, PREFERRED_HEADINGS)) {
            return Section.PREFERRED;
        }
        if (matchesAny(normalised, RESPONSIBILITY_HEADINGS)) {
            return Section.RESPONSIBILITY;
        }
        if (matchesAny(normalised, REQUIRED_HEADINGS)) {
            return Section.REQUIRED;
        }
        if (matchesAny(normalised, OTHER_HEADINGS)) {
            return Section.OTHER;
        }
        return null;
    }

    private static boolean matchesAny(String line, List<String> headings) {
        for (String heading : headings) {
            if (line.equals(heading) || line.startsWith(heading + " ") || line.contains(heading)) {
                return true;
            }
        }
        return false;
    }

    private static String extractTitle(String text) {
        Matcher labelled = TITLE_LABEL.matcher(text);
        if (labelled.find()) {
            return tidy(labelled.group(1));
        }
        Matcher role = TITLE_ROLE.matcher(text);
        if (role.find()) {
            return tidy(role.group(1));
        }
        for (String line : text.split("\n")) {
            String candidate = line.strip();
            if (!candidate.isEmpty() && candidate.length() <= 80) {
                return tidy(candidate);
            }
        }
        return null;
    }

    private static String extractCompany(String text) {
        Matcher labelled = COMPANY_LABEL.matcher(text);
        if (labelled.find()) {
            return tidy(labelled.group(1));
        }
        Matcher at = COMPANY_AT.matcher(text);
        if (at.find()) {
            return tidy(at.group(1));
        }
        return null;
    }

    private static String extractExperience(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = YEARS.matcher(text);
        while (matcher.find() && found.size() < 3) {
            String value = matcher.group(2) == null
                    ? matcher.group(1) + "+ years"
                    : matcher.group(1) + "-" + matcher.group(2) + " years";
            if (!found.contains(value)) {
                found.add(value);
            }
        }
        return found.isEmpty() ? null : String.join(", ", found);
    }

    private static String extractEducation(String text) {
        Matcher matcher = EDUCATION.matcher(text);
        if (matcher.find()) {
            return tidy(matcher.group(1));
        }
        return null;
    }

    private static List<String> extractResponsibilities(String responsibilityBlock, String text) {
        String source = responsibilityBlock.isBlank() ? text : responsibilityBlock;
        List<String> items = new ArrayList<>();
        for (String rawLine : source.split("\n")) {
            String line = rawLine.strip().replaceAll("^[-*•·▪◦]\\s*", "").replaceAll("^\\d+[.)]\\s*", "");
            if (line.length() >= 20 && line.length() <= 320) {
                items.add(line);
            }
            if (items.size() >= 12) {
                break;
            }
        }
        return items;
    }

    private static String tidy(String value) {
        String cleaned = value.replaceAll("\\s+", " ").replaceAll("[,;.\\-\\s]+$", "").strip();
        return cleaned.isEmpty() ? null : (cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned);
    }

    private enum Section {
        REQUIRED,
        PREFERRED,
        RESPONSIBILITY,
        OTHER
    }

    public record ParsedJobDescription(
            String jobTitle,
            String company,
            List<String> requiredSkills,
            List<String> preferredSkills,
            List<String> technologies,
            String experience,
            String education,
            List<String> responsibilities) {
    }
}
