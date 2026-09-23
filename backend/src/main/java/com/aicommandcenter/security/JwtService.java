package com.aicommandcenter.security;

import com.aicommandcenter.config.JwtProperties;
import com.aicommandcenter.user.entity.User;
import com.aicommandcenter.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies HS256 access tokens.
 *
 * <p>Two properties that are enforced rather than documented:</p>
 * <ul>
 *   <li><strong>No default key exists.</strong> The secret always comes from configuration and a
 *       value shorter than 32 bytes is a startup failure. No profile inherits a published
 *       development key, so a production deployment that forgets <code>JWT_SECRET</code> cannot
 *       start rather than silently signing with a known value.</li>
 *   <li><strong>The subject is re-validated against the database on every request.</strong> That
 *       costs one indexed lookup per call and buys immediate effect for a disabled or deleted
 *       account; without it, a 120-minute token would keep working after suspension.</li>
 * </ul>
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private final UserRepository userRepository;
    private final SecretKey key;

    public JwtService(JwtProperties properties, UserRepository userRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
        String configured = properties.secret();
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set. "
                    + "Provide at least 32 bytes (for example: openssl rand -base64 48).");
        }
        byte[] secretBytes = configured.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret must be at least " + MIN_SECRET_BYTES
                    + " bytes; set JWT_SECRET in the environment.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(Long userId, String email) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public long expiresInSeconds() {
        return properties.accessTokenMinutes() * 60;
    }

    /**
     * Verifies the token and confirms the account still exists and is enabled.
     *
     * @return the principal when the token is valid and usable; empty for anything else
     */
    public Optional<UserPrincipal> parse(String token) {
        Optional<Long> subject = readSubject(token);
        if (subject.isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findById(subject.get())
                .filter(User::isEnabled)
                .map(UserPrincipal::fromEntity);
    }

    /**
     * Signature and issuer verification only — no database access. Used for flows that just need to
     * know whether a token is cryptographically sound.
     */
    public Optional<Long> readSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
