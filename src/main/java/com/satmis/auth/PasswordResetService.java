package com.satmis.auth;

import com.satmis.common.ApiException;
import com.satmis.common.EmailNormalizer;
import com.satmis.user.User;
import com.satmis.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(PasswordResetProperties.class)
public class PasswordResetService {

    private static final String INVALID_LINK_MESSAGE = "Şifre sıfırlama linki geçersiz veya süresi dolmuş, yeni bir link isteyin";

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    // Silent for unknown or disabled accounts: the caller must not learn which e-mails are registered.
    // Only the newest link works.
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(EmailNormalizer.normalize(email))
                .filter(User::isEnabled)
                .ifPresent(this::issueFor);
    }

    // Opening the mailed link proves the address, so it also counts as e-mail verification.
    // Every session ends: whoever knew the old password must not stay logged in.
    @Transactional
    public void reset(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(SecureTokens.hash(rawToken))
                .filter(found -> found.getExpiryDate().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, INVALID_LINK_MESSAGE));
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        tokenRepository.deleteAllByUserId(user.getId());
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }

    private void issueFor(User user) {
        tokenRepository.deleteAllByUserId(user.getId());
        String rawToken = SecureTokens.generate();
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(SecureTokens.hash(rawToken));
        token.setExpiryDate(Instant.now().plus(properties.tokenTtl()));
        token.setUser(user);
        tokenRepository.save(token);
        eventPublisher.publishEvent(
                new PasswordResetRequestedEvent(user.getId(), user.getEmail(), user.getFirstName(), rawToken));
    }
}
