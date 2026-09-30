package com.expensemanagement.backend.Authentication.Jwt;

import com.expensemanagement.backend.UserManagement.Enums.UserType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Issues signed JWTs.
 *
 * <p>Signing only - verification is handled by Spring Security's resource-server filter using
 * the {@code JwtDecoder} bean from {@code SecurityConfig}. There is deliberately no
 * decode/parse method here: letting application code parse its own tokens is how the
 * validation step gets bypassed by accident.
 *
 * <p>It takes primitive/claim values rather than a {@code User} entity, so this class has no
 * dependency on the persistence layer and never needs a database to build a token.
 */
@Service
public class JwtService
{
    /** Claim name carrying the RBAC role. Must match the converter in SecurityConfig. */
    public static final String TYPE_CLAIM = "type";

    private final JwtEncoder jwtEncoder;
    private final Duration expiration;
    private final String issuer;

    public JwtService(JwtEncoder jwtEncoder,
                      @Value("${app.security.jwt.expiration-minutes:60}") long expirationMinutes,
                      @Value("${app.security.jwt.issuer:expense-management-backend}") String issuer)
    {
        this.jwtEncoder = jwtEncoder;
        this.expiration = Duration.ofMinutes(expirationMinutes);
        this.issuer = issuer;
    }

    /**
     * Builds and signs a token for one user.
     *
     * <p>{@code sub} holds the user id as a string (the JWT spec requires a string subject);
     * controllers read it back with {@code jwt.getSubject()}.
     */
    public String generateToken(Long userId, String userName, UserType type)
    {
        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .claim("username", userName)
                .claim(TYPE_CLAIM, type.name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Token lifetime in seconds, for the {@code expiresIn} field of the login response. */
    public long getExpiresInSeconds()
    {
        return expiration.toSeconds();
    }
}
