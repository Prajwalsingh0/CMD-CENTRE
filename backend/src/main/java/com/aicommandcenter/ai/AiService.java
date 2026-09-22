package com.aicommandcenter.ai;

import java.util.List;

/**
 * The single boundary between this application and "an AI".
 *
 * <p>Nothing outside this package may know which provider is active. Callers ask for a
 * capability (see {@link #supportsStructuredOutput()}) and fall back to their own
 * deterministic implementation when the capability is absent, which is why the application
 * is fully functional and fully testable with no credentials at all.</p>
 */
public interface AiService {

    /** Stable identifier used in API responses and the activity log, e.g. {@code local}. */
    String providerName();

    /** {@code true} when calls leave the process. Surfaced to the UI so it can be honest. */
    boolean isRemote();

    /**
     * {@code true} only for providers that can be trusted to return well-formed JSON on demand.
     * When {@code false}, callers must use their deterministic fallback instead of prompting.
     */
    boolean supportsStructuredOutput();

    /** Dimensionality of {@link #embed(List)} vectors for this provider. */
    int embeddingDimensions();

    /** Runs a completion. Implementations must never throw provider-specific exceptions. */
    AiCompletion complete(AiRequest request);

    /** Embeds texts in order. Implementations must return exactly one vector per input. */
    List<double[]> embed(List<String> texts);
}
