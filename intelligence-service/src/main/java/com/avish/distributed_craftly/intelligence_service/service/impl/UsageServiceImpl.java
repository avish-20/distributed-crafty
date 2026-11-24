package com.avish.distributed_craftly.intelligence_service.service.impl;

import com.avish.distributed_craftly.common_lib.dto.PlanDto;
import com.avish.distributed_craftly.common_lib.security.AuthUtil;
import com.avish.distributed_craftly.intelligence_service.client.AccountClient;
import com.avish.distributed_craftly.intelligence_service.entity.UsageLog;
import com.avish.distributed_craftly.intelligence_service.repository.UsageLogRepository;
import com.avish.distributed_craftly.intelligence_service.service.UsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RequiredArgsConstructor
@Service
public class UsageServiceImpl implements UsageService {
    private final UsageLogRepository usageLogRepository;
    private final AuthUtil authUtil;
    private final AccountClient accountClient;

    @Override
    public void recordTokenUsage(Long userId, int actualTokens) {
        // Implementation for recording token usage
        LocalDate today = LocalDate.now();
        UsageLog usageLogToday = usageLogRepository.findByUserIdAndDate(userId, today)
                .orElseGet(()-> createNewUsageLog(userId, today));
        usageLogToday.setTokensUsed(usageLogToday.getTokensUsed() + actualTokens);
        usageLogRepository.save(usageLogToday);
    }

    @Override
    public void checkDailyTokensUsage() {
        // Implementation for checking daily token usage
        Long userId = authUtil.getCurrentUserId();
        PlanDto plan = accountClient.getCurrentSubscribedPlanByUser();
        LocalDate today = LocalDate.now();
        UsageLog usageLogToday = usageLogRepository.findByUserIdAndDate(userId, today)
                .orElseGet(()-> createNewUsageLog(userId, today));
        int currentUsage = usageLogToday.getTokensUsed();
        int dailyLimit = plan.maxTokensPerDay();

        if (currentUsage >= dailyLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Daily token limit exceeded. Please upgrade your plan or wait until tomorrow.");
        }

    }

    private UsageLog createNewUsageLog(Long userId, LocalDate date){
        UsageLog newLog = UsageLog.builder()
                .userId(userId)
                .date(date)
                .tokensUsed(0)
                .build();

        return usageLogRepository.save(newLog);
    }
}
