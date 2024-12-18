package ru.afterwhy.openapimd.model;

import java.util.List;

public record SpecApiEndpoint(String operationId,
                              HttpMethod method,
                              String path,
                              String summary,
                              String description,
                              List<String> tag,
                              List<EndpointParameter> parameters,
                              ExchangeContent request,
                              ResponseDescriptor responses) {
}
