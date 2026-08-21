package com.windrunner.server.user;

import com.windrunner.server.id.EntityIdGenerator;
import com.windrunner.server.id.EntityIdType;
import com.windrunner.server.user.domain.SettingValue;
import com.windrunner.server.user.domain.UserSetting;
import com.windrunner.server.user.persistence.UserSettingRepository;
import com.windrunner.server.utils.JsonUtils;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class UserSettingService {

    private static final int MAX_KEY_LENGTH = 64;
    private static final String SETTING_KEY_PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

    private final UserSettingRepository userSettingRepository;
    private final EntityIdGenerator idGenerator;

    public Map<String, SettingValue> getAllSettings(String userId) {
        Map<String, SettingValue> settings = new LinkedHashMap<>();
        for (UserSetting setting : userSettingRepository.findByUserId(userId)) {
            SettingValue value = parseSettingValue(setting.getValue());
            if (value != null) {
                settings.put(setting.getKey(), value);
            }
        }
        return settings;
    }

    public SettingValue updateSetting(String userId, String key, SettingValue settingValue) {
        validateKey(key);
        validateSettingValue(settingValue);

        String id = idGenerator.generate(EntityIdType.USER_SETTING);
        userSettingRepository.upsert(id, userId, key, JsonUtils.toJson(settingValue));
        return settingValue;
    }

    public void deleteSetting(String userId, String key) {
        validateKey(key);
        userSettingRepository.delete(userId, key);
    }

    private void validateKey(String key) {
        if (!StringUtils.hasText(key) || key.length() > MAX_KEY_LENGTH || !key.matches(SETTING_KEY_PATTERN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid setting key");
        }
    }

    private void validateSettingValue(SettingValue settingValue) {
        if (settingValue == null || settingValue.dataType() == null || settingValue.value() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setting value requires dataType and value");
        }

        switch (settingValue.dataType()) {
            case BOOLEAN -> {
                if (!(settingValue.value() instanceof Boolean)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setting value must be a boolean");
                }
            }
            case NUMBER -> {
                if (!(settingValue.value() instanceof Number)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setting value must be a number");
                }
            }
            case TEXT -> {
                if (!(settingValue.value() instanceof String)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setting value must be a string");
                }
            }
            case DATE -> {
                if (!(settingValue.value() instanceof String stringValue)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setting value must be a date string");
                }
                try {
                    LocalDate.parse(stringValue);
                } catch (DateTimeParseException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setting value must be a valid ISO date");
                }
            }
        }
    }

    private SettingValue parseSettingValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            SettingValue value = JsonUtils.fromJson(rawValue, SettingValue.class);
            if (value == null || value.dataType() == null || value.value() == null) {
                return null;
            }
            return value;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}