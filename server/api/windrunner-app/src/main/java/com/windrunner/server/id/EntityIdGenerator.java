package com.windrunner.server.id;

import java.security.SecureRandom;
import org.springframework.stereotype.Service;

@Service
public class EntityIdGenerator {

    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int SUFFIX_LENGTH = 20;

    private final SecureRandom random = new SecureRandom();

    public String generate(EntityIdType type) {
        StringBuilder id = new StringBuilder(type.prefix().length() + 1 + SUFFIX_LENGTH);
        id.append(type.prefix()).append('_');
        for (int index = 0; index < SUFFIX_LENGTH; index++) {
            id.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return id.toString();
    }
}
