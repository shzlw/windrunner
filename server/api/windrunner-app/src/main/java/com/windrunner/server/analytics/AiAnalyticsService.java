package com.windrunner.server.analytics;

import com.windrunner.server.analytics.api.AiAnalyticsSummary;
import com.windrunner.server.llm.LlmUsageService;
import com.windrunner.server.llm.api.LlmUsageSummary;
import com.windrunner.server.proposal.ProposalRepository;
import com.windrunner.server.work.persistence.WorkspaceChangeProposalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiAnalyticsService {

    private final LlmUsageService llmUsageService;
    private final ProposalRepository proposalRepository;
    private final WorkspaceChangeProposalRepository workspaceChangeProposalRepository;

    @Transactional(readOnly = true)
    public AiAnalyticsSummary summarize(List<String> projectIds, OffsetDateTime since) {
        LlmUsageSummary usage = llmUsageService.summarizeIncludingUnscoped(projectIds, since);
        LlmUsageSummary.Feature chat = usage.byFeature().stream()
                .filter(row -> "CHAT".equalsIgnoreCase(row.feature()))
                .findFirst()
                .orElse(new LlmUsageSummary.Feature("CHAT", 0, 0, 0, 0, 0));
        ProposalRepository.AnalyticsRow identity = proposalRepository.summarizeAnalytics(since)
                .orElseGet(ProposalRepository.AnalyticsRow::empty);
        WorkspaceChangeProposalRepository.AnalyticsRow workspace = projectIds.isEmpty()
                ? WorkspaceChangeProposalRepository.AnalyticsRow.empty()
                : workspaceChangeProposalRepository.summarizeAnalyticsForProjects(projectIds, since)
                .orElseGet(WorkspaceChangeProposalRepository.AnalyticsRow::empty);

        Map<String, MutableEntitySummary> entities = new LinkedHashMap<>();
        proposalRepository.summarizeAnalyticsByEntity(since).forEach(row -> add(entities, row));
        if (!projectIds.isEmpty())
            workspaceChangeProposalRepository.summarizeAnalyticsByEntityForProjects(projectIds, since).forEach(row -> add(entities, row));

        long questionsAsked = chat.requests();
        long questionsAnswered = Math.max(0, chat.requests() - chat.failures());
        return new AiAnalyticsSummary(
                questionsAsked,
                questionsAnswered,
                identity.proposalsCreated() + workspace.proposalsCreated(),
                identity.changesProposed() + workspace.changesProposed(),
                identity.changesAccepted() + workspace.changesAccepted(),
                identity.changesRejected() + workspace.changesRejected(),
                identity.changesNeedsUpdate() + workspace.changesNeedsUpdate(),
                identity.changesPending() + workspace.changesPending(),
                entities.values().stream()
                        .map(MutableEntitySummary::toSummary)
                        .sorted(Comparator.comparingLong(AiAnalyticsSummary.EntitySummary::changesProposed).reversed()
                                .thenComparing(AiAnalyticsSummary.EntitySummary::entityType))
                        .toList());
    }

    private void add(Map<String, MutableEntitySummary> entities, ProposalRepository.AnalyticsEntityRow row) {
        entities.computeIfAbsent(row.entityType(), MutableEntitySummary::new).add(row);
    }

    private void add(Map<String, MutableEntitySummary> entities, WorkspaceChangeProposalRepository.AnalyticsEntityRow row) {
        entities.computeIfAbsent(row.entityType(), MutableEntitySummary::new).add(row);
    }

    private static final class MutableEntitySummary {
        private final String entityType;
        private long changesProposed;
        private long changesAccepted;
        private long changesRejected;
        private long changesNeedsUpdate;
        private long changesPending;

        private MutableEntitySummary(String entityType) {
            this.entityType = entityType;
        }

        private void add(ProposalRepository.AnalyticsEntityRow row) {
            changesProposed += row.changesProposed();
            changesAccepted += row.changesAccepted();
            changesRejected += row.changesRejected();
            changesNeedsUpdate += row.changesNeedsUpdate();
            changesPending += row.changesPending();
        }

        private void add(WorkspaceChangeProposalRepository.AnalyticsEntityRow row) {
            changesProposed += row.changesProposed();
            changesAccepted += row.changesAccepted();
            changesRejected += row.changesRejected();
            changesNeedsUpdate += row.changesNeedsUpdate();
            changesPending += row.changesPending();
        }

        private AiAnalyticsSummary.EntitySummary toSummary() {
            return new AiAnalyticsSummary.EntitySummary(entityType, changesProposed, changesAccepted,
                    changesRejected, changesNeedsUpdate, changesPending);
        }
    }
}
