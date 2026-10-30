package com.satmis.auth;

import com.satmis.common.ApiException;
import com.satmis.user.User;
import com.satmis.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class EmailVerificationService {

    private static final String INVALID_LINK_MESSAGE = "Doğrulama linki geçersiz veya süresi dolmuş, yeni bir link isteyin";

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailVerificationProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    // Only the newest link works: earlier tokens of the user are dropped
    @Transactional
    public void issueFor(User user) {
        tokenRepository.deleteAllByUserId(user.getId());
        String rawToken = SecureTokens.generate();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setTokenHash(SecureTokens.hash(rawToken));
        token.setExpiryDate(Instant.now().plus(properties.tokenTtl()));
        token.setUser(user);
        tokenRepository.save(token);
        eventPublisher.publishEvent(
                new EmailVerificationRequestedEvent(user.getId(), user.getEmail(), user.getFirstName(), rawToken));
    }

    @Transactional
    public void resend(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"));
        if (user.isEmailVerified()) {
            throw new ApiException(HttpStatus.CONFLICT, "E-posta adresiniz zaten doğrulanmış");
        }
        issueFor(user);
    }

    // Invalid and expired links get the same answer; the client can only offer a new link either way
    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(SecureTokens.hash(rawToken))
                .filter(found -> found.getExpiryDate().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, INVALID_LINK_MESSAGE));
        User user = token.getUser();
        user.setEmailVerified(true);
        tokenRepository.deleteAllByUserId(user.getId());
    }
}
