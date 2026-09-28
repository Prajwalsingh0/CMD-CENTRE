package com.aicommandcenter.user.service;

import com.aicommandcenter.config.JwtProperties;
import com.aicommandcenter.exception.InvalidTokenException;
import com.aicommandcenter.user.entity.RefreshToken;
import com.aicommandcenter.user.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/**
 * Rotation rules in isolation: what is stored, what a replay does, and what it must never do.
 */
class RefreshTokenServiceTest {

    private static final long REFRESH_DAYS = 14;

    private RefreshTokenRepository repository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(RefreshTokenRepository.class);
        JwtProperties properties = new JwtProperties(
                "unit-test-secret-0123456789abcdef0123456789", "ai-command-center", 15, REFRESH_DAYS);
        service = new RefreshTokenService(repository, properties);
    }

    @Test
    @DisplayName("issuing stores only a hash, in a fresh family, and returns the raw token")
    void issueStoresHashNotToken() {
        String raw = service.issue(42L);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        Mockito.verify(repository).save(saved.capture());
        RefreshToken row = saved.getValue();

        assertThat(raw).isNotBlank().hasSizeGreaterThanOrEqualTo(60);
        assertThat(row.getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
        assertThat(row.getTokenHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(row.getUserId()).isEqualTo(42L);
        assertThat(row.getFamilyId()).isNotBlank();
        assertThat(row.getExpiresAt()).isAfter(Instant.now().plusSeconds(REFRESH_DAYS * 86400 - 60));
        assertThat(row.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("rotating retires the presented token and links it to its successor")
    void rotateRetiresPresentedToken() {
        String presented = "a".repeat(64);
        RefreshToken current = activeToken(7L, presented);
        Mockito.when(repository.findByTokenHashForUpdate(RefreshTokenService.hash(presented)))
                .thenReturn(Optional.of(current));

        RefreshTokenService.Rotation rotation = service.rotate(presented);

        assertThat(rotation.userId()).isEqualTo(7L);
        assertThat(rotation.token()).isNotBlank().isNotEqualTo(presented);
        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedBy()).isEqualTo(RefreshTokenService.hash(rotation.token()));

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        Mockito.verify(repository, Mockito.times(2)).save(saved.capture());
        RefreshToken successor = saved.getAllValues().get(1);
        assertThat(successor.getFamilyId()).isEqualTo(current.getFamilyId());
        assertThat(successor.getTokenHash()).isEqualTo(RefreshTokenService.hash(rotation.token()));
        assertThat(successor.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("a blank, short or unknown token is refused without touching the database")
    void malformedTokensAreRefusedCheaply() {
        assertThatThrownBy(() -> service.rotate(null)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.rotate("")).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> service.rotate("too-short")).isInstanceOf(InvalidTokenException.class);
        Mockito.verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("an unknown but well-formed token is refused")
    void unknownTokenIsRefused() {
        Mockito.when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("b".repeat(64))).isInstanceOf(InvalidTokenException.class);
        Mockito.verify(repository, Mockito.never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("an expired token is refused and its family survives")
    void expiredTokenIsRefused() {
        RefreshToken expired = activeToken(9L, "c".repeat(64));
        expired.setExpiresAt(Instant.now().minusSeconds(5));
        Mockito.when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("c".repeat(64))).isInstanceOf(InvalidTokenException.class);
        Mockito.verify(repository, Mockito.never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("a replay inside the grace window is refused but does not kill the chain")
    void replayInsideGraceKeepsFamilyAlive() {
        RefreshToken rotated = revokedToken(11L, "d".repeat(64), Instant.now().minusSeconds(5));
        Mockito.when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(rotated));

        assertThatThrownBy(() -> service.rotate("d".repeat(64))).isInstanceOf(InvalidTokenException.class);
        Mockito.verify(repository, Mockito.never()).revokeFamily(any(), any());
    }

    @Test
    @DisplayName("a replay after the grace window revokes the whole family")
    void replayAfterGraceRevokesFamily() {
        RefreshToken old = revokedToken(11L, "e".repeat(64),
                Instant.now().minusSeconds(RefreshTokenService.REUSE_GRACE_SECONDS + 30));
        Mockito.when(repository.findByTokenHashForUpdate(any())).thenReturn(Optional.of(old));
        Mockito.when(repository.revokeFamily(Mockito.eq(old.getFamilyId()), any())).thenReturn(2);

        assertThatThrownBy(() -> service.rotate("e".repeat(64))).isInstanceOf(InvalidTokenException.class);
        Mockito.verify(repository).revokeFamily(Mockito.eq(old.getFamilyId()), any());
    }

    @Test
    @DisplayName("revoking is idempotent and quiet about tokens it does not know")
    void revokeIsIdempotent() {
        RefreshToken active = activeToken(3L, "f".repeat(64));
        Mockito.when(repository.findByTokenHash(RefreshTokenService.hash("f".repeat(64))))
                .thenReturn(Optional.of(active));
        assertThat(service.revoke("f".repeat(64))).isTrue();
        assertThat(active.isRevoked()).isTrue();

        RefreshToken alreadyRevoked = revokedToken(3L, "g".repeat(64), Instant.now());
        Mockito.when(repository.findByTokenHash(RefreshTokenService.hash("g".repeat(64))))
                .thenReturn(Optional.of(alreadyRevoked));
        assertThat(service.revoke("g".repeat(64))).isFalse();

        Mockito.when(repository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThat(service.revoke("h".repeat(64))).isFalse();
        assertThat(service.revoke("  ")).isFalse();
    }

    @Test
    @DisplayName("hashing is deterministic, fixed width, and never the input")
    void hashingIsStable() {
        String value = "some-token-value";
        assertThat(RefreshTokenService.hash(value))
                .isEqualTo(RefreshTokenService.hash(value))
                .hasSize(64)
                .isNotEqualTo(value);
        assertThat(RefreshTokenService.hash(value)).isNotEqualTo(RefreshTokenService.hash(value + "x"));
    }

    @Test
    @DisplayName("the advertised refresh lifetime matches the configuration")
    void advertisedLifetimeMatchesConfiguration() {
        assertThat(service.refreshExpiresInSeconds()).isEqualTo(REFRESH_DAYS * 86400);
    }

    private static RefreshToken activeToken(Long userId, String raw) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setFamilyId("family-" + userId);
        token.setTokenHash(RefreshTokenService.hash(raw));
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }

    private static RefreshToken revokedToken(Long userId, String raw, Instant revokedAt) {
        RefreshToken token = activeToken(userId, raw);
        token.setRevokedAt(revokedAt);
        token.setReplacedBy("0".repeat(64));
        return token;
    }
}
