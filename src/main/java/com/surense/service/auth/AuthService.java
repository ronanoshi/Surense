package com.surense.service.auth;

import com.surense.infra.error.ErrorCode;
import com.surense.infra.error.exception.AuthApiException;
import com.surense.infra.error.exception.RateLimitedException;
import com.surense.infra.persistence.auth.entity.RefreshToken;
import com.surense.infra.persistence.auth.entity.User;
import com.surense.infra.persistence.auth.repository.RefreshTokenRepository;
import com.surense.infra.persistence.auth.repository.UserRepository;
import com.surense.infra.ratelimit.ClientIpResolver;
import com.surense.infra.ratelimit.LoginRateLimiter;
import com.surense.infra.ratelimit.RefreshTokenRateLimiter;
import com.surense.infra.security.AuthProperties;
import com.surense.infra.security.JwtTokenService;
import com.surense.infra.security.TokenHasher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthProperties authProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final RefreshTokenRateLimiter refreshTokenRateLimiter;

    @Transactional
    public TokenPair login(String username, String password, HttpServletRequest request) {
        String ip = ClientIpResolver.resolve(request);
        Optional<User> userOpt = userRepository.findByUsername(username);
        boolean passwordOk = userOpt.filter(u -> passwordEncoder.matches(password, u.getPasswordHash())).isPresent();

        if (!passwordOk) {
            Optional<Long> retrySec = loginRateLimiter.recordFailedLogin(username, ip);
            if (retrySec.isPresent()) {
                throw new RateLimitedException(retrySec.get());
            }
            throw new BadCredentialsException("invalid credentials");
        }

        User user = userOpt.get();
        return issueFreshTokens(user);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        Optional<Long> limited = refreshTokenRateLimiter.tryConsumeRefresh(rawRefreshToken);
        if (limited.isPresent()) {
            throw new RateLimitedException(limited.get());
        }

        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AuthApiException(ErrorCode.INVALID_TOKEN));

        if (existing.getRevokedAt() != null) {
            refreshTokenRepository.revokeAllActiveForUser(existing.getUser().getId(), Instant.now());
            throw new AuthApiException(ErrorCode.REFRESH_TOKEN_REUSED);
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthApiException(ErrorCode.INVALID_TOKEN);
        }

        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        return issueFreshTokens(existing.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AuthApiException(ErrorCode.INVALID_TOKEN));
        refreshTokenRepository.delete(token);
    }

    private TokenPair issueFreshTokens(User user) {
        String access = jwtTokenService.createAccessToken(user.getId(), user.getUsername(), user.getRole());
        String rawRefresh = newOpaqueRefreshToken();
        String refreshHash = TokenHasher.sha256Hex(rawRefresh);
        Instant refreshExp = Instant.now().plus(authProperties.refreshTokenTtl());
        RefreshToken entity = new RefreshToken(user, refreshHash, refreshExp);
        refreshTokenRepository.save(entity);
        return new TokenPair(access, rawRefresh, authProperties.accessTokenTtl().toSeconds());
    }

    private static String newOpaqueRefreshToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}
}
