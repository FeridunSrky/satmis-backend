package com.satmis.user;

import com.satmis.auth.RefreshTokenRepository;
import com.satmis.common.ApiException;
import com.satmis.common.EmailNormalizer;
import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.enums.Role;
import com.satmis.listing.ListingQueryService;
import com.satmis.user.dto.AdminUserResponse;
import com.satmis.user.dto.AdminUserSearchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
public class AdminUserService {

    // Newest first; id breaks ties so paging stays stable
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ListingQueryService listingQueryService;
    private final UserMapper userMapper;
    private final AdminProperties adminProperties;

    // Only counts are logged: the configured addresses are personal data
    @Transactional
    public void promoteConfiguredAdmins() {
        Set<String> emails = adminProperties.emails().stream()
                .map(EmailNormalizer::normalize)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toSet());
        if (emails.isEmpty()) {
            return;
        }
        int promoted = userRepository.updateRoleByEmailIn(emails, Role.ADMIN);
        long missing = emails.size() - userRepository.countByEmailIn(emails);
        log.info("Admin bootstrap: {} user(s) promoted, {} configured e-mail(s) without an account", promoted, missing);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(AdminUserSearchRequest request, PagingRequest pagingRequest) {
        Pageable pageable = pagingRequest.toPageable();
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
        String emailPart = EmailNormalizer.normalize(request.q());
        Page<User> users = emailPart == null || emailPart.isEmpty()
                ? userRepository.findAll(sorted)
                : userRepository.findByEmailContaining(emailPart, sorted);

        List<Long> userIds = users.getContent().stream().map(User::getId).toList();
        Map<Long, Long> listingCounts = listingQueryService.countListingsByOwner(userIds);
        List<AdminUserResponse> content = users.getContent().stream()
                .map(user -> userMapper.toAdminResponse(user, listingCounts.getOrDefault(user.getId(), 0L)))
                .toList();
        return PageResponse.of(content, users);
    }

    // Listings stay untouched and are hidden while the owner is disabled, so unblocking restores them as they were
    @Transactional
    public void block(Long adminId, Long userId) {
        User user = findUser(userId);
        if (user.getId().equals(adminId) || user.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Yönetici hesapları engellenemez");
        }
        user.setEnabled(false);
        // Access tokens already stop working: every request reloads the user and checks enabled
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    @Transactional
    public void unblock(Long userId) {
        findUser(userId).setEnabled(true);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"));
    }
}
