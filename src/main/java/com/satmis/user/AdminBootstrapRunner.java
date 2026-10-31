package com.satmis.user;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

// Role is never taken from a request, so the first admin has to come from configuration
@Component
@RequiredArgsConstructor
class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminUserService adminUserService;

    @Override
    public void run(ApplicationArguments args) {
        adminUserService.promoteConfiguredAdmins();
    }
}
