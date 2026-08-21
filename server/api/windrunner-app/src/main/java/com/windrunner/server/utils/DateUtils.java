package com.windrunner.server.utils;

import lombok.experimental.UtilityClass;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@UtilityClass
public class DateUtils {

    public OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
