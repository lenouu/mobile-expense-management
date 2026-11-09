package com.expensemanagement.backend.Authentication.Services.ServiceImplements;

import com.expensemanagement.backend.Authentication.Entities.PasswordResetToken;
import com.expensemanagement.backend.Authentication.Repositories.PasswordResetTokenRepository;
import com.expensemanagement.backend.Authentication.Services.ServiceInterface.PasswordResetService;
import com.expensemanagement.backend.Exceptions.InvalidRequestException;
import com.expensemanagement.backend.UserManagement.Entities.User;
import com.expensemanagement.backend.UserManagement.Enums.UserAccountState;
import com.expensemanagement.backend.UserManagement.Repositories.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Password recovery (US06).
 *
 * <h2>Why the token is stored as a hash</h2>
 * A reset token is a bearer credential: whoever holds it can take over the account. Storing the
 * raw value would mean a database dump - or an administrator reading the table - hands out
 * working links. Only the SHA-256 hash is persisted, so the raw token exists solely in the email.
 *
 * <h2>Why the request step never reveals anything</h2>
 * "Forgot password" answers 202 for every identifier, whether the account exists, whether it is
 * suspended, and whether the mail could be sent. Anything else turns the endpoint into a way to
 * enumerate registered addresses.
 */
@Service
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService
{
    /** Short enough to limit the window a leaked link is useful, long enough to check email. */
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);

    /** 32 bytes = 256 bits of entropy, which is what makes guessing a token hopeless. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String resetLinkBaseUrl;
    private final String mailFrom;

    public PasswordResetServiceImpl(UserRepository userRepository,
                                    PasswordResetTokenRepository tokenRepository,
                                    PasswordEncoder passwordEncoder,
                                    ObjectProvider<JavaMailSender> mailSenderProvider,
                                    @Value("${app.auth.password-reset.link-base-url:http://localhost:8081/reset-password}")
                                    String resetLinkBaseUrl,
                                    @Value("${app.auth.password-reset.from:no-reply@finflow.local}")
                                    String mailFrom)
    {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSenderProvider = mailSenderProvider;
        this.resetLinkBaseUrl = resetLinkBaseUrl;
        this.mailFrom = mailFrom;
    }

    @Override
    @Transactional
    public void requestReset(String identifier)
    {
        String trimmed = identifier.trim();

        // Only an ACTIVE account can be reset: a suspended or deleted one cannot log in anyway,
        // and silently doing nothing keeps the response identical for every identifier.
        userRepository.findByUserNameOrEmail(trimmed, trimmed)
                .filter(user -> user.getStatus() == UserAccountState.ACTIVE)
                .ifPresent(this::issueToken);

        // Nothing is returned or thrown here on purpose - see the class comment.
    }

    @Override
    @Transactional
    public void reset(String token, String newPassword)
    {
        Instant now = Instant.now();

        PasswordResetToken stored = tokenRepository.findByTokenHash(sha256(token.trim()))
                .filter(candidate -> candidate.getUsedAt() == null)
                .filter(candidate -> candidate.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new InvalidRequestException(
                        "This password reset link is invalid or has expired. Request a new one."));

        User user = stored.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Single use: redeemed tokens are marked spent, and every sibling token dies with them.
        stored.setUsedAt(now);
        tokenRepository.save(stored);
        tokenRepository.invalidateAllForUser(user.getId(), now);

        log.info("Password reset completed for user id={}", user.getId());
    }

    // ------------------------------------------------------------------

    private void issueToken(User user)
    {
        Instant now = Instant.now();

        // At most one live link per account: an older request must stop working.
        tokenRepository.invalidateAllForUser(user.getId(), now);

        String rawToken = generateToken();

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(sha256(rawToken));
        token.setExpiresAt(now.plus(TOKEN_LIFETIME));
        tokenRepository.save(token);

        sendResetLink(user, rawToken);

        log.info("Issued a password reset token for user id={}, valid for {} minutes",
                user.getId(), TOKEN_LIFETIME.toMinutes());
    }

    private void sendResetLink(User user, String rawToken)
    {
        String separator = resetLinkBaseUrl.contains("?") ? "&" : "?";
        String link = resetLinkBaseUrl + separator + "token=" + rawToken;

        // Absent when spring.mail.host is unset (e.g. local development). Logging the link keeps
        // the flow testable without an SMTP server; production always configures mail.
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null)
        {
            log.warn("No mail sender configured (spring.mail.host is unset), so no reset email was "
                    + "sent. Password reset link for user id={}: {}", user.getId(), link);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("Reset your FinFlow password");
        message.setText("""
                Someone asked to reset the password for your FinFlow account.

                Open this link to choose a new password:
                %s

                The link works once and expires in %d minutes.
                If this was not you, ignore this email - your password has not changed.
                """.formatted(link, TOKEN_LIFETIME.toMinutes()));

        mailSender.send(message);
    }

    private static String generateToken()
    {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex. Fast is fine here: the token is already 256 random bits, not a password. */
    private static String sha256(String value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is required of every JVM but was not available", ex);
        }
    }
}
