package ru.afterwhy.openapimd.model;

public record SpecSchemaParameter(SpecSchema schema,
                                  String name,
                                  String type,
                                  String format,
                                  String description,
                                  Object example,
                                  boolean required) {

}
