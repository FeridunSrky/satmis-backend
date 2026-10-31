package com.satmis.user;

import com.satmis.common.PageResponse;
import com.satmis.common.PagingRequest;
import com.satmis.security.CustomUserDetails;
import com.satmis.user.dto.AdminUserResponse;
import com.satmis.user.dto.AdminUserSearchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// SecurityConfig already limits /api/admin/** to admins; the annotation keeps that true if the path ever moves
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public PageResponse<AdminUserResponse> search(@Valid @ParameterObject AdminUserSearchRequest request,
                                                  @Valid @ParameterObject PagingRequest pagingRequest) {
        return adminUserService.search(request, pagingRequest);
    }

    @PostMapping("/{userId}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable Long userId) {
        adminUserService.block(principal.getId(), userId);
    }

    @PostMapping("/{userId}/unblock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable Long userId) {
        adminUserService.unblock(userId);
    }
}
