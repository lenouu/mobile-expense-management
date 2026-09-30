package com.expensemanagement.backend.Authentication.Config;

import com.expensemanagement.backend.Swagger.SwaggerConfig;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The whole security setup: authentication, authorisation (RBAC), CORS and statelessness.
 *
 * <p>Defining a {@link SecurityFilterChain} bean switches off Spring Boot's default security
 * auto-configuration, which is what was serving the auto-generated login page and printing a
 * random password at startup. {@code formLogin} and {@code httpBasic} are also disabled
 * explicitly below, so there is no HTML login page in this application at all - clients
 * authenticate by POSTing to {@code /api/auth/login} and then present a Bearer token.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig
{
    /** Endpoints that must work without a token - you cannot log in without them. */
    private static final String[] PUBLIC_POST_ENDPOINTS = {
            "/api/auth/login",
            "/api/auth/register"
    };

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/actuator/health",
            "/actuator/health/**"
    };

    // ------------------------------------------------------------------
    // Password hashing
    // ------------------------------------------------------------------

    /**
     * BCrypt, with the default strength of 10.
     *
     * <p>BCrypt is deliberately slow and salts each hash internally, so two users with the
     * same password get different hashes. Never substitute MD5/SHA-256 here - they are fast,
     * which is exactly the wrong property for password storage.
     */
    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    // ------------------------------------------------------------------
    // JWT signing and verification
    // ------------------------------------------------------------------

    /**
     * Secret used to sign and verify tokens.
     *
     * <p>HS256 is symmetric - the same key both signs and verifies - which is why it lives in
     * this one application. If you ever split authentication into its own service, switch to
     * RS256 with a public/private key pair so the resource servers only hold the public key.
     */
    @Bean
    public SecretKey jwtSecretKey(@Value("${app.security.jwt.secret}") String secret)
    {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);

        // Fail loudly at startup rather than at the first login: HS256 requires a key of at
        // least 256 bits (32 bytes), and Nimbus rejects anything shorter.
        if (keyBytes.length < 32)
        {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least 32 bytes for HS256, but was "
                            + keyBytes.length + " bytes");
        }

        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey)
    {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    /**
     * Verifies incoming Bearer tokens.
     *
     * <p>Its presence is what makes Spring Security register the resource-server filter, so
     * every request carrying {@code Authorization: Bearer ...} is authenticated before it
     * reaches a controller. Signature, issuer-independent expiry and tampering are all checked
     * here - application code never parses a token itself.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey)
    {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Maps the {@code type} claim onto Spring Security authorities - this is the RBAC bridge.
     *
     * <p>A token carrying {@code "type": "ADMIN"} yields the authority {@code ROLE_ADMIN},
     * which is what {@code hasRole("ADMIN")} and {@code @PreAuthorize("hasRole('ADMIN')")}
     * look for. Without this bean every authenticated user would be anonymous as far as
     * authorisation is concerned.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter()
    {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("type");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    // ------------------------------------------------------------------
    // CORS
    // ------------------------------------------------------------------

    /**
     * Cross-origin policy.
     *
     * <p>Worth knowing: CORS is enforced by <em>browsers</em>, not by servers. A native
     * Android/iOS client ignores it completely and will work whether or not this is
     * configured. It matters only for a web front end, Swagger UI or a browser-based tool.
     *
     * <p>{@code setAllowedOriginPatterns} is used rather than {@code setAllowedOrigins}
     * because a literal {@code "*"} combined with {@code allowCredentials(true)} is rejected
     * by Spring - patterns allow wildcards without losing credentialed requests.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:3000}") List<String> allowedOrigins)
    {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // Lets the browser read the token out of the response rather than only sending it.
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // ------------------------------------------------------------------
    // Filter chain: RBAC rules and statelessness
    // ------------------------------------------------------------------

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception
    {
        http
                // Picks up the CorsConfigurationSource bean above.
                .cors(Customizer.withDefaults())

                // CSRF protection defends cookie-based sessions. This API authenticates with a
                // Bearer header, which a cross-site form cannot set, so CSRF adds nothing and
                // would reject every POST.
                .csrf(AbstractHttpConfigurer::disable)

                // No server-side sessions: the token carries the identity, so any instance can
                // serve any request. This is what makes the API horizontally scalable.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // These three are what remove the default login page, the browser Basic-auth
                // prompt and the /logout endpoint.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(authorize -> authorize
                        // Preflight carries no Authorization header, so it must never 401.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()

                        // Swagger UI and the OpenAPI document. The path list is owned by the
                        // Swagger package so there is exactly one place to change it - and one
                        // place to empty when you lock the API down for production.
                        .requestMatchers(HttpMethod.GET, SwaggerConfig.PUBLIC_DOC_PATHS).permitAll()

                        // Spring Security 6+ filters the container's ERROR dispatch too, so
                        // without this every error turns into a 401: the forward to /error is
                        // itself evaluated against anyRequest().authenticated(), and the
                        // entry point overwrites the real status. Permitting /error lets the
                        // genuine 400 / 404 / 500 reach the client.
                        .requestMatchers("/error").permitAll()
                        // URL-level RBAC. Anything under /api/admin requires an ADMIN token.
                        // Finer-grained rules live on the service methods via @PreAuthorize.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Everything else simply requires a valid token.
                        .anyRequest().authenticated())

                // Turns the JwtDecoder bean into request authentication, using the converter
                // above so the "type" claim becomes ROLE_USER / ROLE_ADMIN.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        // BearerTokenAuthenticationFilter handles a present-but-invalid token
                        // itself and calls THIS entry point directly, bypassing the
                        // exceptionHandling block below. Without it a malformed or expired
                        // token returns an empty 401 body, while a missing token returns JSON -
                        // an inconsistency clients trip over.
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "unauthorized", "Invalid or expired token")))

                // Without these, a missing or insufficient token would produce an empty body
                // or an HTML error page. Both are returned as JSON for an API client.
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "unauthorized", "Authentication required"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "forbidden", "Insufficient privileges")));

        return http.build();
    }

    private static void writeJsonError(HttpServletResponse response, int status, String error, String message)
            throws IOException
    {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
