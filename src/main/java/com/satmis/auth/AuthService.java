package com.satmis.auth;

import com.satmis.auth.dto.AuthResponse;
import com.satmis.auth.dto.LoginRequest;
import com.satmis.auth.dto.RegisterRequest;
import com.satmis.common.ApiException;
import com.satmis.common.ErrorCode;
import com.satmis.common.EmailNormalizer;
import com.satmis.enums.Role;
import com.satmis.security.CustomUserDetails;
import com.satmis.security.JwtProperties;
import com.satmis.security.JwtService;
import com.satmis.user.User;
import com.satmis.user.UserMapper;
import com.satmis.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    private static final long MILLIS_PER_SECOND = 1000L;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final EmailVerificationService emailVerificationService;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthSession register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu e-posta adresi zaten kayıtlı");
        }
        User user = userMapper.toEntity(request);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        User saved = userRepository.save(user);
        emailVerificationService.issueFor(saved);
        return buildResponse(saved);
    }

    @Transactional
    public AuthSession login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(EmailNormalizer.normalize(request.email()), request.password()));
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        return buildResponse(principal.getUser());
    }

    // The mass revocation on token reuse must be committed even though the request fails
    @Transactional(noRollbackFor = ApiException.class)
    public AuthSession refresh(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Geçersiz refresh token"));
        User user = refreshToken.getUser();

        if (refreshToken.isRevoked()) {
            if (rotatedWithinGrace(refreshToken)) {
                throw rotatedElsewhere();
            }
            // A rotated token being replayed later means it may have been stolen
            refreshTokenRepository.revokeAllByUserId(user.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Oturum geçersiz, tekrar giriş yapın");
        }
        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token süresi dolmuş");
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Hesap devre dışı");
        }

        if (refreshTokenRepository.rotate(refreshToken.getId(), Instant.now()) == 0) {
            // A concurrent request with the same token won the rotation
            throw rotatedElsewhere();
        }
        return buildResponse(user);
    }

    // Another tab rotated it a moment ago; the shared cookie already holds the new token
    private static ApiException rotatedElsewhere() {
        return new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.REFRESH_TOKEN_ROTATED,
                "Oturum başka bir sekmede yenilendi, isteği tekrarlayın");
    }

    private boolean rotatedWithinGrace(RefreshToken refreshToken) {
        return refreshToken.getRotatedAt() != null
                && refreshToken.getRotatedAt().plus(jwtProperties.refreshReuseGrace()).isAfter(Instant.now());
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(rawToken))
                .ifPresent(refreshToken -> refreshToken.setRevoked(true));
    }

    private AuthSession buildResponse(User user) {
        String accessToken = jwtService.generateAccessToken(new CustomUserDetails(user));
        AuthResponse response = new AuthResponse(accessToken, TOKEN_TYPE,
                jwtService.getAccessExpirationMs() / MILLIS_PER_SECOND, userMapper.toResponse(user));
        return new AuthSession(response, refreshTokenService.create(user));
    }
}
