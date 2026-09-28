package com.aicommandcenter.exception;

import org.springframework.http.HttpStatus;

/**
 * A refresh token that cannot be used: unknown, expired, already rotated, or part of a family
 * that was revoked because a reused token was detected. The reason is never disclosed to the
 * caller - the response is the same 401 either way.
 */
public class InvalidTokenException extends ApiException {

    public InvalidTokenException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "The refresh token is invalid, expired or no longer active");
    }
}
