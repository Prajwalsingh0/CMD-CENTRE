package com.aicommandcenter.job.dto;

/**
 * How one skill in a job description relates to what the user has actually declared.
 *
 * <ul>
 *   <li>{@code KNOWN} — the user declared it and it is verified.</li>
 *   <li>{@code UNVERIFIED} — the user declared it, but nothing backs it up yet.</li>
 *   <li>{@code MISSING} — the user has never declared it.</li>
 * </ul>
 *
 * The three states exist so the system cannot invent qualifications: a skill is only ever
 * KNOWN when the user (or an explicit verification step) put it in their profile.
 */
public record SkillMatch(
        String skill,
        String status,
        boolean required,
        String userLevel) {

    public static final String KNOWN = "KNOWN";
    public static final String UNVERIFIED = "UNVERIFIED";
    public static final String MISSING = "MISSING";

    public static SkillMatch of(String skill, boolean required, boolean hasSkill, boolean verified, String level) {
        String status = !hasSkill ? MISSING : (verified ? KNOWN : UNVERIFIED);
        return new SkillMatch(skill, status, required, hasSkill ? level : null);
    }
}
