package ru.afterwhy.openapimd;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import ru.afterwhy.openapimd.model.*;

import java.util.*;
import java.util.function.Function;

public class SpecParser {
    private static final Map<HttpMethod, Function<PathItem, Operation>> operationGetters = Map.of(
            HttpMethod.GET, PathItem::getGet,
            HttpMethod.POST, PathItem::getPost,
            HttpMethod.PUT, PathItem::getPut,
            HttpMethod.DELETE, PathItem::getDelete,
            HttpMethod.OPTIONS, PathItem::getOptions,
            HttpMethod.PATCH, PathItem::getPatch,
            HttpMethod.TRACE, PathItem::getTrace,
            HttpMethod.HEAD, PathItem::getHead
    );

    public Specification parse(String specFile, Locale locale) {
        OpenAPI openAPI = new OpenAPIV3Parser().read(specFile);

        if (openAPI == null) {
            throw new RuntimeException("Unable to parse spec file: " + specFile);
        }

        var allSchemas = getAllSchemas(openAPI);

        var info = openAPI.getInfo();
        var tags = getTags(openAPI);
        var schemas = new SchemasParser().getSchemas(openAPI, locale);
        return new Specification(info.getTitle(), info.getDescription(), tags, getEndpoints(openAPI, tags, schemas), schemas);
    }

    private static Map<String, List<SpecOperation>> groupOperationsByTag(Paths paths) {
        Map<String, List<SpecOperation>> operationsByTag = new HashMap<>();

        for (Map.Entry<String, PathItem> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();

            operationGetters.forEach((method, operationGetter) ->  {
                addOperationToTags(operationsByTag, pathItem, path, method, operationGetter);
            });
        }

        return operationsByTag;
    }

    private static void addOperationToTags(Map<String, List<SpecOperation>> operationsByTag, PathItem pathItem, String path, HttpMethod method, Function<PathItem, Operation> operationSupplier) {
        var operation = operationSupplier.apply(pathItem);
        if (operation != null) {
            addOperationToTags(operationsByTag, method, operation.getTags(), path);
        }
    }

    private static void addOperationToTags(Map<String, List<SpecOperation>> operationsByTag, HttpMethod method, List<String> tags, String path) {
        if (tags != null) {
            for (String tag : tags) {
                operationsByTag.computeIfAbsent(tag, _ -> new ArrayList<>()).add(new SpecOperation(method, path));
            }
        }
    }

    private static List<SpecTag> getTags(OpenAPI openAPI) {
        var operationsByTag = groupOperationsByTag(openAPI.getPaths());
        return openAPI.getTags().stream()
                .map(t -> new SpecTag(t.getName(), t.getDescription(), operationsByTag.get(t.getName())))
                .toList();
    }

    private static List<SpecApiEndpoint> getEndpoints(OpenAPI openAPI, List<SpecTag> tags, List<SpecSchema> schemas) {
        return openAPI.getPaths().entrySet().stream()
                .flatMap(kv -> {
                    var path = kv.getKey();
                    var pathItem = kv.getValue();

                    return operationGetters.entrySet().stream().map(o -> {
                        return operationToEndpoint(schemas, o.getKey(), o.getValue(), path, pathItem);
                    });
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static SpecApiEndpoint operationToEndpoint(List<SpecSchema> schemas, HttpMethod method, Function<PathItem, Operation> operationGetter, String path, PathItem pathItem) {
        var operation = operationGetter.apply(pathItem);
        if (operation == null) {
            return null;
        }

        for (String mimeType : operation.getRequestBody().getContent().keySet()) {
            1111
        }
        var parameters = getParameters(operation);
        return new SpecApiEndpoint(
                operation.getOperationId(),
                method,
                path,
                operation.getSummary(),
                operation.getDescription(),
                operation.getTags(),
                parameters,
                null, //todo
                List.of() //todo
        );
    }

    private static List<EndpointParameter> getParameters(Operation operation) {
        if (operation.getParameters() == null) {
            return List.of();
        }
        //todo: it won't work for complex models
        return operation.getParameters().stream().map(p -> {
            var type = EndpointParameter.Type.valueOf(p.getIn().toUpperCase());
            var required = p.getRequired() != null && p.getRequired(); // todo: it won't work or complex models?
            return new EndpointParameter(type, p.getName(), p.getDescription(), required);
        }).toList();
    }

    private static Map<String, Schema> getAllSchemas(OpenAPI openAPI) {
        return openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null
                ? openAPI.getComponents().getSchemas()
                : Map.of();
    }

}
