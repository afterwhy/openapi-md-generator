package ru.afterwhy.openapimd;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import ru.afterwhy.openapimd.model.SpecSchema;
import ru.afterwhy.openapimd.model.SpecSchemaParameter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class SchemasParser {

    private final Map<String, SpecSchema> alreadyCreatedSchemas = new LinkedHashMap<>();

    public SpecSchema parseSchema(SchemaStorage schemaStorage, Schema schema, Locale locale) {
        return parseSchema(schemaStorage.getFullSchema(schema), schemaStorage, locale);
    }

    public SchemaStorage getSchemas(OpenAPI openAPI, Locale locale) {
        var allSchemas = getAllSchemas(openAPI);
        var specSchemas = allSchemas.values().stream()
                .map(s -> parseSchema(getSchema(s, allSchemas), scm -> getSchema(scm, allSchemas), locale))
                .collect(Collectors.toMap(SpecSchema::name, s -> s));
        return new SchemaStorage(allSchemas, specSchemas);
    }

    private static Schema getSchema(Schema<?> scm, Map<String, Schema> allSchemas) {
        if (scm.getName() != null) {
            return allSchemas.get(scm.getName());
        }
        return resolveSchema(allSchemas, scm);
    }

    private SpecSchema parseSchema(Schema<?> schema, SchemaGetter storage, Locale locale) {
        var schemaName = schema.getName();
        if (schemaName != null) {
            if (alreadyCreatedSchemas.containsKey(schemaName)) {
                return alreadyCreatedSchemas.get(schemaName);
            }
        }

        var parameters = getParameters(schema, storage, locale);
        SpecSchema itemSpec = null;
        if (schema.getItems() != null) {
            itemSpec = parseSchema(storage.getFullSchema(schema.getItems()), storage, locale);
        }
        var specSchema = new SpecSchema(
                schemaName,
                schema.getDescription(),
                parameters,
                ExampleGenerator.getExample(schema, itemSpec, parameters, storage, locale),
                itemSpec
        );
        if (schemaName != null) {
            alreadyCreatedSchemas.put(schemaName, specSchema);
        }
        return specSchema;
    }

    private List<SpecSchemaParameter> getParameters(Schema<?> schema, SchemaGetter storage, Locale locale) {
        return getProperties(schema, storage).entrySet()
                .stream()
                .map(e -> {
                    var paramName = e.getKey();
                    Schema<?> parameterSchema = storage.getFullSchema(e.getValue());
                    var parameterSpecSchema = parseSchema(parameterSchema, storage, locale);
                    var required = schema.getRequired() != null ? schema.getRequired() : List.of();
                    return new SpecSchemaParameter(
                            parameterSpecSchema,
                            paramName,
                            parameterSchema.getType(),
                            parameterSchema.getFormat(),
                            parameterSchema.getDescription(),
                            ExampleGenerator.getExample(parameterSchema, parameterSpecSchema.itemSpec(), parameterSpecSchema.parameters(), storage, locale),
                            required.contains(paramName)
                    );
                }).toList();
    }

    private static Map<String, Schema> getProperties(Schema<?> schema, SchemaGetter storage) {
        Map<String, Schema> properties = new LinkedHashMap<>();

        if (schema instanceof ComposedSchema composedSchema) {
            //todo is it possible to support oneOf or anyOf?
            if (composedSchema.getAllOf() != null) {
                var composedProperties = composedSchema.getAllOf()
                        .stream()
                        .flatMap(s -> getProperties(storage.getFullSchema(s), storage).entrySet().stream())
                        .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())))
                        .entrySet().stream()
                        .map(e -> Map.entry(e.getKey(), e.getValue().isEmpty() ? null : e.getValue().getLast()))
                        .filter(e -> e.getValue() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                properties.putAll(composedProperties);
            }
        }

        if (schema.getProperties() != null) {
            properties.putAll(schema.getProperties());
        }

        return properties;
    }

    private static Schema<?> resolveSchema(Map<String, Schema> allSchemas, Schema schema) {
        var ref = schema.get$ref();
        if (ref != null && ref.startsWith("#/components/schemas/")) {
            String schemaName = ref.substring("#/components/schemas/".length());
            return resolveSchema(allSchemas, allSchemas.get(schemaName));
        }
        return schema;
    }

    private static Map<String, Schema> getAllSchemas(OpenAPI openAPI) {
        var allSchemas = openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null
                ? openAPI.getComponents().getSchemas().entrySet().stream()
                .map(e -> {
                    e.getValue().setName(e.getKey());
                    return Map.entry(e.getKey(), e.getValue());
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                : Map.<String, Schema>of();
        return allSchemas.entrySet()
                .stream()
                .map(e -> Map.entry(e.getKey(), resolveSchema(allSchemas, e.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
