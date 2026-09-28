package com.aicommandcenter.user.dto;

/**
 * What a client needs to keep a session alive: a short-lived access token for API calls, and a
 * refresh token it rotates whenever the access token expires.
 */
public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds,
                           String refreshToken, long refreshExpiresInSeconds, UserResponse user) {

    public static AuthResponse bearer(String accessToken, long expiresInSeconds,
                                      String refreshToken, long refreshExpiresInSeconds,
                                      UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, refreshToken, refreshExpiresInSeconds, user);
    }
}
