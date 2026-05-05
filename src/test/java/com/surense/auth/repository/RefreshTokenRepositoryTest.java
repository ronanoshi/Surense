package com.surense.auth.repository;

import com.surense.auth.entity.RefreshToken;
import com.surense.auth.entity.User;
import com.surense.auth.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private UserRepository users;

    @Test
    void persistsAndFindsByTokenHash() {
        User user = users.save(new User("agent-1", "bcrypt-placeholder", UserRole.AGENT));
        String hash = "a".repeat(64);
        RefreshToken rt = new RefreshToken(user, hash, Instant.parse("2099-01-01T00:00:00Z"));
        refreshTokens.save(rt);

        Optional<RefreshToken> found = refreshTokens.findByTokenHash(hash);
        assertThat(found).isPresent();
        assertThat(found.get().getUser().getUsername()).isEqualTo("agent-1");
        assertThat(found.get().getExpiresAt()).isEqualTo(Instant.parse("2099-01-01T00:00:00Z"));
        assertThat(found.get().getRevokedAt()).isNull();
    }
}
