package com.satmis.auth;

import com.satmis.auth.dto.AuthResponse;
import com.satmis.auth.dto.LoginRequest;
import com.satmis.auth.dto.RegisterRequest;
import com.satmis.common.ApiException;
import com.satmis.common.EmailNormalizer;
import com.satmis.enums.Role;
import com.satmis.security.CustomUserDetails;
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

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu e-posta adresi zaten kayıtlı");
        }
        User user = userMapper.toEntity(request);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        return buildResponse(userRepository.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(EmailNormalizer.normalize(request.email()), request.password()));
        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        return buildResponse(principal.getUser());
    }

    // The mass revocation on token reuse must be committed even though the request fails
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Geçersiz refresh token"));
        User user = refreshToken.getUser();

        if (refreshToken.isRevoked()) {
            // A rotated token being replayed means it may have been stolen
            refreshTokenRepository.revokeAllByUserId(user.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Oturum geçersiz, tekrar giriş yapın");
        }
        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token süresi dolmuş");
        }
        if (!user.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Hesap devre dışı");
        }

        refreshToken.setRevoked(true);
        return buildResponse(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(RefreshTokenService.hash(rawToken))
                .ifPresent(refreshToken -> refreshToken.setRevoked(true));
    }

    private AuthResponse buildResponse(User user) {
        String accessToken = jwtService.generateAccessToken(new CustomUserDetails(user));
        String refreshToken = refreshTokenService.create(user);
        return new AuthResponse(accessToken, refreshToken, TOKEN_TYPE,
                jwtService.getAccessExpirationMs() / MILLIS_PER_SECOND, userMapper.toResponse(user));
    }
}
