package ru.afterwhy.openapimd.model;

public record SpecSchemaProperty(SpecSchema schema,
                                 String name,
                                 String type,
                                 String format,
                                 String description,
                                 Object example,
                                 boolean required) {

}
