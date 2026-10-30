package com.satmis.auth;

import com.satmis.security.JwtProperties;
import com.satmis.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public String create(User user) {
        String rawToken = SecureTokens.generate();

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
        return SecureTokens.hash(rawToken);
    }
}
