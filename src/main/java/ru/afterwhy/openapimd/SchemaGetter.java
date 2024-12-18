package ru.afterwhy.openapimd;

import io.swagger.v3.oas.models.media.Schema;

@FunctionalInterface
public interface SchemaGetter {
    Schema<?> getFullSchema(Schema<?> schema);
}
