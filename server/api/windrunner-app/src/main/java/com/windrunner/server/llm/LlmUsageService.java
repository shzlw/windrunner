package com.windrunner.server.llm;

import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.llm.api.LlmUsageSummary;
import com.windrunner.server.llm.persistence.LlmUsageRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmUsageService {

    private static final String OUTCOME_SUCCESS = "SUCCESS";
    private static final String OUTCOME_FAILURE = "FAILURE";
    private static final int MAX_ERROR_LENGTH = 500;

    private final LlmUsageRepository llmUsageRepository;
    private final LlmAvailabilityService llmAvailability;
    private final EntityIdGenerator idGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LlmUsageContext context, LlmResult<?> result, long durationMs) {
        try {
            llmUsageRepository.insert(
                    idGenerator.generate(EntityIdType.LLM_USAGE),
                    context.userId(),
                    context.projectId(),
                    context.feature().name(),
                    llmAvailability.provider(),
                    result.model(),
                    result.inputTokens(),
                    result.outputTokens(),
                    result.totalTokens(),
                    OUTCOME_SUCCESS,
                    null,
                    durationMs);
        } catch (Exception exception) {
            log.warn("Failed to record LLM usage for feature={}", context.feature(), exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(LlmUsageContext context, String errorMessage, long durationMs) {
        try {
            llmUsageRepository.insert(
                    idGenerator.generate(EntityIdType.LLM_USAGE),
                    context.userId(),
                    context.projectId(),
                    context.feature().name(),
                    llmAvailability.provider(),
                    null,
                    null,
                    null,
                    null,
                    OUTCOME_FAILURE,
                    truncate(errorMessage),
                    durationMs);
        } catch (Exception exception) {
            log.warn("Failed to record LLM usage failure for feature={}", context.feature(), exception);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    @Transactional(readOnly = true)
    public LlmUsageSummary summarize(List<String> projectIds, OffsetDateTime since) {
        if (projectIds.isEmpty()) {
            return new LlmUsageSummary(
                    new LlmUsageSummary.Totals(0, 0, 0, 0, 0.0, 0),
                    List.of(),
                    List.of(),
                    List.of());
        }
        LlmUsageRepository.TotalsRow totals = llmUsageRepository.summarizeTotals(projectIds, since)
                .orElseGet(() -> new LlmUsageRepository.TotalsRow(0, 0, 0, 0, 0.0));
        List<LlmUsageSummary.Project> byProject = llmUsageRepository.summarizeByProject(projectIds, since).stream()
                .map(row -> new LlmUsageSummary.Project(
                        row.projectId(),
                        row.inputTokens(),
                        row.outputTokens(),
                        row.requests(),
                        failures(row),
                        successRate(row),
                        Math.round(row.avgDurationMs())))
                .toList();
        List<LlmUsageSummary.Feature> byFeature = llmUsageRepository.summarizeByFeature(projectIds, since).stream()
                .map(row -> new LlmUsageSummary.Feature(
                        row.feature(),
                        row.inputTokens(),
                        row.outputTokens(),
                        row.requests(),
                        failures(row),
                        successRate(row)))
                .toList();
        List<LlmUsageSummary.Provider> byProviderModel = llmUsageRepository.summarizeByProviderModel(projectIds, since).stream()
                .map(row -> new LlmUsageSummary.Provider(
                        row.provider(),
                        row.model(),
                        row.inputTokens(),
                        row.outputTokens(),
                        row.requests(),
                        failures(row),
                        successRate(row)))
                .toList();
        return new LlmUsageSummary(
                new LlmUsageSummary.Totals(
                        totals.inputTokens(),
                        totals.outputTokens(),
                        totals.requests(),
                        failures(totals),
                        successRate(totals),
                        Math.round(totals.avgDurationMs())),
                byProject,
                byFeature,
                byProviderModel);
    }

    private static long failures(LlmUsageRepository.TotalsRow row) {
        return row.requests() - row.successes();
    }

    private static long failures(LlmUsageRepository.ProjectRow row) {
        return row.requests() - row.successes();
    }

    private static long failures(LlmUsageRepository.FeatureRow row) {
        return row.requests() - row.successes();
    }

    private static long failures(LlmUsageRepository.ProviderRow row) {
        return row.requests() - row.successes();
    }

    private static double successRate(LlmUsageRepository.TotalsRow row) {
        return row.requests() == 0 ? 0.0 : row.successes() / (double) row.requests();
    }

    private static double successRate(LlmUsageRepository.ProjectRow row) {
        return row.requests() == 0 ? 0.0 : row.successes() / (double) row.requests();
    }

    private static double successRate(LlmUsageRepository.FeatureRow row) {
        return row.requests() == 0 ? 0.0 : row.successes() / (double) row.requests();
    }

    private static double successRate(LlmUsageRepository.ProviderRow row) {
        return row.requests() == 0 ? 0.0 : row.successes() / (double) row.requests();
    }
}