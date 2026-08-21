package com.windrunner.server.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EntityIdGeneratorTest {

    private final EntityIdGenerator generator = new EntityIdGenerator();

    @Test
    void generateUsesEntityPrefixAndBase62Suffix() {
        String id = generator.generate(EntityIdType.PROJECT);

        assertThat(id).startsWith("proj_");
        assertThat(id).hasSize("proj_".length() + 20);
        assertThat(id.substring("proj_".length())).matches("[a-zA-Z0-9]{20}");
    }

    @Test
    void generateProducesDifferentIds() {
        Set<String> ids = new HashSet<>();

        for (int index = 0; index < 1_000; index++) {
            ids.add(generator.generate(EntityIdType.WORK_ITEM));
        }

        assertThat(ids).hasSize(1_000);
    }
}
