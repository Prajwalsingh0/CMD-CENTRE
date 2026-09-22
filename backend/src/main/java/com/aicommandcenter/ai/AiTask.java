package com.aicommandcenter.ai;

/**
 * What the caller actually wants, expressed as a capability rather than a prompt string.
 *
 * <p>This is what lets a deterministic, credential-free provider do something genuinely
 * useful instead of returning placeholder text: it can inspect the task and apply the right
 * algorithm, while a real LLM simply receives the same prompt and ignores the hint.</p>
 */
public enum AiTask {
    /** Free-form question or instruction. */
    GENERIC,
    /** Compress a long text to its most informative sentences. */
    SUMMARIZE,
    /** Pull out the salient points of a text as a bullet list. */
    KEY_POINTS,
    /** Turn a text into structured study/working notes. */
    NOTES,
    /** Generate revision/interview questions from a text. */
    QUESTIONS,
    /** Answer a question strictly from supplied context, citing what was used. */
    ANSWER,
    /** Produce a structured research report. */
    RESEARCH
}
