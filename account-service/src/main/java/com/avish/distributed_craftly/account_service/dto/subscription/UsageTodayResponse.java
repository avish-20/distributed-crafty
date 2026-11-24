package com.avish.distributed_craftly.account_service.dto.subscription;

public record UsageTodayResponse(
        Integer tokensUsed,
        Integer tokenLimit,
        Integer previewRunning,
        Integer previewLimit
) {
}
