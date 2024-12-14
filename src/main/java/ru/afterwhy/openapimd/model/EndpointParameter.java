package ru.afterwhy.openapimd.model;

public record EndpointParameter(Type type, String name, String description, boolean required) {
    public enum Type {
        QUERY,
        PATH,
        HEADER,
        COOKIE,
    }
}
