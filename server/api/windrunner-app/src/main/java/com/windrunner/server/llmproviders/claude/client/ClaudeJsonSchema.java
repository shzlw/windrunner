package com.windrunner.server.llmproviders.claude.client;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

public final class ClaudeJsonSchema {

    private final SchemaGenerator schemaGenerator;

    public ClaudeJsonSchema(ObjectMapper objectMapper) {
        SchemaGeneratorConfigBuilder config = new SchemaGeneratorConfigBuilder(
                objectMapper,
                SchemaVersion.DRAFT_2020_12,
                OptionPreset.PLAIN_JSON
        );
        config.with(new JacksonModule());
        config.with(
                Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT,
                Option.FLATTENED_ENUMS,
                Option.INLINE_ALL_SCHEMAS
        );
        config.without(Option.SCHEMA_VERSION_INDICATOR);
        config.forFields().withRequiredCheck(field -> true);
        config.forMethods().withRequiredCheck(method -> true);
        this.schemaGenerator = new SchemaGenerator(config.build());
    }

    public ObjectNode generate(Class<?> responseType) {
        ObjectNode schema = schemaGenerator.generateSchema(responseType);
        addClaudeRequiredTypes(schema);
        return schema;
    }

    private void addClaudeRequiredTypes(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : ((ObjectNode) node).properties()) {
                addClaudeRequiredTypes(property.getValue());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode element : ((ArrayNode) node).elements()) {
                addClaudeRequiredTypes(element);
            }
        }
    }
}
