package ru.afterwhy.openapimd.model;

import java.util.List;
import java.util.Objects;

public record SpecApiEndpoint(String operationId,
                              HttpMethod method,
                              String path,
                              String summary,
                              String description,
                              List<String> tag,
                              List<EndpointParameter> parameters,
                              ExchangeContent request,
                              ResponseDescriptor responses) {

    public String getSummaryEvenIfNotExists() {
        // Используем путь, если summary не указано
        return Objects.requireNonNullElseGet(summary, () -> "%s.%s".formatted(method.name().toUpperCase(), path));

    }
}
