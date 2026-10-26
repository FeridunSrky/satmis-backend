package com.satmis.user;

import com.satmis.auth.RefreshTokenRepository;
import com.satmis.common.ApiException;
import com.satmis.user.dto.ChangePasswordRequest;
import com.satmis.user.dto.UpdateProfileRequest;
import com.satmis.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getById(Long userId) {
        return userMapper.toResponse(findUser(userId));
    }

    @Transactional
    public UserResponse update(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        userMapper.updateProfile(request, user);
        return userMapper.toResponse(user);
    }

    // Revoking refresh tokens forces other sessions to log in again with the new password
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mevcut şifre hatalı");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"));
    }
}
