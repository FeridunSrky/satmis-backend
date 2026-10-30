package com.satmis.auth;

import com.satmis.security.JwtProperties;
import com.satmis.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 48;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    // Only the hash is stored so a database leak does not expose usable tokens
    @Transactional
    public String create(User user) {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setExpiryDate(Instant.now().plusMillis(jwtProperties.refreshExpirationMs()));
        refreshToken.setUser(user);
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    // Revoked tokens are kept until they expire so reuse detection can still recognise them;
    // once expired they are rejected anyway and can go.
    @Transactional
    public int deleteExpired() {
        return refreshTokenRepository.deleteAllExpiredBefore(Instant.now());
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }
}
