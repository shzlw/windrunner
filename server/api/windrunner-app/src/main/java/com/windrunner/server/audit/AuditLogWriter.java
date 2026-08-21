package com.windrunner.server.audit;

import com.windrunner.server.audit.persistence.AuditLogRepository;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;
    private final EntityIdGenerator idGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insert(AuditLogEntry entry) {
        auditLogRepository.insert(
                idGenerator.generate(EntityIdType.AUDIT_LOG),
                DateUtils.now(),
                entry.actorUserId(),
                entry.action(),
                entry.entityType(),
                entry.entityId(),
                entry.projectId(),
                entry.outcome(),
                entry.summary(),
                entry.beforeJson(),
                entry.afterJson(),
                entry.changesJson(),
                entry.metadataJson());
    }
}
