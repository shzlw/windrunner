package com.windrunner.server.utils;

import lombok.experimental.UtilityClass;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
public class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize object to JSON", e);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize JSON", e);
        }
    }
}
