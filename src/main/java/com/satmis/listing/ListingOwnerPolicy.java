package com.satmis.listing;

import com.satmis.common.ApiException;
import com.satmis.common.ErrorCode;
import com.satmis.user.User;
import com.satmis.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

// Checked before listing and before uploading images, so nobody uploads photos for a listing they cannot publish
@Component
@RequiredArgsConstructor
public class ListingOwnerPolicy {

    private final UserRepository userRepository;

    // Buyers reach sellers by phone, so a listing without a contact number is useless
    public User requireAllowedToList(Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"));
        if (!owner.isEmailVerified()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.EMAIL_NOT_VERIFIED,
                    "İlan vermek için e-posta adresinizi doğrulayın");
        }
        if (owner.getPhone() == null || owner.getPhone().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.PHONE_REQUIRED,
                    "İlan vermek için profilinize telefon numarası ekleyin");
        }
        return owner;
    }
}
