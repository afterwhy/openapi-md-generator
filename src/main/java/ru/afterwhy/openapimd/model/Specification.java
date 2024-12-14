package ru.afterwhy.openapimd.model;

import java.util.List;

public record Specification(String title,
                            String description,
                            List<SpecTag> tags,
                            List<SpecApiEndpoint> endpoints,
                            List<SpecSchema> schemas) {}
