package com.windrunner.server.llmproviders.openai.client;

import com.github.victools.jsonschema.generator.*;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;

public final class OpenAIJsonSchema {

    private final SchemaGenerator schemaGenerator;

    public OpenAIJsonSchema(ObjectMapper objectMapper) {
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
        addOpenAIRequiredTypes(schema);
        return schema;
    }

    private void addOpenAIRequiredTypes(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : ((ObjectNode) node).properties()) {
                addOpenAIRequiredTypes(property.getValue());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode element : ((ArrayNode) node).elements()) {
                addOpenAIRequiredTypes(element);
            }
        }
    }
}
