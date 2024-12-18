package ru.afterwhy.openapimd.model;

import java.util.List;

public record SpecSchema(
        String name,
        String description,
        List<SpecSchemaProperty> properties,
        Object example,
        SpecSchema itemSpec) {
}
