package com.aicommandcenter.user.service;

import com.aicommandcenter.config.JwtProperties;
import com.aicommandcenter.exception.InvalidTokenException;
import com.aicommandcenter.user.entity.RefreshToken;
import com.aicommandcenter.user.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <p>Rotation is the whole point: every refresh returns a new token and retires the one that was
 * presented, so a stolen token is useful at most once - and the moment the theft is visible, the
 * chain dies with it.</p>
 *
 * <h2>Reuse, races and the grace window</h2>
 * <p>Presenting an already-rotated token is either theft or a client retry, and the two look
 * identical from here. The rule is:</p>
 * <ul>
 *   <li>Replay it within {@link #REUSE_GRACE_SECONDS} seconds of its rotation: refuse the request
 *       and hand out nothing, but leave the chain alive. This is the browser-retry case.</li>
 *   <li>Replay it after that: refuse it <em>and</em> revoke the whole family, because the only
 *       innocent explanation has expired. Whoever holds the live successor loses their session
 *       too, which is exactly what should happen when a token has leaked.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 48 random bytes -> 64 base64url characters. */
    private static final int TOKEN_BYTES = 48;
    private static final int MIN_PRESENTED_LENGTH = 20;
    static final long REUSE_GRACE_SECONDS = 60;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** A fresh chain, used at register and login. */
    @Transactional
    public String issue(Long userId) {
        return issueInFamily(userId, UUID.randomUUID().toString());
    }

    /**
     * Rotates the presented token: the caller gets a new one and the old row is retired.
     *
     * <p>{@code noRollbackFor} is load-bearing. When a replay is detected this method revokes the
     * family and then throws - and a plain {@code @Transactional} would roll the revocation back
     * along with the exception, leaving the stolen chain alive. Declaring the exception as
     * non-rollback means the revocation is the thing that survives.</p>
     *
     * @throws InvalidTokenException unknown, malformed, expired, or a reused token
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public Rotation rotate(String presented) {
        if (presented == null || presented.isBlank() || presented.length() < MIN_PRESENTED_LENGTH) {
            throw new InvalidTokenException();
        }

        // The row lock is what makes a concurrent double-refresh safe to reason about: the two
        // callers are serialised, so the second sees the first one's work instead of racing it.
        RefreshToken current = repository.findByTokenHashForUpdate(hash(presented))
                .orElseThrow(InvalidTokenException::new);

        Instant now = Instant.now();

        if (current.isRevoked()) {
            long sinceRotation = Duration.between(current.getRevokedAt(), now).toSeconds();
            if (sinceRotation <= REUSE_GRACE_SECONDS) {
                // A retry, most likely: refuse but keep the chain usable.
                log.debug("Refresh token for user {} presented again {}s after rotation (within grace)",
                        current.getUserId(), sinceRotation);
                throw new InvalidTokenException();
            }
            int revoked = repository.revokeFamily(current.getFamilyId(), now);
            log.warn("Reused refresh token for user {} {}s after rotation; revoked {} token(s) in family {}",
                    current.getUserId(), sinceRotation, revoked, current.getFamilyId());
            throw new InvalidTokenException();
        }

        if (current.isExpired(now)) {
            throw new InvalidTokenException();
        }

        String next = newToken();
        current.setRevokedAt(now);
        current.setReplacedBy(hash(next));
        repository.save(current);

        RefreshToken successor = new RefreshToken();
        successor.setUserId(current.getUserId());
        successor.setFamilyId(current.getFamilyId());
        successor.setTokenHash(current.getReplacedBy());
        successor.setExpiresAt(now.plus(properties.refreshTokenDays(), ChronoUnit.DAYS));
        repository.save(successor);

        return new Rotation(current.getUserId(), next);
    }

    /**
     * Revokes one token - the sign-out path.
     *
     * @return true when an active token was revoked; false for unknown, blank or already-dead ones,
     *         so callers can stay idempotent without leaking whether a token exists
     */
    @Transactional
    public boolean revoke(String presented) {
        if (presented == null || presented.isBlank() || presented.length() < MIN_PRESENTED_LENGTH) {
            return false;
        }
        return repository.findByTokenHash(hash(presented))
                .filter(token -> !token.isRevoked())
                .map(token -> {
                    token.setRevokedAt(Instant.now());
                    repository.save(token);
                    return true;
                })
                .orElse(false);
    }

    /** Lifetime of a freshly issued refresh token, so a client knows when to stop renewing. */
    public long refreshExpiresInSeconds() {
        return properties.refreshTokenDays() * 24L * 60L * 60L;
    }

    /** Signs every device out; available for account-level security events. */
    @Transactional
    public int revokeAllForUser(Long userId) {
        return repository.revokeAllForUser(userId, Instant.now());
    }

    private String issueInFamily(Long userId, String familyId) {
        String raw = newToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setFamilyId(familyId);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(properties.refreshTokenDays(), ChronoUnit.DAYS));
        repository.save(token);
        return raw;
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256, not BCrypt: the input is 384 bits of randomness, so there is nothing to brute force,
     * and lookup-by-hash stays an indexed equality check instead of a scan.
     */
    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    /** The caller's new user id and token after a successful rotation. */
    public record Rotation(Long userId, String token) {
    }
}
