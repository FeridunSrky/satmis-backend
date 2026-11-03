package com.satmis.comment;

import com.satmis.common.ApiException;
import com.satmis.common.ErrorCode;
import com.satmis.user.User;
import com.satmis.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

// Checked before commenting and before uploading evidence, so unverified accounts cannot fill the queue or storage
@Component
@RequiredArgsConstructor
class CommentAuthorPolicy {

    private final UserRepository userRepository;

    User requireAllowedToComment(Long userId) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"));
        if (!author.isEmailVerified()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, ErrorCode.EMAIL_NOT_VERIFIED,
                    "Yorum yapmak için e-posta adresinizi doğrulayın");
        }
        return author;
    }
}
