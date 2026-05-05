package com.surense.auth.repository;

import com.surense.auth.entity.User;
import com.surense.auth.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Test
    void persistsAndFindsByUsername() {
        users.save(new User("seed-admin", "bcrypt-placeholder", UserRole.ADMIN));

        assertThat(users.findByUsername("seed-admin"))
                .isPresent()
                .get()
                .satisfies(u -> {
                    assertThat(u.getRole()).isEqualTo(UserRole.ADMIN);
                    assertThat(u.getPasswordHash()).isEqualTo("bcrypt-placeholder");
                    assertThat(u.getId()).isNotNull();
                    assertThat(u.getCreatedAt()).isNotNull();
                    assertThat(u.getUpdatedAt()).isNotNull();
                });
    }

    @Test
    void existsByUsername() {
        users.save(new User("dup-check", "x", UserRole.CUSTOMER));
        assertThat(users.existsByUsername("dup-check")).isTrue();
        assertThat(users.existsByUsername("missing")).isFalse();
    }
}
