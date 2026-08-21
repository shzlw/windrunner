package com.windrunner.server.auth.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("auth_session")
public class AuthSession {

    @Id
    private String id;

    @Column("user_id")
    private String userId;

    @Column("session_token_hash")
    private String sessionTokenHash;

    @Column("csrf_token")
    private String csrfToken;

    @Column("expires_at")
    private OffsetDateTime expiresAt;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
