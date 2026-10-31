package com.satmis.user;

import com.satmis.auth.RefreshTokenRepository;
import com.satmis.common.ApiException;
import com.satmis.enums.Role;
import com.satmis.listing.ListingQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    private static final long ADMIN_ID = 1L;
    private static final long USER_ID = 2L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private ListingQueryService listingQueryService;

    @Mock
    private UserMapper userMapper;

    @Test
    void promoteConfiguredAdmins_mixedCaseAndBlankEntries_promotesNormalizedEmails() {
        AdminUserService service = serviceWith(List.of(" Boss@Satmis.com ", "", "  ", "boss@satmis.com"));
        when(userRepository.updateRoleByEmailIn(Set.of("boss@satmis.com"), Role.ADMIN)).thenReturn(1);

        service.promoteConfiguredAdmins();

        verify(userRepository).updateRoleByEmailIn(Set.of("boss@satmis.com"), Role.ADMIN);
    }

    @Test
    void promoteConfiguredAdmins_nothingConfigured_touchesNoUser() {
        serviceWith(List.of()).promoteConfiguredAdmins();

        verifyNoInteractions(userRepository);
    }

    @Test
    void block_regularUser_disablesAndRevokesRefreshTokens() {
        User user = user(USER_ID, Role.USER);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        serviceWith(List.of()).block(ADMIN_ID, USER_ID);

        assertThat(user.isEnabled()).isFalse();
        verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
    }

    @Test
    void block_selfOrOtherAdmin_throws422AndKeepsUserEnabled() {
        User self = user(ADMIN_ID, Role.USER);
        User otherAdmin = user(USER_ID, Role.ADMIN);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(self));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(otherAdmin));
        AdminUserService service = serviceWith(List.of());

        assertThatThrownBy(() -> service.block(ADMIN_ID, ADMIN_ID))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
        assertThatThrownBy(() -> service.block(ADMIN_ID, USER_ID)).isInstanceOf(ApiException.class);
        assertThat(self.isEnabled()).isTrue();
        assertThat(otherAdmin.isEnabled()).isTrue();
        verify(refreshTokenRepository, never()).revokeAllByUserId(any());
    }

    private AdminUserService serviceWith(List<String> adminEmails) {
        return new AdminUserService(userRepository, refreshTokenRepository, listingQueryService, userMapper,
                new AdminProperties(adminEmails));
    }

    private static User user(long id, Role role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(role);
        return user;
    }
}
