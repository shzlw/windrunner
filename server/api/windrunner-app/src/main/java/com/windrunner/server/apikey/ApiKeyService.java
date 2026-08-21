package com.windrunner.server.apikey;

import com.windrunner.server.apikey.api.ApiKeyResponse;
import com.windrunner.server.apikey.api.CreateApiKeyRequest;
import com.windrunner.server.apikey.api.CreatedApiKeyResponse;
import com.windrunner.server.apikey.domain.ApiKey;
import com.windrunner.server.apikey.domain.AuthenticatedApiKey;
import com.windrunner.server.apikey.persistence.ApiKeyRepository;
import com.windrunner.server.apikey.persistence.ApiKeyScopeRepository;
import com.windrunner.server.audit.*;
import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.user.UserStatuses;
import com.windrunner.server.user.domain.AppUser;
import com.windrunner.server.user.persistence.AppUserRepository;
import com.windrunner.server.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.*;

@RequiredArgsConstructor
@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "wr_live_";

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyScopeRepository apiKeyScopeRepository;
    private final AppUserRepository appUserRepository;
    private final AuditLogService auditLogService;
    private final EntityIdGenerator idGenerator;
    private final SecureRandom secureRandom = new SecureRandom();

    public List<ApiKeyResponse> listOwnedApiKeys(String ownerUserId) {
        return apiKeyRepository.findByOwnerUserId(ownerUserId).stream()
                .map(apiKey -> ApiKeyResponse.from(apiKey, apiKeyScopeRepository.findScopesByApiKeyId(apiKey.getId())))
                .toList();
    }

    @Transactional
    public CreatedApiKeyResponse createApiKey(AppUser owner, CreateApiKeyRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "API key name is required");
        }

        List<String> scopes = normalizeScopes(request.scopes());
        if (scopes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one scope is required");
        }

        String rawKey = newRawKey();
        ApiKey apiKey = new ApiKey();
        apiKey.setId(idGenerator.generate(EntityIdType.API_KEY));
        apiKey.setOwnerUserId(owner.getId());
        apiKey.setName(request.name().trim());
        apiKey.setKeyHash(ApiKeyHasher.hash(rawKey));
        apiKey.setStatus(ApiKeyStatuses.ACTIVE);
        apiKey.setCreatedAt(DateUtils.now());

        int inserted = apiKeyRepository.insertApiKey(
                apiKey.getId(),
                apiKey.getOwnerUserId(),
                apiKey.getName(),
                apiKey.getKeyHash(),
                apiKey.getStatus(),
                apiKey.getCreatedAt()
        );
        if (inserted != 1) {
            throw new IllegalStateException("Expected one api_key row to be inserted but got " + inserted);
        }

        for (String scope : scopes) {
            apiKeyScopeRepository.insertScope(apiKey.getId(), scope);
        }

        auditLogService.logAfterCommit(new AuditLogEntry(
                owner.getId(),
                AuditActions.CREATE,
                AuditEntityTypes.API_KEY,
                apiKey.getId(),
                null,
                AuditOutcomes.SUCCESS,
                "Created API key " + apiKey.getName(),
                null,
                auditLogService.json(apiKeySnapshot(apiKey, scopes)),
                null,
                null));

        return new CreatedApiKeyResponse(
                apiKey.getId(),
                apiKey.getOwnerUserId(),
                apiKey.getName(),
                apiKey.getStatus(),
                apiKey.getCreatedAt(),
                apiKey.getLastUsedAt(),
                apiKey.getRevokedAt(),
                scopes,
                rawKey
        );
    }

    @Transactional
    public void revokeOwnedApiKey(AppUser owner, String apiKeyId) {
        ApiKey existing = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));
        if (!owner.getId().equals(existing.getOwnerUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found");
        }
        if (ApiKeyStatuses.REVOKED.equalsIgnoreCase(existing.getStatus())) {
            return;
        }

        List<String> scopes = apiKeyScopeRepository.findScopesByApiKeyId(existing.getId());
        int updated = apiKeyRepository.revokeOwnedApiKey(existing.getId(), owner.getId(), DateUtils.now());
        if (updated != 1) {
            throw new IllegalStateException("Expected one api_key row to be revoked but got " + updated);
        }

        ApiKey revoked = apiKeyRepository.findById(existing.getId())
                .orElseThrow(() -> new IllegalStateException("API key not found after revoke: " + existing.getId()));
        auditLogService.logAfterCommit(new AuditLogEntry(
                owner.getId(),
                AuditActions.DELETE,
                AuditEntityTypes.API_KEY,
                revoked.getId(),
                null,
                AuditOutcomes.SUCCESS,
                "Revoked API key " + revoked.getName(),
                auditLogService.json(apiKeySnapshot(existing, scopes)),
                auditLogService.json(apiKeySnapshot(revoked, scopes)),
                auditLogService.changes(apiKeySnapshot(existing, scopes), apiKeySnapshot(revoked, scopes)),
                null));
    }

    public AuthenticatedApiKey authenticate(String rawKey) {
        if (!StringUtils.hasText(rawKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key is required");
        }

        ApiKey apiKey = apiKeyRepository.findActiveByKeyHash(ApiKeyHasher.hash(rawKey.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key"));
        AppUser owner = appUserRepository.findById(apiKey.getOwnerUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key owner"));
        if (!UserStatuses.ACTIVE.equalsIgnoreCase(owner.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "API key owner is inactive");
        }

        List<String> scopes = apiKeyScopeRepository.findScopesByApiKeyId(apiKey.getId());
        apiKeyRepository.updateLastUsedAt(apiKey.getId(), DateUtils.now());
        return new AuthenticatedApiKey(apiKey, owner, scopes);
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null) {
            return List.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : scopes) {
            if (!StringUtils.hasText(scope)) {
                continue;
            }

            String trimmed = scope.trim();
            if (!ApiKeyScopes.isAllowed(trimmed)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported API key scope: " + trimmed);
            }
            normalized.add(trimmed);
        }

        List<String> ordered = new ArrayList<>();
        for (String scope : ApiKeyScopes.ORDERED_SCOPES) {
            if (normalized.contains(scope)) {
                ordered.add(scope);
            }
        }
        return ordered;
    }

    private String newRawKey() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private Map<String, Object> apiKeySnapshot(ApiKey apiKey, List<String> scopes) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", apiKey.getId());
        snapshot.put("ownerUserId", apiKey.getOwnerUserId());
        snapshot.put("name", apiKey.getName());
        snapshot.put("status", apiKey.getStatus());
        snapshot.put("createdAt", apiKey.getCreatedAt());
        snapshot.put("lastUsedAt", apiKey.getLastUsedAt());
        snapshot.put("revokedAt", apiKey.getRevokedAt());
        snapshot.put("scopes", scopes);
        return snapshot;
    }
}
