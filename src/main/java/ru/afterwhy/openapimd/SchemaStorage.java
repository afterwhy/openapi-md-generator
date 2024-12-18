package ru.afterwhy.openapimd;

import io.swagger.v3.oas.models.media.Schema;
import ru.afterwhy.openapimd.model.SpecSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SchemaStorage implements SchemaGetter {
    private Map<String, Schema> schemas;
    private Map<String, SpecSchema> parsedSchemas;

    public SchemaStorage(Map<String, Schema> schemas, Map<String, SpecSchema> parsedSchemas) {
        this.schemas = schemas;
        this.parsedSchemas = parsedSchemas;
    }

    @Override
    public Schema<?> getFullSchema(Schema<?> schema) {
        if (schema.getName() != null) {
            return schemas.get(schema.getName());
        }
        return resolveSchema(schema);
    }

    public SpecSchema getSchemaSpec(String name) {
        return parsedSchemas.get(name);
    }

    private Schema<?> resolveSchema(Schema schema) {
        var ref = schema.get$ref();
        if (ref != null && ref.startsWith("#/components/schemas/")) {
            String schemaName = ref.substring("#/components/schemas/".length());
            return schemas.get(schemaName);
        }
        return schema;
    }

    public List<SpecSchema> getSchemaSpecs() {
        return new ArrayList<>(parsedSchemas.values());
    }
}
