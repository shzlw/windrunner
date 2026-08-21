package com.windrunner.server.apikey.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("api_key")
public class ApiKey {

    @Id
    private String id;

    @Column("owner_user_id")
    private String ownerUserId;

    private String name;

    @Column("key_hash")
    private String keyHash;

    private String status;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column("revoked_at")
    private OffsetDateTime revokedAt;
}
