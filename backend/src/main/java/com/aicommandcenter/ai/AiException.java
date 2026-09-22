package com.aicommandcenter.ai;

/**
 * Raised when a provider cannot answer: missing credentials, network failure, non-2xx
 * status, unparsable body or a timeout.
 *
 * <p>The message is safe to show to a user: it never contains a credential, a request body
 * or a stack trace. Callers translate it into a controlled fallback or a 502.</p>
 */
public class AiException extends RuntimeException {

    private final String code;

    public AiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AiException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AiException unavailable(String provider, String detail) {
        return new AiException("AI_UNAVAILABLE", "AI provider '" + provider + "' is unavailable: " + detail);
    }

    public static AiException invalidResponse(String provider) {
        return new AiException("AI_INVALID_RESPONSE", "AI provider '" + provider + "' returned an unusable response");
    }
}
