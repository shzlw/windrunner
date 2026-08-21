package com.windrunner.server.audit;

import com.windrunner.server.utils.JsonUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RequiredArgsConstructor
@Service
public class AuditLogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogWriter auditLogWriter;

    public void logAfterCommit(AuditLogEntry entry) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeInsert(entry);
                }
            });
            return;
        }

        safeInsert(entry);
    }

    public void logImmediately(AuditLogEntry entry) {
        safeInsert(entry);
    }

    public String json(Object value) {
        return value == null ? null : JsonUtils.toJson(value);
    }

    public String changes(Map<String, ?> before, Map<String, ?> after) {
        if (before == null || after == null) {
            return null;
        }

        Map<String, Object> changes = new LinkedHashMap<>();
        after.forEach((key, afterValue) -> {
            Object beforeValue = before.get(key);
            if (!java.util.Objects.equals(beforeValue, afterValue)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("from", beforeValue);
                change.put("to", afterValue);
                changes.put(key, change);
            }
        });

        return changes.isEmpty() ? null : json(changes);
    }

    private void safeInsert(AuditLogEntry entry) {
        try {
            auditLogWriter.insert(entry);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to write audit log for {} {} {}", entry.action(), entry.entityType(), entry.entityId(), e);
        }
    }
}
