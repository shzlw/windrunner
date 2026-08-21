package com.windrunner.server.apikey.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("api_key_scope")
public class ApiKeyScope {

    @Id
    @Column("api_key_id")
    private String apiKeyId;

    private String scope;
}
