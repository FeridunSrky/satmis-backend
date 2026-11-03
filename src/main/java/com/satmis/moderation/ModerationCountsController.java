package com.satmis.moderation;

import com.satmis.moderation.dto.ModerationCountsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/moderation")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ModerationCountsController {

    private final ModerationCountsService moderationCountsService;

    @GetMapping("/counts")
    public ModerationCountsResponse getCounts() {
        return moderationCountsService.getCounts();
    }
}
