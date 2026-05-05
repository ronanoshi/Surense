package com.surense.infra.security;

import com.surense.infra.persistence.auth.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private final AuthProperties props;
    private final SecretKey signingKey;

    public JwtTokenService(AuthProperties props) {
        this.props = props;
        this.signingKey = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(long userId, String username, UserRole role) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.accessTokenTtl());
        return Jwts.builder()
                .issuer(props.issuer())
                .subject(Long.toString(userId))
                .claim("username", username)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidate(String jwt) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
    }

    public long parseUserId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }
}
