package com.windrunner.server.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SettingDataType {
    NUMBER("number"),
    TEXT("text"),
    DATE("date"),
    BOOLEAN("boolean");

    private final String wireValue;

    SettingDataType(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    @JsonCreator
    public static SettingDataType fromWireValue(String value) {
        if (value == null) {
            return null;
        }
        for (SettingDataType dataType : values()) {
            if (dataType.wireValue.equalsIgnoreCase(value)) {
                return dataType;
            }
        }
        throw new IllegalArgumentException("Unsupported setting data type: " + value);
    }
}