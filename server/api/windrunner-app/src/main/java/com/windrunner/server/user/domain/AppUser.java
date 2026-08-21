package com.windrunner.server.user.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Data
@Table("app_user")
public class AppUser {

    @Id
    private String id;

    private String username;

    private String email;

    @Column("display_name")
    private String displayName;

    private String timezone;

    @Column("password_hash")
    private String passwordHash;

    private String status;

    @Column("global_role")
    private String globalRole;

    @Column("must_change_password")
    private Boolean mustChangePassword;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
