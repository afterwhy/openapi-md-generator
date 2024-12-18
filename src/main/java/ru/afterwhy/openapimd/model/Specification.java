package ru.afterwhy.openapimd.model;

import java.util.List;
import java.util.Objects;

public record Specification(String title,
                            String description,
                            List<SpecTag> tags,
                            List<SpecApiEndpoint> endpoints,
                            List<SpecSchema> schemas) {

    public SpecApiEndpoint getEndpoint(HttpMethod method, String path) {
        return endpoints.stream()
                .filter(e -> Objects.equals(e.method(), method) && Objects.equals(e.path(), path))
                .findFirst()
                .orElse(null);
    }
}
