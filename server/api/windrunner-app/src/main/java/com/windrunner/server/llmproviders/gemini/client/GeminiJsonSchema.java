package com.windrunner.server.llmproviders.gemini.client;

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

public final class GeminiJsonSchema {

    private final SchemaGenerator schemaGenerator;

    public GeminiJsonSchema(ObjectMapper objectMapper) {
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
        addGeminiRequiredTypes(schema);
        removeAdditionalProperties(schema);  // Gemini doesn't support additionalProperties
        return schema;
    }

    private void removeAdditionalProperties(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            objectNode.remove("additionalProperties");
            
            // Recursively remove from nested objects and arrays
            objectNode.properties().forEach(entry -> removeAdditionalProperties(entry.getValue()));
        } else if (node.isArray()) {
            ((ArrayNode) node).elements().forEach(this::removeAdditionalProperties);
        }
    }

    private void addGeminiRequiredTypes(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> property : ((ObjectNode) node).properties()) {
                addGeminiRequiredTypes(property.getValue());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode element : ((ArrayNode) node).elements()) {
                addGeminiRequiredTypes(element);
            }
        }
    }
}
