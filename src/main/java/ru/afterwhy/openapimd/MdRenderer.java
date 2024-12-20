package ru.afterwhy.openapimd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.afterwhy.openapimd.model.EndpointParameter;
import ru.afterwhy.openapimd.model.SpecSchema;
import ru.afterwhy.openapimd.model.SpecSchemaProperty;
import ru.afterwhy.openapimd.model.Specification;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MdRenderer {

    private final ResourceBundle resourceBundle;
    private final ObjectMapper objectMapper;

    public MdRenderer(ObjectMapper objectMapper, Locale locale) {
        this.objectMapper = objectMapper;
        this.resourceBundle = ResourceBundle.getBundle("locale", locale);
    }

    public String render(Specification spec) {
        try (var os = new ByteArrayOutputStream(); var writer = new OutputStreamWriter(os)) {
            render(spec, writer);
            return os.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void render(Specification spec, OutputStreamWriter writer) throws IOException {
        // Заголовок первого уровня (title из info)
        writer.write("# " + spec.title() + "\n\n");

        // Описание API (description из info)
        if (spec.description() != null) {
            writer.write(spec.description() + "\n\n");
        }

        // Заголовок второго уровня - API
        writer.write("## API\n\n");

        // Группировка эндпойнтов по тэгам
        for (var tag : spec.tags()) {
            writer.write("- [" + tag.name() + "](#" + stringToLink(tag.name()) + ")\n");
            for (var operation : tag.operations()) {
                var summary = spec.getEndpoint(operation.httpMethod(), operation.path()).getSummaryEvenIfNotExists();
                writer.write("  - [" + summary + "](#" + stringToLink(summary) + ")\n");
            }
        }

        writer.write("\n");

        // Описание тэгов и эндпойнтов
        for (var tag : spec.tags()) {
            writer.write("### " + tag.name() + "\n\n");

            for (var operation : tag.operations()) {
                var path = operation.path();
                var endpoint = spec.getEndpoint(operation.httpMethod(), operation.path());
                var summary = endpoint.getSummaryEvenIfNotExists();

                // Заголовок 4 уровня с использованием summary
                writer.write("#### " + summary + "\n\n");

                // Метод, путь и operationId
                writer.write("`" + endpoint.method().name().toUpperCase() + " " + path + "`\n\n");
                writer.write("**Operation ID:** `" + endpoint.operationId() + "`\n\n");

                // Описание (description)
                if (endpoint.description() != null) {
                    writer.write(endpoint.description() + "\n\n");
                }

                // Параметры запроса
                if (endpoint.parameters() != null && !endpoint.parameters().isEmpty()) {
                    writer.write("##### %s\n\n".formatted(resourceBundle.getString("endpoint.request.schema-properties.header")));
                    var typeHeader = resourceBundle.getString("schema-parameters.table-header.type");
                    var nameHeader = resourceBundle.getString("schema-parameters.table-header.name");
                    var descriptionHeader = resourceBundle.getString("schema-parameters.table-header.description");
                    var requiredHeader = resourceBundle.getString("schema-parameters.table-header.required");
                    writer.write("| %s | %s | %s | %s |\n".formatted(typeHeader, nameHeader, descriptionHeader, requiredHeader));
                    writer.write("|----|----|----|----|\n");
                    for (var parameter : endpoint.parameters()) {
                        var type = getEndpointParameterTypeLocalized(parameter);
                        var name = parameter.name();
                        var description = parameter.description() != null ? parameter.description() : "";
                        var required = parameter.required() ? "+" : "-";
                        writer.write("| " + type + " | " + name + " | " + description + " | " + required + " |\n");
                    }
                    writer.write("\n");
                }

                if (endpoint.request() != null) {
                    writer.write("### %s\n".formatted(resourceBundle.getString("endpoint.request")));
                    for (var requestVariant : endpoint.request().content().entrySet()) {
                        var mimeType = requestVariant.getKey();
                        writer.write("##### %s\n".formatted(mimeType));
                        writer.write(generateMarkdownTableForProperties(requestVariant.getValue().properties()));
                        writer.write("##### %s\n".formatted(resourceBundle.getString("endpoint.request.example")));
                        writer.write(generateExample(mimeType, requestVariant));
                    }
                }

                if (!endpoint.responses().responses().isEmpty()) {
                    writer.write("### %s\n".formatted(resourceBundle.getString("endpoint.response")));
                    for (var responsesByHttpCode : endpoint.responses().responses().entrySet()) {
                        writer.write("#### %s\n".formatted(responsesByHttpCode.getKey()));
                        for (var responseVariant : responsesByHttpCode.getValue().content().entrySet()) {
                            var mimeType = responseVariant.getKey();
                            writer.write("##### %s\n".formatted(mimeType));
                            writer.write(generateMarkdownTableForProperties(responseVariant.getValue().properties()));
                            writer.write("##### %s\n".formatted(resourceBundle.getString("endpoint.response.example")));
                            writer.write(generateExample(mimeType, responseVariant));
                        }
                    }
                }
            }
        }
    }

    private String generateExample(String mimeType, Map.Entry<String, SpecSchema> responseVariant) {
        var codeType = switch (mimeType) {
            case "application/json" -> "json";
            default -> throw new UnsupportedOperationException("Unsupported mime type: " + mimeType);
        };

        return """
                ```%s
                %s
                ```
                """.formatted(codeType, getFormattedExample(mimeType, responseVariant.getValue().example()));
    }

    private String getFormattedExample(String mimeType, Object example) {
        if (Objects.equals(mimeType, "application/json")) {
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(example);
            } catch (JsonProcessingException e) {
                throw new UncheckedIOException(e);
            }
        }

        throw new UnsupportedOperationException("Unsupported mime type: " + mimeType);
    }

    private String getEndpointParameterTypeLocalized(EndpointParameter parameter) {
        var type = parameter.type();
        var suffix = type != null ? type.name().toLowerCase() : "unspecified";
        return resourceBundle.getString("endpoint-parameter-type." + suffix);
    }

    private static String stringToLink(String str) {
        return str.replace(" ", "-").replace("/", "-").toLowerCase();
    }

    private String generateMarkdownTableForProperties(List<SpecSchemaProperty> properties) {
        var tableBuilder = new StringBuilder();
        var typeHeader = resourceBundle.getString("schema-properties.table-header.type");
        var nameHeader = resourceBundle.getString("schema-properties.table-header.name");
        var descriptionHeader = resourceBundle.getString("schema-properties.table-header.description");
        var requiredHeader = resourceBundle.getString("schema-properties.table-header.required");
        tableBuilder.append("| ").append(typeHeader).append(" | ").append(nameHeader).append(" | ").append(descriptionHeader).append(" | ").append(requiredHeader).append(" |\n");
        tableBuilder.append("|-----|----------|----------|--------------|\n");

        for (var parameter : properties) {
            var propertyName = parameter.name();
            var propertySchema = parameter.schema();

            var type = getPropertyTypeName(parameter.type(), parameter.schema().itemSpec());
            var description = propertySchema.description() != null ? propertySchema.description() : "—";
            var required = parameter.required() ? "+" : "-";

            // Добавляем строку в таблицу
            tableBuilder.append("| ")
                    .append(type)
                    .append(" | ").append(propertyName)
                    .append(" | ").append(description)
                    .append(" | ").append(required)
                    .append(" |\n");
        }

        return tableBuilder.toString();
    }

    private String getPropertyTypeName(String type, SpecSchema itemSchema) {
        if (type == null) {
            type = "object";
        }

        var name = resourceBundle.getString("schema-property-type." + type.toLowerCase());

        if (type.equals("array")) {
            name = name.formatted(itemSchema.name());
        }

        return name;
    }
}
