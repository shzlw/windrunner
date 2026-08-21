package com.windrunner.server.user.domain;

public record SettingValue(
        SettingDataType dataType,
        Object value
) {
}