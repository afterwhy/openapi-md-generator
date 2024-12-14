package ru.afterwhy.openapimd.model;

import java.util.List;

public record SpecSchema(
        String name,
        String description,
        List<SpecSchemaParameter> parameters,
        Object example,
        SpecSchema itemSpec) {
}
