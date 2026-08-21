package com.windrunner.server.user.domain;

import java.time.OffsetDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("user_setting")
public class UserSetting {

    @Id
    private String id;

    private String userId;

    private String key;

    private String value;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}