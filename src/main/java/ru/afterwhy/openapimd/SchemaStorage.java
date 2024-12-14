package ru.afterwhy.openapimd;

import io.swagger.v3.oas.models.media.Schema;

@FunctionalInterface
public interface SchemaStorage {
    Schema<?> getFullSchema(Schema<?> schema);
}
